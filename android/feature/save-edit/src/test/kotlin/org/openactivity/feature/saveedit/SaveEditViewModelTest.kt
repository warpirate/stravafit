// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.feature.saveedit

import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.openactivity.core.data.model.NewActivity
import org.openactivity.core.data.repository.LocalActivityRepository
import org.openactivity.core.database.OpenActivityDatabase
import org.openactivity.model.SportType
import org.openactivity.model.UnitSystem
import org.openactivity.model.Visibility
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SaveEditViewModelTest {
    private lateinit var db: OpenActivityDatabase
    private lateinit var activities: LocalActivityRepository
    private val extras = FakeExtras()
    private val settings = FakeSettings()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), OpenActivityDatabase::class.java)
            .allowMainThreadQueries().build()
        activities = LocalActivityRepository(db.activityDao(), Clock.systemUTC())
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    /** Stores a run the way the recorder does: default title, UTC zone, privacy radii. */
    private suspend fun stored(): Long =
        activities.saveFinished(steadyRun(), NewActivity("rec", hideStartMeters = 200.0, hideEndMeters = 200.0, timeZone = ZoneOffset.UTC), UnitSystem.METRIC)

    private fun vm(id: Long, isNew: Boolean = true) = SaveEditViewModel(id, isNew, activities, extras, settings)

    private suspend fun SaveEditViewModel.editing(): SaveEditUiState.Editing =
        withTimeout(10_000) { uiState.first { it is SaveEditUiState.Editing } } as SaveEditUiState.Editing

    @Test
    fun `loads the stored activity with its automatic title and facts`(): Unit = runBlocking {
        val state = vm(stored()).editing()
        assertTrue(state.isNew)
        assertEquals("Morning Run", state.form.title)
        assertEquals("Morning Run", state.facts.defaultTitle)
        assertTrue(state.facts.summary, Regex("""\d+[.,]\d\d km · 1[45]:\d\d""").matches(state.facts.summary))
        assertEquals(200.0, state.facts.hideStartMeters, 0.0)
        assertTrue(state.facts.hasRoute)
        assertFalse(state.dirty)
    }

    @Test
    fun `an automatic title follows the sport but a typed one is kept`(): Unit = runBlocking {
        val vm = vm(stored())
        vm.editing()
        vm.setSport(SportType.WALK)
        assertEquals("Morning Walk", vm.editing().form.title)
        assertTrue(vm.editing().dirty)

        vm.setTitle("Lake loop with Sam")
        vm.setSport(SportType.HIKE)
        assertEquals("Lake loop with Sam", vm.editing().form.title)
        assertEquals("Morning Hike", vm.editing().facts.defaultTitle)
    }

    @Test
    fun `saving from the edit screen reports an edit, not a first save`(): Unit = runBlocking {
        val id = stored()
        val vm = vm(id, isNew = false)
        vm.editing()
        vm.setTitle("Renamed")
        vm.save()
        assertEquals(SaveEditEffect.Saved(id, isNew = false), withTimeout(10_000) { vm.effects.first() })
    }

    @Test
    fun `save stores every field, and an empty title saves as the default`(): Unit = runBlocking {
        val id = stored()
        val vm = vm(id)
        vm.editing()
        vm.setTitle("   ")
        vm.setDescription("Felt strong ")
        vm.setSport(SportType.TRAIL_RUN)
        vm.setVisibility(Visibility.ONLY_ME)
        vm.setPerceivedExertion(12)
        vm.setMuted(true)
        vm.save()
        assertEquals(SaveEditEffect.Saved(id, isNew = true), withTimeout(10_000) { vm.effects.first() })

        val a = activities.observeActivity(id).first()!!
        assertEquals("Morning Trail Run", a.title)
        assertEquals("Felt strong", a.description)
        assertEquals(SportType.TRAIL_RUN, a.sport)
        assertEquals(Visibility.ONLY_ME, a.visibility)
        assertEquals(10, a.perceivedExertion)
        assertTrue(extras.get(id).muted)
        assertFalse(vm.editing().dirty)
    }

    @Test
    fun `clearing the exertion rating stores no rating`(): Unit = runBlocking {
        val id = stored()
        val vm = vm(id)
        vm.editing()
        vm.setPerceivedExertion(4)
        vm.setPerceivedExertion(null)
        vm.save()
        withTimeout(10_000) { vm.effects.first() }
        assertNull(activities.observeActivity(id).first()!!.perceivedExertion)
    }

    @Test
    fun `discard deletes the recording and its extras after confirmation`(): Unit = runBlocking {
        val id = stored()
        val vm = vm(id)
        vm.editing()
        vm.requestDiscard()
        assertEquals(SaveEditDialog.DISCARD_ACTIVITY, vm.editing().dialog)
        vm.confirmDialog()
        assertEquals(SaveEditEffect.Discarded, withTimeout(10_000) { vm.effects.first() })
        assertNull(activities.observeActivity(id).first())
        assertEquals(listOf(id), extras.deleted)
    }

    @Test
    fun `edit mode cannot discard, and back with changes asks first`(): Unit = runBlocking {
        val vm = vm(stored(), isNew = false)
        vm.editing()
        vm.requestDiscard()
        assertNull(vm.editing().dialog)

        vm.onBack()
        assertEquals(SaveEditEffect.Close, withTimeout(10_000) { vm.effects.first() })

        val changed = vm(stored(), isNew = false)
        changed.editing()
        changed.setDescription("changed")
        changed.onBack()
        assertEquals(SaveEditDialog.DISCARD_CHANGES, changed.editing().dialog)
        changed.dismissDialog()
        assertNull(changed.editing().dialog)
        changed.onBack()
        changed.confirmDialog()
        assertEquals(SaveEditEffect.Close, withTimeout(10_000) { changed.effects.first() })
    }

    @Test
    fun `closing the save screen of a new activity offers save or discard instead of keeping it`(): Unit = runBlocking {
        val id = stored()
        val vm = vm(id, isNew = true)
        vm.editing()
        vm.onBack()
        assertEquals(SaveEditDialog.LEAVE_NEW_ACTIVITY, vm.editing().dialog)
        assertNull(kotlinx.coroutines.withTimeoutOrNull(200) { vm.effects.first() })
        vm.confirmDialog()
        assertEquals(SaveEditEffect.Discarded, withTimeout(10_000) { vm.effects.first() })
        assertNull(activities.observeActivity(id).first())
    }

    @Test
    fun `a double tap on back closes the screen only once`(): Unit = runBlocking {
        val vm = vm(stored(), isNew = false)
        vm.editing()
        vm.onBack()
        vm.onBack()
        assertEquals(SaveEditEffect.Close, withTimeout(10_000) { vm.effects.first() })
        assertNull(kotlinx.coroutines.withTimeoutOrNull(200) { vm.effects.first() })
    }

    @Test
    fun `photos are stored at once and reported when over the limit`(): Unit = runBlocking {
        val id = stored()
        val vm = vm(id)
        vm.editing()
        vm.addPhotos(List(14) { Uri.parse("content://media/$it") })
        val message = withTimeout(10_000) { vm.effects.first() } as SaveEditEffect.Message
        assertTrue(message.message.contains("2 over the limit"))
        withTimeout(10_000) { vm.uiState.first { (it as? SaveEditUiState.Editing)?.photos?.size == 12 } }
        vm.removePhoto("p0")
        withTimeout(10_000) { vm.uiState.first { (it as? SaveEditUiState.Editing)?.photos?.size == 11 } }
    }

    @Test
    fun `a missing activity shows not found`(): Unit = runBlocking {
        val vm = vm(404)
        assertEquals(SaveEditUiState.NotFound, withTimeout(10_000) { vm.uiState.first { it != SaveEditUiState.Loading } })
    }

    @Test
    fun `perceived exertion has a cue for every level`() {
        assertEquals("Easy", PerceivedExertion.band(2))
        assertEquals("Max effort", PerceivedExertion.band(10))
        assertEquals(PerceivedExertion.band(1), PerceivedExertion.band(-3))
        assertTrue((1..10).all { PerceivedExertion.cue(it).isNotBlank() })
    }
}
