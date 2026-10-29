// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.activityextras

import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Moves [source] over [target], atomically where the file system supports it. */
internal fun replaceAtomically(source: File, target: File) {
    try {
        try {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    } catch (e: IOException) {
        source.delete()
        throw e
    }
}
