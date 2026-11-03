// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.data.segments

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.Binds
import dagger.Module
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.openactivity.app.data.local.LocalPreferences
import org.openactivity.app.notifications.InboxText
import org.openactivity.app.notifications.SystemNotifier
import org.openactivity.core.data.model.SavedActivity
import org.openactivity.core.data.repository.ActivityRepository
import org.openactivity.core.data.repository.NotificationRepository
import org.openactivity.core.data.repository.SegmentRepository
import org.openactivity.core.data.repository.SettingsRepository
import java.time.Clock
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

/** Remembers the newest activity matched against your segments. */
interface SegmentWatermark {
    suspend fun get(): Long?

    suspend fun set(activityId: Long)
}

/** [SegmentWatermark] in the app's local preferences. */
class PreferencesSegmentWatermark @Inject constructor(private val preferences: LocalPreferences) : SegmentWatermark {
    override suspend fun get(): Long? = preferences.segmentWatermark.first()

    override suspend fun set(activityId: Long) = preferences.setSegmentWatermark(activityId)
}

/**
 * Matches every newly saved activity against your segments, whichever way it arrived (recorded, recovered
 * or imported), and posts a new segment PR to the inbox, as best efforts do.
 *
 * Like the inbox it works from a watermark (the newest activity matched), so an activity saved while the
 * app was being killed is matched on the next start. Matching is idempotent per segment and activity. On
 * the very first run the watermark is set without matching: no segment existed before this feature, and a
 * new segment is matched against older activities by [SegmentRematchScheduler] anyway.
 */
@Singleton
class SegmentMatchWatcher @Inject constructor(
    private val activities: ActivityRepository,
    private val segments: SegmentRepository,
    private val inbox: NotificationRepository,
    private val settings: SettingsRepository,
    private val watermark: SegmentWatermark,
    private val notifier: SystemNotifier,
    private val clock: Clock,
) {
    private val lock = Mutex()

    /** Watches the activity list for as long as [scope] lives. */
    fun start(scope: CoroutineScope) {
        scope.launch {
            activities.observeActivities()
                .map { list -> list.maxOfOrNull { it.id } ?: 0L }
                .distinctUntilChanged()
                .collect {
                    try {
                        process()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // Retried with the next change; the watermark only moves past matched activities.
                    }
                }
        }
    }

    /** Matches every activity newer than the watermark, oldest first. */
    suspend fun process() = lock.withLock {
        val all = activities.observeActivities().first()
        val newest = all.maxOfOrNull { it.id } ?: 0L
        val mark = watermark.get()
        if (mark == null) {
            watermark.set(newest)
            return@withLock
        }
        val fresh = all.filter { it.id > mark }.sortedBy { it.id }
        if (fresh.isEmpty()) return@withLock
        // A bulk import is not news: efforts are stored, but only a recording or two is announced.
        val announce = fresh.size <= MAX_ANNOUNCED_AT_ONCE
        val notificationSettings = settings.settings.first().notifications
        for (activity in fresh) {
            val added = segments.matchActivity(activity.id)
            if (announce && isRecent(activity)) {
                for (effort in added.filter { it.isPersonalRecord }) {
                    val previous = segments.observeEffortsOfSegment(effort.segmentId).first()
                        .filter { it.id != effort.id && it.activityId != activity.id }
                        .minOfOrNull { it.elapsed.inWholeSeconds }
                    val entry = InboxText.segmentPr(effort, previous)
                    inbox.add(entry)?.let { id -> notifier.post(id, entry, notificationSettings) }
                }
            }
            watermark.set(activity.id)
        }
    }

    private fun isRecent(activity: SavedActivity): Boolean = Duration.between(activity.startTime, clock.instant()) <= RECENT

    internal companion object {
        const val MAX_ANNOUNCED_AT_ONCE = 3
        val RECENT: Duration = Duration.ofDays(14)
    }
}

/** Matches a segment against every stored activity in the background (after creating it, or on request). */
interface SegmentRematchScheduler {
    fun rematch(segmentId: Long)
}

/** [SegmentRematchScheduler] over WorkManager: one unique job per segment, replaced when asked again. */
class WorkManagerSegmentRematchScheduler @Inject constructor(@ApplicationContext private val context: Context) : SegmentRematchScheduler {
    override fun rematch(segmentId: Long) {
        val request = OneTimeWorkRequestBuilder<SegmentRematchWorker>()
            .setInputData(workDataOf(SegmentRematchWorker.KEY_SEGMENT to segmentId))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("segment-rematch-$segmentId", ExistingWorkPolicy.REPLACE, request)
    }
}

/** Re-matches one segment against every activity ("Re-match all activities"). */
class SegmentRematchWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getLong(KEY_SEGMENT, 0L).takeIf { it > 0 } ?: return Result.failure()
        val graph = EntryPointAccessors.fromApplication(applicationContext, SegmentWorkEntryPoint::class.java)
        return try {
            graph.segmentRepository().rematchSegment(id)
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    /** Reaches the Hilt graph from a worker WorkManager created itself. */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface SegmentWorkEntryPoint {
        fun segmentRepository(): SegmentRepository
    }

    internal companion object {
        const val KEY_SEGMENT = "segment_id"
        const val MAX_ATTEMPTS = 3
    }
}

/** Binds segment matching. */
@Module
@InstallIn(SingletonComponent::class)
abstract class SegmentMatchingModule {
    @Binds
    abstract fun watermark(impl: PreferencesSegmentWatermark): SegmentWatermark

    @Binds
    abstract fun rematchScheduler(impl: WorkManagerSegmentRematchScheduler): SegmentRematchScheduler
}
