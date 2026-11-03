// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.feature.segments

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openactivity.app.data.segments.SegmentMatchWatcher
import org.openactivity.app.data.segments.SegmentWatermark
import org.openactivity.app.notifications.SystemNotifier
import org.openactivity.app.testing.FakeActivityRepository
import org.openactivity.app.testing.FakeNotificationRepository
import org.openactivity.app.testing.FakeSettingsRepository
import org.openactivity.app.testing.TEST_CLOCK
import org.openactivity.app.testing.savedActivity
import org.openactivity.core.data.model.NewNotification
import org.openactivity.core.data.model.NotificationType
import org.openactivity.core.data.model.Segment
import org.openactivity.core.data.model.SegmentEffort
import org.openactivity.core.data.model.SegmentSummary
import org.openactivity.core.data.repository.SegmentRepository
import org.openactivity.core.datastore.NotificationSettings
import org.openactivity.model.SportType
import org.openactivity.model.UnitSystem
import org.openactivity.recording.segment.LiveSegment
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

/** The post-save matcher hook, segment formatting and the segment detail state. */
class SegmentMatchWatcherTest {

    private class MemoryWatermark(var value: Long?) : SegmentWatermark {
        override suspend fun get(): Long? = value
        override suspend fun set(activityId: Long) { value = activityId }
    }

    /** Hands out the efforts listed per activity, once. */
    private class FakeSegments(val byActivity: Map<Long, List<SegmentEffort>>) : SegmentRepository {
        val matched = mutableListOf<Long>()
        private val stored = MutableStateFlow<List<SegmentEffort>>(emptyList())
        override fun observeSegments(): Flow<List<SegmentSummary>> = flowOf(emptyList())
        override fun observeSegment(id: Long): Flow<Segment?> = flowOf(null)
        override fun observeEffortsOfSegment(segmentId: Long): Flow<List<SegmentEffort>> = stored.map { l -> l.filter { it.segmentId == segmentId } }
        override fun observeEffortsOfActivity(activityId: Long): Flow<List<SegmentEffort>> = stored.map { l -> l.filter { it.activityId == activityId } }
        override fun observePersonalRecords(): Flow<List<SegmentEffort>> = flowOf(emptyList())
        override suspend fun create(activityId: Long, startIndex: Int, endIndex: Int, name: String, sport: SportType): Long = 1
        override suspend fun rename(id: Long, name: String): Boolean = true
        override suspend fun setStarred(id: Long, starred: Boolean) = Unit
        override suspend fun delete(id: Long): Boolean = true
        override suspend fun matchActivity(activityId: Long): List<SegmentEffort> {
            matched += activityId
            val added = byActivity[activityId].orEmpty()
            stored.value = stored.value + added
            return added
        }
        override suspend fun rematchSegment(segmentId: Long): Int = 0
        override suspend fun liveSegments(sport: SportType): List<LiveSegment> = emptyList()
    }

    private class RecordingNotifier : SystemNotifier {
        val posted = mutableListOf<NewNotification>()
        override fun post(inboxId: Long, entry: NewNotification, settings: NotificationSettings) { posted += entry }
    }

    private fun effort(id: Long, activityId: Long, seconds: Long, pr: Boolean) = SegmentEffort(
        id = id, segmentId = 5, segmentName = "Bridge sprint", segmentSport = SportType.RUN, segmentDistanceMeters = 310.0,
        segmentGradePercent = -0.4, activityId = activityId, activityTitle = "Run $activityId", activitySport = SportType.RUN,
        startTime = TEST_CLOCK.instant(), timeZone = ZoneOffset.UTC, year = 2026, startIndex = 0, endIndex = 5,
        elapsed = seconds.seconds, moving = seconds.seconds, distanceMeters = 312.0, averageSpeedMps = 5.0, averageHeartRate = null,
        maxHeartRate = null, averagePower = null, averageCadence = null, rankAllTime = if (pr) 1 else 2, rankYear = null, isPersonalRecord = pr,
    )

