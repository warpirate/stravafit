// SPDX-License-Identifier: Apache-2.0
package app.stravafit.lib.gpxtcx.internal

import java.io.Closeable
import java.io.Reader

/** Thrown by [XmlPullReader] for input that is not well-formed (or uses a forbidden construct). */
internal class XmlSyntaxException(
    message: String,
    val line: Int,
    val column: Int,
) : Exception(message)

/** Event kinds produced by [XmlPullReader.next]. */
internal object XmlEvent {
    const val START_ELEMENT: Int = 1
    const val END_ELEMENT: Int = 2
    const val CHARACTERS: Int = 4
    const val END_DOCUMENT: Int = 8
}

/**
 * A small, dependency-free, namespace-aware XML pull tokenizer.
 *
 * It exists because `javax.xml.stream` is not available on Android, and the platform's own pull
 * parser is not available on a plain JVM. It supports exactly what track files need:
 *
 * - elements and attributes with optional namespace prefixes; `xmlns` declarations are resolved
 *   per scope and exposed through [namespaceUri], and are not reported as attributes;
 * - text, CDATA sections (reported as [XmlEvent.CHARACTERS]), comments and processing
 *   instructions (skipped), and the XML declaration;
 * - the five predefined entities plus decimal and hexadecimal character references.
 *
 * Security: a `<!DOCTYPE>` (including any internal subset) is skipped without being interpreted,
 * so custom entities are never defined and a reference to one is a syntax error. Nothing external
 * is ever loaded, which rules out XXE and entity-expansion attacks.
 *
 * Well-formedness checks: matching end tags, a single root element, quoted attribute values,
 * no duplicate attributes, and no premature end of input. Content after the root element is not
 * read. Byte-order marks and encodings are handled by [XmlSource] before characters reach this class.
 */
internal class XmlPullReader(private val reader: Reader) : Closeable {

    private val buffer = CharArray(BUFFER_SIZE)
    private var bufferLength = 0
    private var bufferPos = 0

    private var line = 1
    private var column = 1

    private var event = 0
    private var pendingEnd = false
    private var rootSeen = false
    private var done = false

    private val openNames = ArrayList<String>()
    private val namespaceScopes = ArrayList<Map<String, String>>()

    private var qualifiedName = ""
    private val attrNames = ArrayList<String>()
    private val attrValues = ArrayList<String>()
    private val textBuilder = StringBuilder()

    /** Local name (without prefix) of the current START/END element. */
    var localName: String = ""
        private set

    /** Namespace URI of the current START/END element, or `null` when it is in no namespace. */
    var namespaceUri: String? = null
        private set

    /** Line (1-based) at which the current event started. */
    var eventLine: Int = 1
        private set

    /** Column (1-based) at which the current event started. */
    var eventColumn: Int = 1
        private set

    /** Text of the current [XmlEvent.CHARACTERS] event. */
    val text: String get() = textBuilder.toString()

    /** Number of (non-namespace-declaration) attributes on the current START_ELEMENT. */
    val attributeCount: Int get() = if (event == XmlEvent.START_ELEMENT) attrNames.size else 0

    /** Local name of attribute [index]. */
    fun getAttributeLocalName(index: Int): String = attrNames[index].substringAfter(':')

    /** Qualified name of attribute [index], prefix included. */
    fun getAttributeQualifiedName(index: Int): String = attrNames[index]

    /** Value of attribute [index] with entities expanded. */
    fun getAttributeValue(index: Int): String = attrValues[index]

    /** `false` once [XmlEvent.END_DOCUMENT] has been returned. */
    fun hasNext(): Boolean = event != XmlEvent.END_DOCUMENT

    /** Advances to the next event and returns its kind (see [XmlEvent]). */
    fun next(): Int {
        event = advance()
        return event
    }

    override fun close() {
        reader.close()
    }

    private fun advance(): Int {
        if (pendingEnd) {
            pendingEnd = false
            popElement()
            return XmlEvent.END_ELEMENT
        }
        if (done) return XmlEvent.END_DOCUMENT
        while (true) {
            eventLine = line
            eventColumn = column
            val c = peek()
            if (c < 0) {
                if (openNames.isNotEmpty()) fail("Unexpected end of document inside <${openNames.last()}>")
                if (!rootSeen) fail("Document has no root element")
                done = true
                return XmlEvent.END_DOCUMENT
            }
            if (c != '<'.code) {
                if (openNames.isEmpty()) {
                    // Outside the root only whitespace is allowed.
                    val ch = read()
                    if (!ch.toChar().isXmlWhitespace()) fail("Content is not allowed outside the root element")
                    continue
                }
                readText()
                return XmlEvent.CHARACTERS
            }
            read() // '<'
            when (peek()) {
                '/'.code -> { read(); readEndTag(); return XmlEvent.END_ELEMENT }
                '?'.code -> { read(); skipUntil("?>", "processing instruction") }
                '!'.code -> {
                    read()
                    when {
                        tryConsume("--") -> skipUntil("-->", "comment")
                        tryConsume("[CDATA[") -> {
                            if (openNames.isEmpty()) fail("CDATA is not allowed outside the root element")
                            textBuilder.setLength(0)
                            readUntil("]]>", "CDATA section", textBuilder)
                            return XmlEvent.CHARACTERS
                        }
                        tryConsume("DOCTYPE") -> {
                            if (rootSeen) fail("DOCTYPE is only allowed before the root element")
                            skipDoctype()
                        }
                        else -> fail("Unsupported markup declaration")
                    }
                }
                else -> {
                    if (openNames.isEmpty() && rootSeen) {
                        // Trailing content after the root is not read (matches the readers' needs).
                        done = true
                        return XmlEvent.END_DOCUMENT
                    }
                    readStartTag()
                    return XmlEvent.START_ELEMENT
                }
            }
        }
    }

