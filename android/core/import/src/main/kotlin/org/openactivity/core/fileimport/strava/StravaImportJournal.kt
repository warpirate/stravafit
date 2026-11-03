// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.fileimport.strava

import java.io.File
import java.io.IOException

/** What became of one row of `activities.csv`. */
public sealed interface RowOutcome {
    /** Stored as activity [activityId]. */
    public data class Imported(val activityId: Long) : RowOutcome

    /** Already on the phone as [activityId] (an earlier import, or the same recording from elsewhere). */
    public data class Duplicate(val activityId: Long) : RowOutcome

    /** Could not be imported, for [reason]. */
    public data class Failed(val reason: String) : RowOutcome
}

/** A row's outcome with the name to show for it. */
public data class JournalEntry(val rowId: String, val name: String, val outcome: RowOutcome)

/**
 * Remembers what a Strava import has done, so an import that is stopped (the app is killed, the phone
 * restarts, the user cancels) continues where it stopped instead of starting over or importing anything
 * twice. Every row and photo is written down as soon as it is done.
 */
public interface StravaImportJournal {
    /** Every row done so far, in the order they were done. */
    public suspend fun entries(): List<JournalEntry>

    /** Photo paths inside the export already attached to their activity. */
    public suspend fun photos(): Set<String>

    public suspend fun record(entry: JournalEntry)

    public suspend fun recordPhoto(path: String)

    /** Forgets everything, for a new import. */
    public suspend fun clear()
}

/**
 * [StravaImportJournal] as an append-only text file: one line per row or photo, written as soon as it
 * is done. A line cut short by a crash is ignored when the file is read back, so the worst case is
 * redoing that one row, which the importer's duplicate check then catches.
 */
public class FileStravaImportJournal(private val file: File) : StravaImportJournal {
    private val lock = Any()

    override suspend fun entries(): List<JournalEntry> = lines().mapNotNull(::parseRow)

    override suspend fun photos(): Set<String> =
        lines().filter { it.startsWith(PHOTO) }.mapNotNull { it.split(TAB).getOrNull(1)?.let(::unescape) }.toSet()

    override suspend fun record(entry: JournalEntry) {
        val (kind, value) = when (val o = entry.outcome) {
            is RowOutcome.Imported -> "imported" to o.activityId.toString()
            is RowOutcome.Duplicate -> "duplicate" to o.activityId.toString()
            is RowOutcome.Failed -> "failed" to o.reason
        }
        append(listOf(ROW, escape(entry.rowId), kind, escape(value), escape(entry.name)).joinToString(TAB) + END)
    }

    override suspend fun recordPhoto(path: String) {
        append(PHOTO + TAB + escape(path) + END)
    }

    override suspend fun clear() {
        synchronized(lock) { if (file.exists() && !file.delete()) throw IOException("Could not reset ${file.name}") }
    }

    private fun append(line: String) {
        synchronized(lock) {
            file.parentFile?.mkdirs()
            java.io.FileOutputStream(file, true).use { out ->
                out.write(line.toByteArray(Charsets.UTF_8))
                // No fsync: a killed process loses nothing once write returns; only a power cut can, and the
                // duplicate check catches the rows redone after that.
                out.flush()
            }
        }
    }

    /** Complete lines only: each ends with [END] before its newline, so a torn last line is dropped. */
    private fun lines(): List<String> = synchronized(lock) {
        if (!file.exists()) return emptyList()
        file.readText(Charsets.UTF_8).split('\n').filter { it.endsWith(END_MARK) }.map { it.removeSuffix(END_MARK) }
    }

    private fun parseRow(line: String): JournalEntry? {
        if (!line.startsWith(ROW)) return null
        val parts = line.split(TAB)
        if (parts.size < 5) return null
        val value = unescape(parts[3])
        val outcome = when (parts[2]) {
            "imported" -> value.toLongOrNull()?.let { RowOutcome.Imported(it) }
            "duplicate" -> value.toLongOrNull()?.let { RowOutcome.Duplicate(it) }
            "failed" -> RowOutcome.Failed(value)
            else -> null
        } ?: return null
        return JournalEntry(unescape(parts[1]), unescape(parts[4]), outcome)
    }

    private companion object {
        const val ROW = "R"
        const val PHOTO = "P"
        const val TAB = "\t"
        const val END_MARK = "\t."
        const val END = "$END_MARK\n"

        fun escape(text: String) = text.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n").replace("\r", "")

        fun unescape(text: String): String {
            val out = StringBuilder(text.length)
            var i = 0
            while (i < text.length) {
                val c = text[i]
                if (c == '\\' && i + 1 < text.length) {
                    when (text[i + 1]) {
                        't' -> out.append('\t')
                        'n' -> out.append('\n')
                        else -> out.append(text[i + 1])
                    }
                    i += 2
                } else {
                    out.append(c)
                    i++
                }
            }
            return out.toString()
        }
    }
}

/** [StravaImportJournal] in memory, for tests and for previews. */
public class InMemoryStravaImportJournal : StravaImportJournal {
    private val rows = ArrayList<JournalEntry>()
    private val photoPaths = LinkedHashSet<String>()

    override suspend fun entries(): List<JournalEntry> = synchronized(rows) { rows.toList() }

    override suspend fun photos(): Set<String> = synchronized(rows) { photoPaths.toSet() }

    override suspend fun record(entry: JournalEntry) {
        synchronized(rows) { rows += entry }
    }

    override suspend fun recordPhoto(path: String) {
        synchronized(rows) { photoPaths += path }
    }

    override suspend fun clear() {
        synchronized(rows) {
            rows.clear()
            photoPaths.clear()
        }
    }
}
