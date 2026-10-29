// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.feature.profile

import android.net.Uri
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.openactivity.app.model.UserProfile
import org.openactivity.app.testing.FakeProfileRepository
import org.openactivity.app.testing.MainDispatcherRule
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class EditProfileViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val repository = FakeProfileRepository(UserProfile(name = "Asha", location = "Pune"))

    @Test
    fun `loads the profile, edits and saves it`() = runTest(main.dispatcher) {
        val vm = EditProfileViewModel(repository)
        assertTrue(vm.uiState.value.isLoading)
        advanceUntilIdle()
        assertEquals("Asha", vm.uiState.value.name)
        vm.setName("  Asha Rao ")
        vm.setLocation("Bengaluru")
        vm.save()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.saved)
        assertEquals(UserProfile("Asha Rao", "Bengaluru"), repository.state.value)
    }

    @Test
    fun `photo can be set and removed, and bad images report an error`() = runTest(main.dispatcher) {
        val vm = EditProfileViewModel(repository)
        advanceUntilIdle()
        vm.setPhoto(Uri.parse("content://media/42"))
        advanceUntilIdle()
        assertEquals("/photos/42", vm.uiState.value.photoPath)
        vm.removePhoto()
        advanceUntilIdle()
        assertNull(vm.uiState.value.photoPath)

        repository.failPhoto = true
        vm.setPhoto(Uri.parse("content://media/43"))
        advanceUntilIdle()
        assertEquals("That image could not be used. Try another one.", vm.uiState.value.error)
        vm.errorShown()
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun `names are capped`() = runTest(main.dispatcher) {
        val vm = EditProfileViewModel(repository)
        advanceUntilIdle()
        vm.setName("x".repeat(100))
        assertEquals(EditProfileUiState.MAX_NAME_LENGTH, vm.uiState.value.name.length)
        assertFalse(vm.uiState.value.saved)
    }
}