    // ---- elements --------------------------------------------------------------------------------

    private fun readStartTag() {
        val name = readName("element name")
        attrNames.clear()
        attrValues.clear()
        var declarations: HashMap<String, String>? = null
        while (true) {
            val hadSpace = skipWhitespace()
            when (val c = peek()) {
                '>'.code -> { read(); break }
                '/'.code -> {
                    read()
                    expect('>')
                    pendingEnd = true
                    break
                }
                -1 -> fail("Unexpected end of document in start tag <$name>")
                else -> {
                    if (!hadSpace) fail("Expected whitespace before attribute in <$name> but found '${c.toChar()}'")
                    val attrName = readName("attribute name")
                    skipWhitespace()
                    expect('=')
                    skipWhitespace()
                    val value = readAttributeValue()
                    when {
                        attrName == "xmlns" ->
                            (declarations ?: HashMap<String, String>().also { declarations = it })[""] = value
                        attrName.startsWith("xmlns:") ->
                            (declarations ?: HashMap<String, String>().also { declarations = it })[attrName.substring(6)] = value
                        else -> {
                            if (attrName in attrNames) fail("Duplicate attribute '$attrName' in <$name>")
                            attrNames += attrName
                            attrValues += value
                        }
                    }
                }
            }
        }
        rootSeen = true
        openNames += name
        namespaceScopes += declarations ?: emptyMap()
        setName(name)
    }

    private fun readEndTag() {
        val name = readName("element name")
        skipWhitespace()
        expect('>')
        if (openNames.isEmpty()) fail("Unexpected end tag </$name>")
        val open = openNames.last()
        if (open != name) fail("End tag </$name> does not match start tag <$open>")
        setName(name)
        popElement()
    }

    private fun popElement() {
        openNames.removeAt(openNames.size - 1)
        namespaceScopes.removeAt(namespaceScopes.size - 1)
    }

    private fun setName(name: String) {
        qualifiedName = name
        val colon = name.indexOf(':')
        localName = if (colon < 0) name else name.substring(colon + 1)
        namespaceUri = resolvePrefix(if (colon < 0) "" else name.substring(0, colon))
    }

    private fun resolvePrefix(prefix: String): String? {
        if (prefix == "xml") return "http://www.w3.org/XML/1998/namespace"
        for (i in namespaceScopes.indices.reversed()) {
            namespaceScopes[i][prefix]?.let { return it.ifEmpty { null } }
        }
        return null
    }

    // ---- text and attribute values ---------------------------------------------------------------

    private fun readText() {
        textBuilder.setLength(0)
        while (true) {
            val c = peek()
            if (c < 0 || c == '<'.code) return
            read()
            if (c == '&'.code) appendEntity(textBuilder) else textBuilder.append(c.toChar())
        }
    }

    private fun readAttributeValue(): String {
        val quote = read()
        if (quote != '"'.code && quote != '\''.code) fail("Attribute value must be quoted")
        val sb = StringBuilder()
        while (true) {
            val c = read()
            when {
                c < 0 -> fail("Unexpected end of document in attribute value")
                c == quote -> return sb.toString()
                c == '<'.code -> fail("'<' is not allowed in attribute values")
                c == '&'.code -> appendEntity(sb)
                // Attribute-value normalisation: literal whitespace characters become spaces.
                c == '\t'.code || c == '\n'.code || c == '\r'.code -> sb.append(' ')
                else -> sb.append(c.toChar())
            }
        }
    }

