// SPDX-License-Identifier: Apache-2.0
package app.stravafit.lib.gpxtcx.internal

import app.stravafit.lib.gpxtcx.TrackFileFormatException
import java.io.BufferedInputStream
import java.io.InputStream
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants
import javax.xml.stream.XMLStreamException
import javax.xml.stream.XMLStreamReader

/**
 * Creates a namespace-aware StAX reader hardened against XXE: DTDs and external entities are
 * disabled. A UTF-8 byte-order mark and leading whitespace before the XML declaration, both common
 * in device exports and fatal to strict parsers, are skipped first.
 */
internal fun openXml(input: InputStream): XMLStreamReader {
    val factory = XMLInputFactory.newFactory().apply {
        setProperty(XMLInputFactory.SUPPORT_DTD, false)
        setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false)
        setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, true)
        setProperty(XMLInputFactory.IS_COALESCING, true)
    }
    return try {
        factory.createXMLStreamReader(skipLeadingJunk(input))
    } catch (e: XMLStreamException) {
        throw e.toFormatException()
    }
}

private fun skipLeadingJunk(input: InputStream): InputStream {
    val buffered = if (input.markSupported()) input else BufferedInputStream(input)
    while (true) {
        buffered.mark(3)
        val b = buffered.read()
        when {
            b == -1 -> { buffered.reset(); return buffered }
            b == ' '.code || b == '\t'.code || b == '\r'.code || b == '\n'.code -> continue
            b == 0xEF && buffered.read() == 0xBB && buffered.read() == 0xBF -> continue
            else -> { buffered.reset(); return buffered }
        }
    }
}

internal fun XMLStreamException.toFormatException(): TrackFileFormatException =
    TrackFileFormatException(
        message = "Malformed XML: ${message?.substringAfter("Message: ")?.trim() ?: "unknown error"}",
        line = location?.lineNumber ?: -1,
        column = location?.columnNumber ?: -1,
        cause = this,
    )

/** Advances to the document's root element and returns its local name. */
internal fun XMLStreamReader.moveToRoot(): String {
    while (hasNext()) {
        if (next() == XMLStreamConstants.START_ELEMENT) return localName
    }
    throw XMLStreamException("Document has no root element", location)
}

/**
 * Calls [onChild] for every child element of the element the reader is positioned on. [onChild]
 * must consume the whole child, leaving the reader on the child's END_ELEMENT (use [skipElement]
 * for children it does not care about). Returns with the reader on the parent's END_ELEMENT.
 */
internal inline fun XMLStreamReader.forEachChild(onChild: (localName: String) -> Unit) {
    while (true) {
        when (next()) {
            XMLStreamConstants.START_ELEMENT -> onChild(localName)
            XMLStreamConstants.END_ELEMENT -> return
            XMLStreamConstants.END_DOCUMENT -> throw XMLStreamException("Unexpected end of document", location)
        }
    }
}

/** Skips the current element and all its descendants. */
internal fun XMLStreamReader.skipElement() {
    var depth = 1
    while (depth > 0) {
        when (next()) {
            XMLStreamConstants.START_ELEMENT -> depth++
            XMLStreamConstants.END_ELEMENT -> depth--
            XMLStreamConstants.END_DOCUMENT -> throw XMLStreamException("Unexpected end of document", location)
        }
    }
}

/**
 * Returns the trimmed direct text content of the current element (text of nested elements is
 * ignored) and leaves the reader on its END_ELEMENT. Blank content yields `null`.
 */
internal fun XMLStreamReader.readText(): String? {
    val text = StringBuilder()
    var depth = 1
    while (depth > 0) {
        when (next()) {
            XMLStreamConstants.START_ELEMENT -> depth++
            XMLStreamConstants.END_ELEMENT -> depth--
            XMLStreamConstants.CHARACTERS, XMLStreamConstants.CDATA ->
                if (depth == 1) text.append(this.text)
            XMLStreamConstants.END_DOCUMENT -> throw XMLStreamException("Unexpected end of document", location)
        }
    }
    return text.toString().trim().ifEmpty { null }
}

/** Value of the attribute with [localName] in any (or no) namespace, ignoring case. */
internal fun XMLStreamReader.attribute(localName: String): String? {
    for (i in 0 until attributeCount) {
        if (getAttributeLocalName(i).equals(localName, ignoreCase = true)) return getAttributeValue(i)
    }
    return null
}
