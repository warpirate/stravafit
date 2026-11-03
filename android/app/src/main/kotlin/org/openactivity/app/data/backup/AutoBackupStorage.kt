// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.data.backup

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.openactivity.core.database.OpenActivityDatabase
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.OutputStream
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [AutoBackupStore] in its own preferences file under `no_backup/`, outside the `files/` a backup carries:
 * restoring a backup from another phone must not bring along a folder grant that does not exist here, and
 * must not rewind the record of the last automatic backup. An unreadable file reads as empty.
 */
@Singleton
class DataStoreAutoBackupStore internal constructor(private val dataStore: DataStore<Preferences>) : AutoBackupStore {

    @Inject
    constructor(@ApplicationContext context: Context) : this(storeFor(context))

    private val data: Flow<Preferences> = dataStore.data.catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }

    override val config: Flow<AutoBackupConfig> = data.map { p ->
        AutoBackupConfig(
            enabled = p[Keys.ENABLED] ?: false,
            folderUri = p[Keys.FOLDER_URI],
            folderName = p[Keys.FOLDER_NAME],
            frequency = AutoBackupFrequency.entries.firstOrNull { it.name == p[Keys.FREQUENCY] } ?: AutoBackupFrequency.WEEKLY,
        )
    }.distinctUntilChanged()

    override val status: Flow<AutoBackupStatus> = data.map { p ->
        AutoBackupStatus(
            lastSuccessAt = p[Keys.SUCCESS_AT]?.let(Instant::ofEpochMilli),
            lastSuccessFile = p[Keys.SUCCESS_FILE],
            lastActivityCount = p[Keys.SUCCESS_COUNT],
            lastFailureAt = p[Keys.FAILURE_AT]?.let(Instant::ofEpochMilli),
            lastFailure = AutoBackupFailure.entries.firstOrNull { it.name == p[Keys.FAILURE] },
        )
    }.distinctUntilChanged()

    override suspend fun updateConfig(change: (AutoBackupConfig) -> AutoBackupConfig) {
        dataStore.edit { p ->
            val current = AutoBackupConfig(
                enabled = p[Keys.ENABLED] ?: false,
                folderUri = p[Keys.FOLDER_URI],
                folderName = p[Keys.FOLDER_NAME],
                frequency = AutoBackupFrequency.entries.firstOrNull { it.name == p[Keys.FREQUENCY] } ?: AutoBackupFrequency.WEEKLY,
            )
            val next = change(current)
            p[Keys.ENABLED] = next.enabled
            p[Keys.FREQUENCY] = next.frequency.name
            if (next.folderUri == null) p.remove(Keys.FOLDER_URI) else p[Keys.FOLDER_URI] = next.folderUri
            if (next.folderName == null) p.remove(Keys.FOLDER_NAME) else p[Keys.FOLDER_NAME] = next.folderName
        }
    }

    override suspend fun recordSuccess(at: Instant, fileName: String, activityCount: Int) {
        dataStore.edit { p ->
            p[Keys.SUCCESS_AT] = at.toEpochMilli()
            p[Keys.SUCCESS_FILE] = fileName
            p[Keys.SUCCESS_COUNT] = activityCount
            p.remove(Keys.FAILURE)
            p.remove(Keys.FAILURE_AT)
        }
    }

    override suspend fun recordFailure(at: Instant, failure: AutoBackupFailure) {
        dataStore.edit { p ->
            p[Keys.FAILURE_AT] = at.toEpochMilli()
            p[Keys.FAILURE] = failure.name
        }
    }

    private object Keys {
        val ENABLED = booleanPreferencesKey("enabled")
        val FOLDER_URI = stringPreferencesKey("folder_uri")
        val FOLDER_NAME = stringPreferencesKey("folder_name")
        val FREQUENCY = stringPreferencesKey("frequency")
        val SUCCESS_AT = longPreferencesKey("last_success_at")
        val SUCCESS_FILE = stringPreferencesKey("last_success_file")
        val SUCCESS_COUNT = intPreferencesKey("last_success_activities")
        val FAILURE_AT = longPreferencesKey("last_failure_at")
        val FAILURE = stringPreferencesKey("last_failure")
    }

    private companion object {
        @Volatile private var instance: DataStore<Preferences>? = null

        /** One DataStore per file and process, as DataStore requires. */
        fun storeFor(context: Context): DataStore<Preferences> = instance ?: synchronized(this) {
            instance ?: PreferenceDataStoreFactory.create(
                produceFile = { File(context.applicationContext.noBackupFilesDir, "auto_backup.preferences_pb") },
            ).also { instance = it }
        }
    }
}

