// SPDX-License-Identifier: Apache-2.0
package app.stravafit.lib.geojson.internal

import app.stravafit.lib.geojson.GeoJsonFormatException

/**
 * Strict RFC 8259 JSON parser producing `LinkedHashMap<String, Any?>`, `List<Any?>`, `String`,
 * `Long` (integers that fit), `Double`, `Boolean` and `null`. Nesting is limited to [maxDepth] to
 * bound stack use on hostile input. Duplicate object keys keep the last value.
 */
internal class JsonParser(private val text: String, private val maxDepth: Int = 256) {
    private var pos = 0

    fun parse(): Any? {
        skipWhitespace()
        val value = readValue(0)
        skipWhitespace()
        if (pos != text.length) fail("Unexpected trailing content")
        return value
    }

    private fun readValue(depth: Int): Any? {
        if (depth > maxDepth) fail("Nesting deeper than $maxDepth levels")
        if (pos >= text.length) fail("Unexpected end of input")
        return when (val c = text[pos]) {
            '{' -> readObject(depth)
            '[' -> readArray(depth)
            '"' -> readString()
            't' -> readLiteral("true", true)
            'f' -> readLiteral("false", false)
            'n' -> readLiteral("null", null)
            else -> if (c == '-' || c in '0'..'9') readNumber() else fail("Unexpected character '$c'")
        }
    }

    private fun readObject(depth: Int): Map<String, Any?> {
        pos++ // {
        val map = LinkedHashMap<String, Any?>()
        skipWhitespace()
        if (peek() == '}') { pos++; return map }
        while (true) {
            skipWhitespace()
            if (peek() != '"') fail("Expected a string key")
            val key = readString()
            skipWhitespace()
            expect(':')
            skipWhitespace()
            map[key] = readValue(depth + 1)
            skipWhitespace()
            when (peek()) {
                ',' -> pos++
                '}' -> { pos++; return map }
                else -> fail("Expected ',' or '}'")
            }
        }
    }

    private fun readArray(depth: Int): List<Any?> {
        pos++ // [
        val list = ArrayList<Any?>()
        skipWhitespace()
        if (peek() == ']') { pos++; return list }
        while (true) {
            skipWhitespace()
            list += readValue(depth + 1)
            skipWhitespace()
            when (peek()) {
                ',' -> pos++
                ']' -> { pos++; return list }
                else -> fail("Expected ',' or ']'")
            }
        }
    }

    private fun readString(): String {
        pos++ // opening quote
        val sb = StringBuilder()
        while (true) {
            if (pos >= text.length) fail("Unterminated string")
            when (val c = text[pos++]) {
                '"' -> return sb.toString()
                '\\' -> sb.append(readEscape())
                else -> if (c < ' ') fail("Unescaped control character in string") else sb.append(c)
            }
        }
    }

    private fun readEscape(): Char {
        if (pos >= text.length) fail("Unterminated escape")
        return when (val c = text[pos++]) {
            '"' -> '"'
            '\\' -> '\\'
            '/' -> '/'
            'b' -> '\b'
            'f' -> '\u000C'
            'n' -> '\n'
            'r' -> '\r'
            't' -> '\t'
            'u' -> {
                if (pos + 4 > text.length) fail("Truncated \\u escape")
                val hex = text.substring(pos, pos + 4)
                pos += 4
                hex.toIntOrNull(16)?.toChar() ?: fail("Invalid \\u escape '$hex'")
            }
            else -> fail("Invalid escape '\\$c'")
        }
    }

    private fun readNumber(): Number {
        val start = pos
        if (peek() == '-') pos++
        when {
            peek() == '0' -> pos++
            peek() in '1'..'9' -> while (peek() in '0'..'9') pos++
            else -> fail("Invalid number")
        }
        var integral = true
        if (peek() == '.') {
            integral = false
            pos++
            if (peek() !in '0'..'9') fail("Invalid number: digit expected after '.'")
            while (peek() in '0'..'9') pos++
        }
        if (peek() == 'e' || peek() == 'E') {
            integral = false
            pos++
            if (peek() == '+' || peek() == '-') pos++
            if (peek() !in '0'..'9') fail("Invalid number: digit expected in exponent")
            while (peek() in '0'..'9') pos++
        }
        val literal = text.substring(start, pos)
        if (integral) literal.toLongOrNull()?.let { return it }
        val value = literal.toDouble()
        if (!value.isFinite()) fail("Number out of range: $literal")
        return value
    }

    private fun readLiteral(word: String, value: Any?): Any? {
        if (!text.startsWith(word, pos)) fail("Invalid literal")
        pos += word.length
        return value
    }

    private fun expect(c: Char) {
        if (peek() != c) fail("Expected '$c'")
        pos++
    }

    private fun peek(): Char = if (pos < text.length) text[pos] else '\u0000'

    private fun skipWhitespace() {
        while (pos < text.length && text[pos].let { it == ' ' || it == '\t' || it == '\n' || it == '\r' }) pos++
    }

    private fun fail(message: String): Nothing {
        val before = text.substring(0, pos.coerceAtMost(text.length))
        val line = before.count { it == '\n' } + 1
        val column = pos - (before.lastIndexOf('\n') + 1) + 1
        throw GeoJsonFormatException("$message at line $line, column $column")
    }
}
