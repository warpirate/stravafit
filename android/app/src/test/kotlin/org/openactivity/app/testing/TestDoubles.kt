// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.testing

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.openactivity.app.data.PreferencesRepository
import org.openactivity.app.data.ProfileRepository
import org.openactivity.app.format.RelativeTimeFormatter
import org.openactivity.app.model.Sport
import org.openactivity.app.model.ThemeMode
import org.openactivity.app.model.UserPreferences
import org.openactivity.app.model.UserProfile
import org.openactivity.core.data.mapper.EffortKind
import org.openactivity.core.data.model.ActivityDetail
import org.openactivity.core.data.model.ActivityMetadataUpdate
import org.openactivity.core.data.model.NewActivity
import org.openactivity.core.data.model.PersonalBest
import org.openactivity.core.data.model.RouteDetail
import org.openactivity.core.data.model.RoutePoint
import org.openactivity.core.data.model.RoutePointInput
import org.openactivity.core.data.model.SavedActivity
import org.openactivity.core.data.model.SavedRoute
import org.openactivity.core.data.model.StatTotals
import org.openactivity.core.data.model.WeekTotals
import org.openactivity.core.data.model.YearTotals
import org.openactivity.core.data.repository.ActivityRepository
import org.openactivity.core.data.repository.RouteRepository
import org.openactivity.core.data.repository.SettingsRepository
import org.openactivity.core.datastore.AppSettings
import org.openactivity.core.datastore.AudioCueSettings
import org.openactivity.core.datastore.PrivacySettings
import org.openactivity.core.datastore.ThemePreference
import org.openactivity.geo.BoundingBox
import org.openactivity.model.LatLng
import org.openactivity.model.SportType
import org.openactivity.model.UnitSystem
import org.openactivity.model.Visibility
import org.openactivity.polyline.PolylineCodec
import org.openactivity.recording.AltitudeSource
import org.openactivity.recording.FinishedActivity
import java.io.IOException
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

/** Fixed "now" for deterministic tests: Tuesday 27 Oct 2026, 15:00 in Pune. */
val TEST_CLOCK: Clock = Clock.fixed(Instant.parse("2026-10-27T09:30:00Z"), ZoneId.of("Asia/Kolkata"))

/** Formatter on [TEST_CLOCK], UK English, 24-hour. */
fun testTimeFormatter(): RelativeTimeFormatter = RelativeTimeFormatter(TEST_CLOCK, Locale.UK, use24Hour = true)

/** Replaces `Dispatchers.Main` with a test dispatcher and pins the default locale. */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(val dispatcher: TestDispatcher = StandardTestDispatcher()) : TestWatcher() {
    private var savedLocale: Locale = Locale.getDefault()

    override fun starting(description: Description) {
        savedLocale = Locale.getDefault()
        Locale.setDefault(Locale.UK)
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
        Locale.setDefault(savedLocale)
    }
}

/** In-memory [PreferencesRepository]. */
class FakePreferencesRepository(initial: UserPreferences = UserPreferences()) : PreferencesRepository {
    val state = MutableStateFlow(initial)
    override val preferences: Flow<UserPreferences> = state

    override suspend fun setUnitSystem(unitSystem: UnitSystem) = state.update { it.copy(unitSystem = unitSystem) }

    override suspend fun setThemeMode(themeMode: ThemeMode) = state.update { it.copy(themeMode = themeMode) }

    override suspend fun setReduceMotion(enabled: Boolean) = state.update { it.copy(reduceMotion = enabled) }

    override suspend fun setLastSport(sport: Sport) = state.update { it.copy(lastSport = sport) }
}

/** In-memory [SettingsRepository]. */
class FakeSettingsRepository(initial: AppSettings = AppSettings()) : SettingsRepository {
    val state = MutableStateFlow(initial)
    override val settings: Flow<AppSettings> = state

    override suspend fun setUnits(units: UnitSystem) = state.update { it.copy(units = units) }

    override suspend fun setTheme(theme: ThemePreference) = state.update { it.copy(theme = theme) }

