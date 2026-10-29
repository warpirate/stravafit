// SPDX-License-Identifier: Apache-2.0
package app.stravafit.lib.geojson.internal

import java.math.BigDecimal
import java.math.BigInteger

/** A number whose JSON text is already formatted (used for precision-limited coordinates). */
@JvmInline
internal value class RawNumber(val text: String)

/**
 * Serialises a tree of `Map<String, *>`, `List<*>`, `String`, `Number`, [RawNumber], `Boolean`
 * and `null` as RFC 8259 JSON. In pretty mode, arrays holding only scalars stay on one line so
 * coordinate lists remain readable.
 */
internal class JsonSerializer(private val pretty: Boolean) {
    private val out = StringBuilder()

    fun serialize(value: Any?): String {
        write(value, 0)
        if (pretty) out.append('\n')
        return out.toString()
    }

    private fun write(value: Any?, depth: Int) {
        when (value) {
            null -> out.append("null")
            is String -> writeString(value)
            is Boolean -> out.append(value)
            is RawNumber -> out.append(value.text)
            is Number -> out.append(formatNumber(value))
            is Map<*, *> -> writeObject(value, depth)
            is List<*> -> writeArray(value, depth)
            else -> throw IllegalArgumentException("Unsupported JSON value type: ${value::class.qualifiedName}")
        }
    }

    private fun writeObject(map: Map<*, *>, depth: Int) {
        if (map.isEmpty()) { out.append("{}"); return }
        out.append('{')
        map.entries.forEachIndexed { i, (key, value) ->
            require(key is String) { "JSON object keys must be strings, got $key" }
            if (i > 0) out.append(',')
            newline(depth + 1)
            writeString(key)
            out.append(if (pretty) ": " else ":")
            write(value, depth + 1)
        }
        newline(depth)
        out.append('}')
    }

    private fun writeArray(list: List<*>, depth: Int) {
        if (list.isEmpty()) { out.append("[]"); return }
        val inline = !pretty || list.none { it is Map<*, *> || (it is List<*> && it.isNotEmpty()) }
        out.append('[')
        list.forEachIndexed { i, value ->
            if (i > 0) out.append(if (pretty && inline) ", " else ",")
            if (!inline) newline(depth + 1)
            write(value, depth + 1)
        }
        if (!inline) newline(depth)
        out.append(']')
    }

    private fun newline(depth: Int) {
        if (!pretty) return
        out.append('\n')
        repeat(depth) { out.append("  ") }
    }

    private fun writeString(s: String) {
        out.append('"')
        for (c in s) {
            when (c) {
                '"' -> out.append("\\\"")
                '\\' -> out.append("\\\\")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                '\b' -> out.append("\\b")
                '\u000C' -> out.append("\\f")
                else -> if (c < ' ') out.append("\\u%04x".format(c.code)) else out.append(c)
            }
        }
        out.append('"')
    }
}

/** Shortest plain (non-exponent) decimal text for [value]; rejects NaN and infinities. */
internal fun formatNumber(value: Number): String = when (value) {
    is Int, is Long, is Short, is Byte, is BigInteger -> value.toString()
    is BigDecimal -> value.stripTrailingZeros().toPlainString().normaliseZero()
    else -> {
        val d = value.toDouble()
        require(d.isFinite()) { "JSON cannot represent $d" }
        if (d == Math.rint(d) && kotlin.math.abs(d) < 1e15) {
            d.toLong().toString()
        } else {
            BigDecimal(d.toString()).stripTrailingZeros().toPlainString().normaliseZero()
        }
    }
}

private fun String.normaliseZero(): String = if (this == "-0") "0" else this
