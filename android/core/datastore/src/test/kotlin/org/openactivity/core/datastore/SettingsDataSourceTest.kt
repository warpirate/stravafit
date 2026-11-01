// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.openactivity.model.PaceOrSpeed
import org.openactivity.model.SpeedDisplay
import org.openactivity.model.SportType
import org.openactivity.model.SwimPaceUnit
import org.openactivity.model.UnitSystem
import org.openactivity.model.Visibility
import java.io.File
import java.time.DayOfWeek
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsDataSourceTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun TestScope.newStore(file: File = tmp.newFile("settings.preferences_pb").also { it.delete() }) =
        PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { file })

    @Test
    fun `defaults follow the docs`() = runTest(UnconfinedTestDispatcher()) {
        val settings = SettingsDataSource(newStore()) { Locale.UK }.settings.first()
        assertEquals(UnitSystem.METRIC, settings.units)
        assertEquals(ThemePreference.SYSTEM, settings.theme)
        assertTrue(settings.autoPauseEnabled(SportType.RIDE))
        assertFalse(settings.autoPauseEnabled(SportType.RUN))
        assertFalse(settings.autoPauseEnabled(SportType.HIKE))
        assertEquals(SportType.RUN, settings.defaultSport)
        assertEquals(Visibility.FOLLOWERS, settings.defaultVisibility)
        assertEquals(200.0, settings.privacy.hideStartMeters, 0.0)
        assertEquals(200.0, settings.privacy.hideEndMeters, 0.0)
        assertTrue(settings.audioCues.enabled)
        assertEquals(1.0, settings.audioCues.distanceInterval, 0.0)
        assertEquals(DayOfWeek.MONDAY, settings.firstDayOfWeek)
        assertEquals(NotificationSettings(achievements = true, goals = true, weeklySummary = true), settings.notifications)
    }

    @Test
    fun `first-run units are imperial only for US, LR and MM`() = runTest(UnconfinedTestDispatcher()) {
        assertEquals(UnitSystem.IMPERIAL, SettingsDataSource(newStore()) { Locale.US }.settings.first().units)
        assertEquals(UnitSystem.METRIC, AppSettings.defaultUnits(Locale.GERMANY))
        assertEquals(UnitSystem.IMPERIAL, AppSettings.defaultUnits(Locale("en", "LR")))
        assertEquals(UnitSystem.IMPERIAL, AppSettings.defaultUnits(Locale("my", "MM")))
    }

    @Test
    fun `every setter round-trips`() = runTest(UnconfinedTestDispatcher()) {
        val source = SettingsDataSource(newStore()) { Locale.US }
        source.setUnits(UnitSystem.METRIC)
        source.setTheme(ThemePreference.DARK)
        source.setAutoPause(SportType.RUN, true)
        source.setAutoPause(SportType.RIDE, false)
        source.setAudioCues(AudioCueSettings(enabled = false, distanceInterval = 0.5, announceAutoPause = false))
        source.setDefaultSport(SportType.HIKE)
        source.setDefaultVisibility(Visibility.ONLY_ME)
        source.setPrivacy(PrivacySettings(hideStartMeters = 400.0, hideEndMeters = 0.0))
        source.setFirstDayOfWeek(DayOfWeek.SUNDAY)
        source.setNotifications(NotificationSettings(achievements = false, goals = true, weeklySummary = false))

        val s = source.settings.first()
        assertEquals(UnitSystem.METRIC, s.units)
        assertEquals(ThemePreference.DARK, s.theme)
        assertTrue(s.autoPauseEnabled(SportType.RUN))
        assertFalse(s.autoPauseEnabled(SportType.RIDE))
        assertTrue(s.autoPauseEnabled(SportType.GRAVEL_RIDE))
        assertEquals(AudioCueSettings(false, 0.5, false), s.audioCues)
        assertEquals(SportType.HIKE, s.defaultSport)
        assertEquals(Visibility.ONLY_ME, s.defaultVisibility)
        assertEquals(PrivacySettings(400.0, 0.0), s.privacy)
        assertEquals(DayOfWeek.SUNDAY, s.firstDayOfWeek)
        assertEquals(NotificationSettings(achievements = false, goals = true, weeklySummary = false), s.notifications)
    }

    @Test
    fun `toggling auto-pause back and forth keeps one entry per sport`() = runTest(UnconfinedTestDispatcher()) {
        val source = SettingsDataSource(newStore())
        source.setAutoPause(SportType.WALK, true)
        source.setAutoPause(SportType.WALK, false)
        source.setAutoPause(SportType.WALK, true)
        assertEquals(mapOf(SportType.WALK to true), source.settings.first().autoPauseOverrides)
    }

    @Test
    fun `unknown stored values fall back to defaults`() = runTest(UnconfinedTestDispatcher()) {
        val store = newStore()
        store.edit {
            it[stringPreferencesKey("units")] = "FURLONGS"
            it[stringPreferencesKey("default_sport")] = "QUIDDITCH"
        }
        val s = SettingsDataSource(store) { Locale.UK }.settings.first()
        assertEquals(UnitSystem.METRIC, s.units)
        assertEquals(SportType.RUN, s.defaultSport)
    }

    @Test
    fun `pace or speed overrides round-trip and resolve per sport`() = runTest(UnconfinedTestDispatcher()) {
        val source = SettingsDataSource(newStore()) { Locale.UK }
        val defaults = source.settings.first()
        assertEquals(SpeedDisplay.PACE, defaults.speedDisplay(SportType.RUN))
        assertEquals(SpeedDisplay.SPEED, defaults.speedDisplay(SportType.RIDE))
        assertEquals(SpeedDisplay.PACE_PER_100M, defaults.speedDisplay(SportType.SWIM))
        assertNull(defaults.swimPaceUnit)

        source.setSpeedOverride(SportType.RUN, PaceOrSpeed.SPEED)
        source.setSpeedOverride(SportType.RIDE, PaceOrSpeed.PACE)
        source.setSpeedOverride(SportType.RIDE, PaceOrSpeed.SPEED)
        source.setSwimPaceUnit(SwimPaceUnit.YARDS)
        val s = source.settings.first()
        assertEquals(mapOf(SportType.RUN to PaceOrSpeed.SPEED, SportType.RIDE to PaceOrSpeed.SPEED), s.speedOverrides)
        assertEquals(SpeedDisplay.SPEED, s.speedDisplay(SportType.RUN))
        assertEquals(PaceOrSpeed.SPEED, s.paceOrSpeed(SportType.RUN))
        assertEquals(SpeedDisplay.PACE_PER_100YD, s.speedDisplay(SportType.SWIM))

        source.setSpeedOverride(SportType.RUN, null)
        source.setSwimPaceUnit(null)
        val reset = source.settings.first()
        assertEquals(SpeedDisplay.PACE, reset.speedDisplay(SportType.RUN))
        assertNull(reset.swimPaceUnit)
    }

    @Test
    fun `per-sport default visibility falls back to the account default`() = runTest(UnconfinedTestDispatcher()) {
        val source = SettingsDataSource(newStore())
        source.setDefaultVisibility(Visibility.EVERYONE)
        source.setVisibilityOverride(SportType.WALK, Visibility.ONLY_ME)
        val s = source.settings.first()
        assertEquals(Visibility.ONLY_ME, s.defaultVisibility(SportType.WALK))
        assertEquals(Visibility.EVERYONE, s.defaultVisibility(SportType.RUN))
        source.setVisibilityOverride(SportType.WALK, null)
        assertEquals(Visibility.EVERYONE, source.settings.first().defaultVisibility(SportType.WALK))
    }

    @Test
    fun `an unreadable stored visibility fails safe to only me`() = runTest(UnconfinedTestDispatcher()) {
        val store = newStore()
        store.edit {
            it[stringPreferencesKey("default_visibility")] = "PUBLIC_TO_ALIENS"
            it[stringSetPreferencesKey("visibility_overrides")] = setOf("RUN=nonsense", "QUIDDITCH=EVERYONE", "RIDE=FOLLOWERS")
        }
        val s = SettingsDataSource(store).settings.first()
        assertEquals(Visibility.ONLY_ME, s.defaultVisibility)
        assertEquals(Visibility.ONLY_ME, s.defaultVisibility(SportType.RUN))
        assertEquals(Visibility.FOLLOWERS, s.defaultVisibility(SportType.RIDE))
        assertEquals(setOf(SportType.RUN, SportType.RIDE), s.visibilityOverrides.keys)
    }

    @Test
    fun `launch tab defaults to home and round-trips`() = runTest(UnconfinedTestDispatcher()) {
        val source = SettingsDataSource(newStore())
        assertEquals(LaunchTab.HOME, source.settings.first().launchTab)
        source.setLaunchTab(LaunchTab.RECORD)
        assertEquals(LaunchTab.RECORD, source.settings.first().launchTab)
    }
}