    @Test
    fun `new activities are matched oldest first and a segment PR goes to the inbox`() = runTest {
        val activities = FakeActivityRepository(listOf(savedActivity(1)))
        val segments = FakeSegments(mapOf(2L to listOf(effort(20, 2, 70, pr = false)), 3L to listOf(effort(30, 3, 62, pr = true))))
        val inbox = FakeNotificationRepository()
        val notifier = RecordingNotifier()
        val watermark = MemoryWatermark(null)
        val watcher = SegmentMatchWatcher(activities, segments, inbox, FakeSettingsRepository(), watermark, notifier, TEST_CLOCK)

        watcher.process()
        assertEquals("the first run only sets the watermark", 1L, watermark.value)
        assertTrue(segments.matched.isEmpty())

        activities.activities.value = listOf(savedActivity(3), savedActivity(2), savedActivity(1))
        watcher.process()
        assertEquals(listOf(2L, 3L), segments.matched)
        assertEquals(3L, watermark.value)
        val entry = inbox.entries.value.single()
        assertEquals(NotificationType.SEGMENT_PR, entry.type)
        assertEquals("New segment PR: Bridge sprint in 1:02", entry.title)
        assertEquals("Run 3 · 0:08 faster than your old PR", entry.body)
        assertEquals(3L, entry.activityId)
        assertEquals(1, notifier.posted.size)

        watcher.process()
        assertEquals("nothing new, nothing matched again", listOf(2L, 3L), segments.matched)
    }

    @Test
    fun `a bulk import is matched but not announced`() = runTest {
        val activities = FakeActivityRepository(emptyList())
        val efforts = (1L..5L).associateWith { listOf(effort(it * 10, it, 60 - it, pr = true)) }
        val segments = FakeSegments(efforts)
        val inbox = FakeNotificationRepository()
        val watcher = SegmentMatchWatcher(activities, segments, inbox, FakeSettingsRepository(), MemoryWatermark(0), RecordingNotifier(), TEST_CLOCK)
        activities.activities.value = (1L..5L).map { savedActivity(it) }
        watcher.process()
        assertEquals(5, segments.matched.size)
        assertTrue(inbox.entries.value.isEmpty())
    }

    @Test
    fun `clock and grade format like the rest of the app`() {
        assertEquals("0:42", SegmentFormat.clock(42))
        assertEquals("4:05", SegmentFormat.clock(245))
        assertEquals("1:02:03", SegmentFormat.clock(3723))
        assertEquals("PR", SegmentFormat.medalLabel(1, yearly = false))
        assertEquals("2nd this year", SegmentFormat.medalLabel(2, yearly = true))
        assertEquals(null, SegmentFormat.medalLabel(null, yearly = false))
    }

    @Test
    fun `segment detail lists the PR, the top three and every effort newest first`() {
        Locale.setDefault(Locale.US)
        val segment = Segment(
            id = 5, name = "Bridge sprint", sport = SportType.RUN, points = listOf(org.openactivity.model.LatLng(47.0, 8.0), org.openactivity.model.LatLng(47.003, 8.0)),
            elevations = null, distanceMeters = 333.0, elevationGainMeters = 0.0, averageGradePercent = -0.4, starred = true,
            createdAt = Instant.EPOCH, sourceActivityId = 1,
        )
        val efforts = listOf(
            effort(1, 1, 70, pr = false).copy(startTime = Instant.parse("2026-09-01T07:00:00Z")),
            effort(2, 2, 62, pr = true).copy(startTime = Instant.parse("2026-09-08T07:00:00Z")),
            effort(3, 3, 66, pr = false).copy(startTime = Instant.parse("2026-09-15T07:00:00Z")),
            effort(4, 4, 75, pr = false).copy(startTime = Instant.parse("2026-09-22T07:00:00Z")),
        )
        val state = SegmentDetailViewModel.build(segment, efforts, UnitSystem.METRIC, Locale.US)
        assertEquals("1:02", state.prTime)
        assertEquals(2L, state.prActivityId)
        assertEquals(listOf(2L, 3L, 1L), state.best.map { it.id })
        assertEquals(listOf(4L, 3L, 2L, 1L), state.recent.map { it.id })
        assertEquals(listOf(false, true, false, false), state.chart.map { it.isBest })
        assertEquals("4", state.stats.first { it.label == "Efforts" }.value)
        assertTrue(!state.profile.hasElevation)
    }
}