/** [BackupFolders] over Storage Access Framework trees (local storage, Google Drive, Nextcloud...). */
class SafBackupFolders @Inject constructor(@ApplicationContext private val context: Context) : BackupFolders {

    override fun open(uri: String): BackupFolder {
        val resolver = context.contentResolver
        val tree = Uri.parse(uri)
        val granted = resolver.persistedUriPermissions.any { it.uri == tree && it.isWritePermission }
        if (!granted) throw AutoBackupFolderException(AutoBackupFailure.FOLDER_REVOKED)
        val folder = try {
            DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        } catch (e: IllegalArgumentException) {
            throw AutoBackupFolderException(AutoBackupFailure.FOLDER_MISSING)
        }
        if (!exists(resolver, folder)) throw AutoBackupFolderException(AutoBackupFailure.FOLDER_MISSING)
        return SafBackupFolder(resolver, tree, folder)
    }

    /** True when the provider still knows [document]; a deleted folder throws or returns no row. */
    private fun exists(resolver: ContentResolver, document: Uri): Boolean = try {
        resolver.query(document, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID), null, null, null)?.use { it.moveToFirst() } ?: false
    } catch (e: FileNotFoundException) {
        false
    } catch (e: IllegalArgumentException) {
        false
    } catch (e: IllegalStateException) {
        false
    }

    /** Remembers access to [tree] across restarts; returns its display name. */
    fun takePermission(tree: Uri): String? {
        context.contentResolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        return displayName(tree)
    }

    /** Gives up access to a folder no longer used. */
    fun releasePermission(tree: Uri) {
        runCatching {
            context.contentResolver.releasePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
    }

    private fun displayName(tree: Uri): String? = runCatching {
        val folder = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        context.contentResolver.query(folder, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null }
    }.getOrNull()
}

/** One SAF folder. */
private class SafBackupFolder(
    private val resolver: ContentResolver,
    private val tree: Uri,
    private val folder: Uri,
) : BackupFolder {

    override fun list(): List<BackupFolderFile> {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getDocumentId(folder))
        val projection = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        val cursor = resolver.query(children, projection, null, null, null) ?: return emptyList()
        return cursor.use { c ->
            buildList {
                while (c.moveToNext()) {
                    val id = c.getString(0) ?: continue
                    add(BackupFolderFile(DocumentsContract.buildDocumentUriUsingTree(tree, id).toString(), c.getString(1).orEmpty()))
                }
            }
        }
    }

    override fun create(name: String): BackupFolderFile {
        val created = DocumentsContract.createDocument(resolver, folder, MIME_ZIP, name) ?: throw FileNotFoundException("The folder did not accept a new file")
        return BackupFolderFile(created.toString(), name)
    }

    override fun openOutput(file: BackupFolderFile): OutputStream =
        resolver.openOutputStream(Uri.parse(file.id), "wt") ?: throw IOException("Cannot open ${file.name}")

    override fun delete(file: BackupFolderFile): Boolean = DocumentsContract.deleteDocument(resolver, Uri.parse(file.id))

    private companion object {
        const val MIME_ZIP = "application/zip"
    }
}

/** [LocalSpaceCheck] over the partition holding the database and the snapshot scratch space. */
class FileLocalSpaceCheck @Inject constructor(@ApplicationContext private val context: Context) : LocalSpaceCheck {
    override fun hasRoomForSnapshot(): Boolean {
        val db = context.getDatabasePath(OpenActivityDatabase.NAME)
        val needed = db.length() + File(db.path + "-wal").length() + MARGIN_BYTES
        return context.noBackupFilesDir.usableSpace >= needed
    }

    private companion object {
        /** Room for the ZIP's own buffers and the snapshot's journal. */
        const val MARGIN_BYTES = 8L * 1024 * 1024
    }
}