    override suspend fun setAutoPause(sport: SportType, enabled: Boolean) =
        state.update { it.copy(autoPauseOverrides = it.autoPauseOverrides + (sport to enabled)) }

    override suspend fun setAudioCues(cues: AudioCueSettings) = state.update { it.copy(audioCues = cues) }

    override suspend fun setDefaultSport(sport: SportType) = state.update { it.copy(defaultSport = sport) }

    override suspend fun setDefaultVisibility(visibility: Visibility) = state.update { it.copy(defaultVisibility = visibility) }

    override suspend fun setPrivacy(privacy: PrivacySettings) = state.update { it.copy(privacy = privacy) }
}

/** In-memory [ProfileRepository]; [failPhoto] makes [setPhoto] throw. */
class FakeProfileRepository(initial: UserProfile = UserProfile()) : ProfileRepository {
    val state = MutableStateFlow(initial)
    var failPhoto = false
    override val profile: Flow<UserProfile> = state

    override suspend fun updateDetails(name: String, location: String) = state.update { it.copy(name = name.trim(), location = location.trim()) }

    override suspend fun setPhoto(source: Uri) {
        if (failPhoto) throw IOException("bad image")
        state.update { it.copy(photoPath = "/photos/${source.lastPathSegment}") }
    }

    override suspend fun removePhoto() = state.update { it.copy(photoPath = null) }
}

/**
 * In-memory [ActivityRepository]. Lists come from [activities]; aggregates are whatever the test
 * puts in [weekly], [year], [allTime] and [bests].
 */
class FakeActivityRepository(initial: List<SavedActivity> = emptyList()) : ActivityRepository {
    val activities = MutableStateFlow(initial)
    val details = MutableStateFlow<Map<Long, ActivityDetail>>(emptyMap())
    val weekly = MutableStateFlow(List(12) { WeekTotals(TEST_CLOCK.instant().atZone(TEST_CLOCK.zone).toLocalDate().minusWeeks(11L - it), StatTotals.ZERO) })
    val year = MutableStateFlow(YearTotals(2026, StatTotals.ZERO, emptyMap()))
    val allTime = MutableStateFlow<Map<SportType, StatTotals>>(emptyMap())
    val bests = MutableStateFlow<List<PersonalBest>>(emptyList())
    val weeklyRequests = mutableListOf<Set<SportType>?>()
    val bestRequests = mutableListOf<Set<SportType>>()

    override fun observeActivities(): Flow<List<SavedActivity>> = activities

    override fun observeActivity(id: Long): Flow<SavedActivity?> = activities.map { list -> list.firstOrNull { it.id == id } }

    override fun observeActivityDetail(id: Long): Flow<ActivityDetail?> = details.map { it[id] }

    override suspend fun saveFinished(finished: FinishedActivity, options: NewActivity, recordedUnits: UnitSystem): Long = error("not used")

    override suspend fun findIdByUuid(uuid: String): Long? = activities.value.firstOrNull { it.uuid == uuid }?.id

    override suspend fun updateMetadata(id: Long, update: ActivityMetadataUpdate): Boolean = error("not used")

    override suspend fun delete(id: Long): Boolean {
        val before = activities.value.size
        activities.update { list -> list.filterNot { it.id == id } }
        return activities.value.size < before
    }

    override fun observeWeeklyTotals(weeks: Int, sports: Set<SportType>?, firstDayOfWeek: DayOfWeek): Flow<List<WeekTotals>> {
        weeklyRequests += sports
        return weekly
    }

    override fun observeYearTotals(year: Int): Flow<YearTotals> = this.year

    override fun observeAllTimeTotals(): Flow<Map<SportType, StatTotals>> = allTime

    override fun observePersonalBests(sports: Set<SportType>, kind: EffortKind): Flow<List<PersonalBest>> {
        bestRequests += sports
        return bests
    }
}

/** In-memory [RouteRepository]. */
class FakeRouteRepository(initial: List<RouteDetail> = emptyList()) : RouteRepository {
    val routes = MutableStateFlow(initial)
    private var nextId = (initial.maxOfOrNull { it.route.id } ?: 0L) + 1

