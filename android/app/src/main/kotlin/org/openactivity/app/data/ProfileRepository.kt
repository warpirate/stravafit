// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.data

import android.net.Uri
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import org.openactivity.app.data.local.LocalPreferences
import org.openactivity.app.data.local.ProfilePhotoStore
import org.openactivity.app.model.UserProfile
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** The athlete's local profile (name, location, photo). */
interface ProfileRepository {
    /** The current profile; emits again on every change. */
    val profile: Flow<UserProfile>

    /** Saves name and location (trimmed). */
    suspend fun updateDetails(name: String, location: String)

    /**
     * Copies the picked image into app storage as the new profile photo.
     *
     * @throws IOException when the image cannot be read or decoded.
     */
    suspend fun setPhoto(source: Uri)

    /** Removes the profile photo. */
    suspend fun removePhoto()
}

/** [ProfileRepository] over [LocalPreferences] and the private [ProfilePhotoStore]. */
@Singleton
class LocalProfileRepository @Inject constructor(
    private val local: LocalPreferences,
    private val photos: ProfilePhotoStore,
) : ProfileRepository {

    override val profile: Flow<UserProfile> = local.profile

    override suspend fun updateDetails(name: String, location: String) = local.setProfileDetails(name, location)

    override suspend fun setPhoto(source: Uri) {
        val previous = local.profile.first().photoPath
        val path = photos.save(source)
        local.setPhotoPath(path)
        if (previous != null && previous != path) photos.delete(previous)
    }

    override suspend fun removePhoto() {
        val previous = local.profile.first().photoPath ?: return
        local.setPhotoPath(null)
        photos.delete(previous)
    }
}
