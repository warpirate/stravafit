// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.testing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.openactivity.app.data.PreferencesRepository
import org.openactivity.app.format.RelativeTimeFormatter
import org.openactivity.app.model.Sport
import org.openactivity.app.model.ThemeMode
import org.openactivity.app.model.UnitSystem
import org.openactivity.app.model.UserPreferences
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

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