    override fun observeRoutes(): Flow<List<SavedRoute>> = routes.map { list -> list.map { it.route } }

    override fun observeRoute(id: Long): Flow<RouteDetail?> = routes.map { list -> list.firstOrNull { it.route.id == id } }

    override suspend fun getRoute(id: Long): RouteDetail? = routes.value.firstOrNull { it.route.id == id }

    override suspend fun importRoute(name: String, points: List<RoutePointInput>, description: String, sport: SportType?): Long {
        val id = nextId++
        val detail = routeDetail(id, name, points.map { LatLng(it.latitude, it.longitude) }, sport = sport)
        routes.update { listOf(detail) + it }
        return id
    }

    override suspend fun delete(id: Long): Boolean {
        val before = routes.value.size
        routes.update { list -> list.filterNot { it.route.id == id } }
        return routes.value.size < before
    }
}

/** A small loop near Pune, for routes and activity tracks. */
val PUNE_LOOP: List<LatLng> = listOf(
    LatLng(18.5204, 73.8567), LatLng(18.5230, 73.8601), LatLng(18.5262, 73.8590), LatLng(18.5281, 73.8545),
    LatLng(18.5259, 73.8502), LatLng(18.5221, 73.8510), LatLng(18.5204, 73.8567),
)

/** A [SavedActivity] with sensible defaults. */
fun savedActivity(
    id: Long,
    title: String = "Morning Run",
    sport: SportType = SportType.RUN,
    start: Instant = TEST_CLOCK.instant().minusSeconds(3_600),
    distanceMeters: Double = 8_020.0,
    movingSeconds: Long = 2_500,
    elevationGain: Double = 45.0,
    visibility: Visibility = Visibility.EVERYONE,
    track: List<LatLng>? = PUNE_LOOP,
    description: String = "",
) = SavedActivity(
    id = id,
    uuid = "uuid-$id",
    sport = sport,
    title = title,
    description = description,
    visibility = visibility,
    perceivedExertion = null,
    startTime = start,
    timeZone = TEST_CLOCK.zone,
    elapsedTime = (movingSeconds + 120).seconds,
    movingTime = movingSeconds.seconds,
    distanceMeters = distanceMeters,
    elevationGainMeters = elevationGain,
    elevationLossMeters = elevationGain,
    altitudeSource = AltitudeSource.GPS,
    averageSpeedMps = if (movingSeconds > 0) distanceMeters / movingSeconds else 0.0,
    maxSpeedMps = 5.0,
    averageGradeAdjustedSpeedMps = null,
    averageHeartRate = 148.0,
    maxHeartRate = 171,
    averagePower = null,
    maxPower = null,
    averageCadence = null,
    autoPauseCount = 0,
    polyline = track?.let { PolylineCodec.encode(it) },
    bounds = track?.let { BoundingBox.of(it) },
    hideStartMeters = 200.0,
    hideEndMeters = 200.0,
    flags = emptySet(),
    routeId = null,
)

/** A [RouteDetail] along [points], with cumulative distances and a gentle climb. */
fun routeDetail(
    id: Long,
    name: String = "Riverside Loop",
    points: List<LatLng> = PUNE_LOOP,
    sport: SportType? = SportType.RUN,
    distanceMeters: Double = 8_200.0,
    elevationGain: Double = 45.0,
): RouteDetail {
    val step = distanceMeters / (points.size - 1).coerceAtLeast(1)
    val routePoints = points.mapIndexed { i, p -> RoutePoint(p.latitude, p.longitude, 550.0 + i * 7.5 - (if (i > 3) (i - 3) * 9.0 else 0.0), i * step) }
    return RouteDetail(
        SavedRoute(
            id = id,
            uuid = "route-$id",
            name = name,
            description = "",
            sport = sport,
            distanceMeters = distanceMeters,
            elevationGainMeters = elevationGain,
            elevationLossMeters = elevationGain,
            polyline = PolylineCodec.encode(points),
            bounds = checkNotNull(BoundingBox.of(points)),
            createdAt = TEST_CLOCK.instant(),
        ),
        routePoints,
    )
}
