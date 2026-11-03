// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.fileimport.strava

import java.io.Reader

/**
 * A small RFC 4180 CSV reader: comma separated, fields optionally in double quotes, `""` for a quote
 * inside a quoted field, and line breaks allowed inside quotes (Strava descriptions have them). Accepts
 * CRLF, LF and a leading byte-order mark. Reads one record at a time, so large files stream.
 */
internal class CsvReader(private val reader: Reader) {
    private var pending = -2 // -2: nothing peeked yet
    private var first = true

    /** The next record's fields, or null at the end of the input. A blank line is an empty record. */
    fun next(): List<String>? {
        var c = read()
        if (first) {
            first = false
            if (c == BOM) c = read()
        }
        if (c == EOF) return null
        val fields = ArrayList<String>()
        val field = StringBuilder()
        var quoted = false
        var fieldStarted = false
        while (true) {
            if (quoted) {
                when (c) {
                    EOF -> {
                        fields += field.toString()
                        return fields
                    }
                    QUOTE -> {
                        val n = read()
                        if (n == QUOTE) {
                            field.append('"')
                        } else {
                            quoted = false
                            c = n
                            continue
                        }
                    }
                    else -> field.append(c.toChar())
                }
            } else {
                when (c) {
                    EOF -> {
                        fields += field.toString()
                        return fields
                    }
                    COMMA -> {
                        fields += field.toString()
                        field.setLength(0)
                        fieldStarted = false
                    }
                    CR -> {
                        val n = read()
                        if (n != LF) unread(n)
                        fields += field.toString()
                        return fields
                    }
                    LF -> {
                        fields += field.toString()
                        return fields
                    }
                    QUOTE -> if (!fieldStarted && field.isEmpty()) quoted = true else field.append('"')
                    else -> field.append(c.toChar())
                }
                if (c != COMMA) fieldStarted = true
            }
            c = read()
        }
    }

    private fun read(): Int {
        if (pending != -2) {
            val p = pending
            pending = -2
            return p
        }
        return reader.read()
    }

    private fun unread(c: Int) {
        pending = c
    }

    private companion object {
        const val EOF = -1
        const val BOM = 0xFEFF
        const val QUOTE = '"'.code
        const val COMMA = ','.code
        const val CR = '\r'.code
        const val LF = '\n'.code
    }
}
