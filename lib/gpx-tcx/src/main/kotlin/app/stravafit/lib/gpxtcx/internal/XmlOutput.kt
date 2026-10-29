// SPDX-License-Identifier: Apache-2.0
package app.stravafit.lib.gpxtcx.internal

import java.io.Writer

/**
 * Minimal streaming XML writer with deterministic two-space indentation and `\n` line endings, so
 * output is byte-stable across platforms (golden-file friendly). Callers pass qualified names.
 */
internal class XmlOutput(private val out: Writer) {
    private var depth = 0

    fun declaration() {
        out.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
    }

    /** Writes an element containing child elements produced by [body]. */
    fun element(name: String, vararg attributes: Pair<String, String?>, body: () -> Unit) {
        indent()
        out.write("<$name")
        writeAttributes(attributes)
        out.write(">\n")
        depth++
        body()
        depth--
        indent()
        out.write("</$name>\n")
    }

    /** Writes `<name>text</name>`; does nothing when [text] is `null`. */
    fun leaf(name: String, text: String?, vararg attributes: Pair<String, String?>) {
        if (text == null) return
        indent()
        out.write("<$name")
        writeAttributes(attributes)
        out.write(">")
        out.write(escape(text))
        out.write("</$name>\n")
    }

    private fun writeAttributes(attributes: Array<out Pair<String, String?>>) {
        for ((key, value) in attributes) {
            if (value != null) out.write(" $key=\"${escape(value)}\"")
        }
    }

    private fun indent() {
        repeat(depth) { out.write("  ") }
    }

    private fun escape(text: String): String {
        if (text.none { it in "<>&\"'" || it < ' ' }) return text
        val sb = StringBuilder(text.length + 16)
        for (c in text) {
            when {
                c == '<' -> sb.append("&lt;")
                c == '>' -> sb.append("&gt;")
                c == '&' -> sb.append("&amp;")
                c == '"' -> sb.append("&quot;")
                c == '\'' -> sb.append("&apos;")
                c == '\n' || c == '\t' -> sb.append("&#").append(c.code).append(';')
                c == '\r' -> sb.append("&#13;")
                c < ' ' -> Unit // Control characters are not allowed in XML 1.0; drop them.
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }
}
