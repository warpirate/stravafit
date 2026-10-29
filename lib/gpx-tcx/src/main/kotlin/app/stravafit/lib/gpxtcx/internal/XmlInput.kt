// SPDX-License-Identifier: Apache-2.0
package app.stravafit.lib.gpxtcx.internal

import app.stravafit.lib.gpxtcx.TrackFileFormatException
import java.io.BufferedInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.Charset
import java.nio.charset.IllegalCharsetNameException
import java.nio.charset.UnsupportedCharsetException

/**
 * Opens [input] for pull parsing with the dependency-free [XmlPullReader], which works the same on
 * the JVM and on Android. The character encoding is detected per XML 1.0 Appendix F: a UTF-8 or
 * UTF-16 byte-order mark wins, otherwise the `encoding` pseudo-attribute of the XML declaration,
 * otherwise UTF-8. Leading whitespace before the XML declaration (common in device exports and
 * fatal to strict parsers) is tolerated. DTDs are never interpreted, see [XmlPullReader].
 */
internal fun openXml(input: InputStream): XmlPullReader {
    val buffered = if (input.markSupported()) input else BufferedInputStream(input)
    return XmlPullReader(InputStreamReader(buffered, detectCharset(buffered)).buffered(BUFFER))
}

private const val BUFFER = 16 * 1024
private const val SNIFF_LENGTH = 512

/** Detects the encoding and leaves [input] positioned after any byte-order mark. */
private fun detectCharset(input: InputStream): Charset {
    input.mark(SNIFF_LENGTH)
    val head = ByteArray(SNIFF_LENGTH)
    var n = 0
    while (n < head.size) {
        val r = input.read(head, n, head.size - n)
        if (r < 0) break
        n += r
    }
    input.reset()
    fun b(i: Int) = if (i < n) head[i].toInt() and 0xFF else -1
    return when {
        b(0) == 0xEF && b(1) == 0xBB && b(2) == 0xBF -> { input.skipFully(3); Charsets.UTF_8 }
        b(0) == 0xFE && b(1) == 0xFF -> { input.skipFully(2); Charsets.UTF_16BE }
        b(0) == 0xFF && b(1) == 0xFE -> { input.skipFully(2); Charsets.UTF_16LE }
        b(0) == 0x00 && b(1) == '<'.code -> Charsets.UTF_16BE
        b(0) == '<'.code && b(1) == 0x00 -> Charsets.UTF_16LE
        else -> declaredCharset(String(head, 0, n, Charsets.ISO_8859_1)) ?: Charsets.UTF_8
    }
}

private val ENCODING_ATTRIBUTE = Regex("""^\s*<\?xml[^>]*?\bencoding\s*=\s*["']([A-Za-z0-9._\-]+)["']""")

private fun declaredCharset(head: String): Charset? {
    val name = ENCODING_ATTRIBUTE.find(head)?.groupValues?.get(1) ?: return null
    return try {
        // A UTF-16 declaration without a BOM in an ASCII-compatible byte stream is a lie; ignore it.
        Charset.forName(name).takeUnless { it.name().startsWith("UTF-16") || it.name().startsWith("UTF-32") }
    } catch (_: IllegalCharsetNameException) {
        null
    } catch (_: UnsupportedCharsetException) {
        null
    }
}

private fun InputStream.skipFully(count: Int) {
    repeat(count) { read() }
}

internal fun XmlSyntaxException.toFormatException(): TrackFileFormatException =
    TrackFileFormatException(
        message = "Malformed XML: ${message ?: "unknown error"}",
        line = line,
        column = column,
        cause = this,
    )

/** Advances to the document's root element and returns its local name. */
internal fun XmlPullReader.moveToRoot(): String {
    while (hasNext()) {
        if (next() == XmlEvent.START_ELEMENT) return localName
    }
    throw XmlSyntaxException("Document has no root element", eventLine, eventColumn)
}

/**
 * Calls [onChild] for every child element of the element the reader is positioned on. [onChild]
 * must consume the whole child, leaving the reader on the child's END_ELEMENT (use [skipElement]
 * for children it does not care about). Returns with the reader on the parent's END_ELEMENT.
 */
internal inline fun XmlPullReader.forEachChild(onChild: (localName: String) -> Unit) {
    while (true) {
        when (next()) {
            XmlEvent.START_ELEMENT -> onChild(localName)
            XmlEvent.END_ELEMENT -> return
            XmlEvent.END_DOCUMENT -> throw XmlSyntaxException("Unexpected end of document", eventLine, eventColumn)
        }
    }
}

/** Skips the current element and all its descendants. */
internal fun XmlPullReader.skipElement() {
    var depth = 1
    while (depth > 0) {
        when (next()) {
            XmlEvent.START_ELEMENT -> depth++
            XmlEvent.END_ELEMENT -> depth--
            XmlEvent.END_DOCUMENT -> throw XmlSyntaxException("Unexpected end of document", eventLine, eventColumn)
        }
    }
}

/**
 * Returns the trimmed direct text content of the current element (text of nested elements is
 * ignored) and leaves the reader on its END_ELEMENT. Blank content yields `null`.
 */
internal fun XmlPullReader.readText(): String? {
    val text = StringBuilder()
    var depth = 1
    while (depth > 0) {
        when (next()) {
            XmlEvent.START_ELEMENT -> depth++
            XmlEvent.END_ELEMENT -> depth--
            XmlEvent.CHARACTERS -> if (depth == 1) text.append(this.text)
            XmlEvent.END_DOCUMENT -> throw XmlSyntaxException("Unexpected end of document", eventLine, eventColumn)
        }
    }
    return text.toString().trim().ifEmpty { null }
}

/** Value of the attribute with [localName] in any (or no) namespace, ignoring case. */
internal fun XmlPullReader.attribute(localName: String): String? {
    for (i in 0 until attributeCount) {
        if (getAttributeLocalName(i).equals(localName, ignoreCase = true)) return getAttributeValue(i)
    }
    return null
}
