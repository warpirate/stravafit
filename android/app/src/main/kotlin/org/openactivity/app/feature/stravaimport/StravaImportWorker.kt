// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.feature.stravaimport

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.openactivity.app.MainActivity
import org.openactivity.app.R
import org.openactivity.core.activityextras.ActivityExtrasRepository
import org.openactivity.core.data.model.NewNotification
import org.openactivity.core.data.model.NotificationTarget
import org.openactivity.core.data.model.NotificationType
import org.openactivity.core.data.repository.NotificationRepository
import org.openactivity.core.fileimport.strava.RowOutcome
import org.openactivity.core.fileimport.strava.StravaExportException
import org.openactivity.core.fileimport.strava.StravaExportImporter
import org.openactivity.core.fileimport.strava.StravaImportProgress
import org.openactivity.core.fileimport.strava.StravaImportResult
import org.openactivity.core.fileimport.strava.StravaImportSink
import org.openactivity.feature.activitydetail.fileimport.ActivityImportSaver
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.text.NumberFormat
import java.util.Locale

/**
 * Runs the Strava import in the background (a foreground service with a progress notification, so a
 * long import keeps going with the screen off). WorkManager restarts it after the app is killed or the
 * phone restarts, and the journal makes it continue where it stopped.
 */
class StravaImportWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val deps = EntryPointAccessors.fromApplication(applicationContext, StravaImportEntryPoint::class.java)
        val store = deps.stravaImportStore()
        val job = store.job.value ?: return Result.success()
        if (job.state != StravaImportState.QUEUED && job.state != StravaImportState.RUNNING) return Result.success()
        createChannel(applicationContext)
        goForeground(job)
        store.update { it.copy(state = StravaImportState.RUNNING, error = null) }
        return try {
            val source = source(store, job)
            val saver = deps.activityImportSaver()
            val stored = DatabaseStravaImportSink(saver, deps.activityExtras(), saver.existingStarts(), File(applicationContext.cacheDir, "strava-photos"))
            // Counts photos as they are stored, so a resumed import still reports them all.
            val sink = object : StravaImportSink by stored {
                override suspend fun attachPhoto(activityId: Long, path: String, input: InputStream): Boolean =
                    stored.attachPhoto(activityId, path, input).also { ok ->
                        if (ok) store.update(persist = false) { it.copy(photos = it.photos + 1) }
                    }
            }
            var lastPersist = 0L
            var lastNotify = 0L
            val result = StravaExportImporter().run(source, store.journal, sink) { progress ->
                val now = SystemClock.elapsedRealtime()
                val persist = now - lastPersist > PERSIST_EVERY_MS || progress.done == progress.total
                if (persist) lastPersist = now
                val updated = store.update(persist) {
                    it.copy(phase = progress.phase.toPhase(), done = progress.done, total = progress.total)
                }
                if (updated != null && now - lastNotify > NOTIFY_EVERY_MS) {
                    lastNotify = now
                    goForeground(updated)
                }
            }
            finish(store, deps.notificationRepository(), result)
            Result.success()
        } catch (e: CancellationException) {
            // Cancelled by the user (the screen already marked it) or stopped by the system (WorkManager runs it again).
            throw e
        } catch (e: StravaExportException) {
            fail(store, deps.notificationRepository(), e.message ?: "The ZIP could not be read")
            Result.failure()
        } catch (e: SecurityException) {
            fail(store, deps.notificationRepository(), "StravaFit can no longer open the ZIP. Choose it again.")
            Result.failure()
        } catch (e: FileNotFoundException) {
            fail(store, deps.notificationRepository(), "The ZIP is gone from where it was. Choose it again.")
            Result.failure()
        } catch (e: IOException) {
            // Out of space, or the storage went away: worth another go.
            if (runAttemptCount < MAX_ATTEMPTS) {
                Result.retry()
            } else {
                fail(store, deps.notificationRepository(), "The import stopped: ${e.message ?: "the file could not be read"}")
                Result.failure()
            }
        }
    }

    /** Opens the ZIP: the picked document, or the app's copy of a shared one (made now if needed). */
    private suspend fun source(store: StravaImportStore, job: StravaImportJob): () -> InputStream {
        val resolver = applicationContext.contentResolver
        if (!job.needsCopy) {
            val uri = Uri.parse(job.sourceUri)
            return { resolver.openInputStream(uri) ?: throw FileNotFoundException(job.sourceName) }
        }
        val staged = store.stagedZip
        if (!staged.exists()) {
            store.update { it.copy(phase = StravaImportPhase.COPYING) }
            goForeground(store.job.value ?: job)
            withContext(Dispatchers.IO) {
                val partial = File(staged.parentFile, staged.name + ".part")
                partial.parentFile?.mkdirs()
                val input = resolver.openInputStream(Uri.parse(job.sourceUri)) ?: throw FileNotFoundException(job.sourceName)
                input.use { source -> partial.outputStream().use { source.copyTo(it, COPY_BUFFER) } }
                if (!partial.renameTo(staged)) throw IOException("Could not keep the copy of the ZIP")
            }
        }
        return { staged.inputStream() }
    }

    private suspend fun finish(store: StravaImportStore, inbox: NotificationRepository, result: StravaImportResult) {
        val failures = result.entries.mapNotNull { e ->
            (e.outcome as? RowOutcome.Failed)?.let { StravaImportFailure(e.name, it.reason) }
        }
        val job = store.update {
            it.copy(
                state = StravaImportState.DONE,
                done = result.total,
                total = result.total,
                imported = result.imported,
                duplicates = result.duplicates,
                failed = failures.size,
                failures = failures.take(StravaImportJob.MAX_FAILURES_LISTED),
                finishedAtMillis = System.currentTimeMillis(),
            )
        } ?: return
        store.stagedZip.delete()
        val entry = NewNotification(
            type = NotificationType.IMPORT_FINISHED,
            title = "Strava import finished",
            body = StravaImportText.summary(job),
            target = NotificationTarget.ACTIVITIES,
            dedupeKey = "strava-import:${job.id}",
        )
        inbox.add(entry)
        postResult(entry)
    }

    private suspend fun fail(store: StravaImportStore, inbox: NotificationRepository, message: String) {
        val job = store.update {
            it.copy(state = StravaImportState.FAILED, error = message, finishedAtMillis = System.currentTimeMillis())
        } ?: return
        val entry = NewNotification(
            type = NotificationType.IMPORT_FAILED,
            title = "Strava import stopped",
            body = message,
            target = NotificationTarget.ACTIVITIES,
            dedupeKey = "strava-import:${job.id}:failed:${job.done}",
        )
        inbox.add(entry)
        postResult(entry)
    }

    /** The finished or failed import as a notification that opens the import screen. */
    private fun postResult(entry: NewNotification) {
        val manager = NotificationManagerCompat.from(applicationContext)
        if (!manager.areNotificationsEnabled()) return
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_inbox)
            .setColor(ACCENT)
            .setContentTitle(entry.title)
            .setContentText(entry.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(entry.body))
            .setContentIntent(openImportScreen(applicationContext, RESULT_NOTIFICATION_ID))
            .setAutoCancel(true)
            .build()
        try {
            manager.notify(RESULT_NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // Notifications not allowed; the inbox and the screen still show the result.
        }
    }

    private suspend fun goForeground(job: StravaImportJob) {
        val notification = progressNotification(applicationContext, job)
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(PROGRESS_NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(PROGRESS_NOTIFICATION_ID, notification)
        }
        try {
            setForeground(info)
        } catch (e: IllegalStateException) {
            // Restarted from the background (Android 12+ forbids starting a foreground service then): it runs
            // as plain background work, and WorkManager runs the rest later if it is stopped.
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val job = EntryPointAccessors.fromApplication(applicationContext, StravaImportEntryPoint::class.java).stravaImportStore().job.value
        createChannel(applicationContext)
        val notification = progressNotification(applicationContext, job)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(PROGRESS_NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(PROGRESS_NOTIFICATION_ID, notification)
        }
    }

    /** Reaches the Hilt graph from a worker WorkManager created itself. */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface StravaImportEntryPoint {
        fun stravaImportStore(): StravaImportStore
        fun activityImportSaver(): ActivityImportSaver
        fun activityExtras(): ActivityExtrasRepository
        fun notificationRepository(): NotificationRepository
    }

    companion object {
        private const val WORK_NAME = "strava-export-import"
        const val CHANNEL_ID = "imports"
        private const val PROGRESS_NOTIFICATION_ID = 7_301
        private const val RESULT_NOTIFICATION_ID = 7_302
        private const val PERSIST_EVERY_MS = 2_000L
        private const val NOTIFY_EVERY_MS = 1_000L
        private const val MAX_ATTEMPTS = 5
        private const val COPY_BUFFER = 256 * 1024
        private const val ACCENT = 0xFFFC5200.toInt()

        /** Launch extra: open the Strava import screen. */
        const val EXTRA_OPEN_STRAVA_IMPORT: String = "org.openactivity.app.extra.OPEN_STRAVA_IMPORT"

        /** Runs (or continues) the current job. [restart] replaces a worker that is still around. */
        fun enqueue(context: Context, restart: Boolean) {
            // No storage constraint: a job waiting on one would look stuck. A full disk fails the save and retries.
            val request = OneTimeWorkRequestBuilder<StravaImportWorker>().build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                if (restart) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
                request,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }

        /** The unique work name, for observing it. */
        const val UNIQUE_WORK: String = WORK_NAME

        fun createChannel(context: Context) {
            NotificationManagerCompat.from(context).createNotificationChannel(
                NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
                    .setName(context.getString(R.string.channel_imports_name))
                    .setDescription(context.getString(R.string.channel_imports_description))
                    .build(),
            )
        }

        private fun progressNotification(context: Context, job: StravaImportJob?): Notification {
            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_inbox)
                .setColor(ACCENT)
                .setContentTitle("Importing from Strava")
                .setContentText(job?.let(StravaImportText::progressLine) ?: "Starting")
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setContentIntent(openImportScreen(context, PROGRESS_NOTIFICATION_ID))
            if (job != null && job.total > 0 && job.phase == StravaImportPhase.ACTIVITIES) {
                builder.setProgress(job.total, job.done, false)
            } else {
                builder.setProgress(0, 0, true)
            }
            return builder.build()
        }

        private fun openImportScreen(context: Context, requestCode: Int): PendingIntent = PendingIntent.getActivity(
            context,
            requestCode,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(EXTRA_OPEN_STRAVA_IMPORT, true),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        private fun StravaImportProgress.Phase.toPhase(): StravaImportPhase = when (this) {
            StravaImportProgress.Phase.READING_LIST -> StravaImportPhase.READING_LIST
            StravaImportProgress.Phase.ACTIVITIES -> StravaImportPhase.ACTIVITIES
            StravaImportProgress.Phase.PHOTOS -> StravaImportPhase.PHOTOS
        }
    }
}

/** The words of the Strava import, shared by the screen, the notifications and the inbox. */
object StravaImportText {
    private fun count(n: Int): String = NumberFormat.getIntegerInstance(Locale.getDefault()).format(n)

    /** "312 of 1,204 activities", or what it is doing before the count is known. */
    fun progressLine(job: StravaImportJob): String = when {
        job.phase == StravaImportPhase.COPYING -> "Copying the ZIP into StravaFit"
        job.total == 0 -> "Reading the list of activities"
        job.phase == StravaImportPhase.PHOTOS -> "Adding photos"
        else -> "${count(job.done)} of ${count(job.total)} ${if (job.total == 1) "activity" else "activities"}"
    }

    /** "1,180 imported · 20 already here · 4 couldn't be imported". */
    fun summary(job: StravaImportJob): String = listOfNotNull(
        "${count(job.imported)} imported",
        "${count(job.duplicates)} already here".takeIf { job.duplicates > 0 },
        "${count(job.failed)} couldn't be imported".takeIf { job.failed > 0 },
        "${count(job.photos)} ${if (job.photos == 1) "photo" else "photos"}".takeIf { job.photos > 0 },
    ).joinToString(" · ")
}