    /** Called after '&' was consumed. */
    private fun appendEntity(into: StringBuilder) {
        val name = StringBuilder()
        while (true) {
            val c = read()
            if (c < 0) fail("Unexpected end of document in entity reference")
            if (c == ';'.code) break
            name.append(c.toChar())
            if (name.length > MAX_ENTITY_LENGTH) fail("Malformed entity reference '&$name'")
        }
        val ref = name.toString()
        when (ref) {
            "lt" -> into.append('<')
            "gt" -> into.append('>')
            "amp" -> into.append('&')
            "quot" -> into.append('"')
            "apos" -> into.append('\'')
            else -> {
                if (!ref.startsWith("#")) fail("Undefined entity '&$ref;'")
                val code = if (ref.startsWith("#x") || ref.startsWith("#X")) {
                    ref.substring(2).toIntOrNull(16)
                } else {
                    ref.substring(1).toIntOrNull()
                }
                if (code == null || code < 0 || code > Character.MAX_CODE_POINT || code in 0xD800..0xDFFF) {
                    fail("Invalid character reference '&$ref;'")
                }
                into.appendCodePoint(code)
            }
        }
    }

    // ---- skipping --------------------------------------------------------------------------------

    /**
     * Skips a DOCTYPE declaration, including an internal subset in brackets. Its contents are never
     * interpreted, so no entity it declares becomes usable.
     */
    private fun skipDoctype() {
        var bracketDepth = 0
        var quote = -1
        while (true) {
            val c = read()
            when {
                c < 0 -> fail("Unexpected end of document in DOCTYPE")
                quote >= 0 -> if (c == quote) quote = -1
                c == '"'.code || c == '\''.code -> quote = c
                c == '['.code -> bracketDepth++
                c == ']'.code -> bracketDepth--
                c == '>'.code && bracketDepth <= 0 -> return
            }
        }
    }

    private fun skipUntil(terminator: String, what: String) {
        readUntil(terminator, what, null)
    }

    /** Reads up to and including [terminator], appending the content before it to [into]. */
    private fun readUntil(terminator: String, what: String, into: StringBuilder?) {
        val last = terminator.last().code
        val window = StringBuilder()
        while (true) {
            val c = read()
            if (c < 0) fail("Unexpected end of document in $what")
            window.append(c.toChar())
            if (c == last && window.endsWith(terminator)) {
                into?.append(window, 0, window.length - terminator.length)
                return
            }
            // Flush the part of the window that can no longer start a terminator.
            if (window.length > terminator.length * 4) {
                val keep = terminator.length
                into?.append(window, 0, window.length - keep)
                window.delete(0, window.length - keep)
            }
        }
    }

    // ---- lexical helpers -------------------------------------------------------------------------

    private fun readName(what: String): String {
        val sb = StringBuilder()
        val first = peek()
        if (first < 0 || !isNameStart(first.toChar())) fail("Expected $what")
        while (true) {
            val c = peek()
            if (c < 0 || !isNameChar(c.toChar())) break
            sb.append(read().toChar())
        }
        return sb.toString()
    }

    private fun skipWhitespace(): Boolean {
        var skipped = false
        while (true) {
            val c = peek()
            if (c < 0 || !c.toChar().isXmlWhitespace()) return skipped
            read()
            skipped = true
        }
    }

    private fun expect(ch: Char) {
        val c = read()
        if (c != ch.code) fail(if (c < 0) "Unexpected end of document, expected '$ch'" else "Expected '$ch' but found '${c.toChar()}'")
    }

    /** Consumes [literal] if the input continues with it; on mismatch nothing is consumed. */
    private fun tryConsume(literal: String): Boolean {
        if (!ensure(literal.length)) return false
        for (i in literal.indices) if (buffer[bufferPos + i] != literal[i]) return false
        repeat(literal.length) { read() }
        return true
    }

    private fun peek(): Int = if (ensure(1)) buffer[bufferPos].code else -1

    private fun read(): Int {
        if (!ensure(1)) return -1
        val c = buffer[bufferPos++]
        if (c == '\n') { line++; column = 1 } else column++
        return c.code
    }

    /** Makes at least [count] characters available in the buffer; `false` at end of input. */
    private fun ensure(count: Int): Boolean {
        if (bufferLength - bufferPos >= count) return true
        if (bufferPos > 0) {
            System.arraycopy(buffer, bufferPos, buffer, 0, bufferLength - bufferPos)
            bufferLength -= bufferPos
            bufferPos = 0
        }
        while (bufferLength < count) {
            val n = reader.read(buffer, bufferLength, buffer.size - bufferLength)
            if (n < 0) return false
            bufferLength += n
        }
        return true
    }

    private fun fail(message: String): Nothing = throw XmlSyntaxException(message, line, column)

    private companion object {
        const val BUFFER_SIZE = 8192
        const val MAX_ENTITY_LENGTH = 16

        fun Char.isXmlWhitespace() = this == ' ' || this == '\t' || this == '\n' || this == '\r'

        fun isNameStart(c: Char) = c.isLetter() || c == '_' || c == ':'

        fun isNameChar(c: Char) = c.isLetterOrDigit() || c == '_' || c == ':' || c == '-' || c == '.'
    }
}
