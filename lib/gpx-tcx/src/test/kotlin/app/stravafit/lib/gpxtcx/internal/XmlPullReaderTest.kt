// SPDX-License-Identifier: Apache-2.0
package app.stravafit.lib.gpxtcx.internal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class XmlPullReaderTest {
    private fun events(xml: String): List<String> {
        val reader = openXml(xml.byteInputStream())
        val out = mutableListOf<String>()
        while (true) {
            when (reader.next()) {
                XmlEvent.START_ELEMENT -> {
                    val attrs = (0 until reader.attributeCount)
                        .joinToString("") { " ${reader.getAttributeLocalName(it)}=${reader.getAttributeValue(it)}" }
                    out += "<${reader.localName}{${reader.namespaceUri}}$attrs>"
                }
                XmlEvent.END_ELEMENT -> out += "</${reader.localName}>"
                XmlEvent.CHARACTERS -> out += "'${reader.text}'"
                XmlEvent.END_DOCUMENT -> return out
            }
        }
    }

    @Test
    fun `resolves default and prefixed namespaces per scope`() {
        val xml = """<a xmlns="urn:a" xmlns:p="urn:p"><p:b x="1" p:y="2"/><c xmlns="urn:c"/><d/></a>"""
        assertEquals(
            listOf(
                "<a{urn:a}>", "<b{urn:p} x=1 y=2>", "</b>", "<c{urn:c}>", "</c>", "<d{urn:a}>", "</d>", "</a>",
            ),
            events(xml),
        )
    }

    @Test
    fun `decodes entities, character references and CDATA`() {
        assertEquals(
            listOf("<r{null} v=<&\">", "'a&bé€'", "'x<y]]'", "'!'", "</r>"),
            events("<r v='&lt;&amp;&quot;'>a&amp;b&#233;&#x20AC;<![CDATA[x<y]]]]><!-- c -->!</r>"),
        )
    }

    @Test
    fun `normalises whitespace in attribute values`() {
        assertEquals(listOf("<r{null} v=a b c>", "</r>"), events("<r v=\"a\tb\nc\"/>"))
    }

    @Test
    fun `skips declaration, processing instructions and doctype`() {
        assertEquals(
            listOf("<r{null}>", "</r>"),
            events("<?xml version=\"1.0\"?>\n<!DOCTYPE r SYSTEM \"r.dtd\">\n<?pi data?><r/>\n<!-- trailing -->"),
        )
    }

    @Test
    fun `rejects malformed input`() {
        listOf(
            "",
            "   ",
            "text<r/>",
            "<r>",
            "<r></s>",
            "<r a=1/>",
            "<r a='1' a='2'/>",
            "<r>&unknown;</r>",
            "<r>&#xZZ;</r>",
            "<r><!-- unterminated </r>",
            "<r a='1'b='2'/>",
        ).forEach { xml ->
            assertFailsWith<XmlSyntaxException>("should reject: $xml") { events(xml) }
        }
    }

    @Test
    fun `handles text larger than the internal buffer`() {
        val big = "x".repeat(50_000)
        val reader = openXml("<r>$big<![CDATA[$big]]></r>".byteInputStream())
        reader.next()
        assertEquals(XmlEvent.CHARACTERS, reader.next())
        assertEquals(big, reader.text)
        assertEquals(XmlEvent.CHARACTERS, reader.next())
        assertEquals(big, reader.text)
        assertEquals(XmlEvent.END_ELEMENT, reader.next())
    }

    @Test
    fun `reports no attributes on end events`() {
        val reader = openXml("<r a='1'/>".byteInputStream())
        reader.next()
        assertEquals("1", reader.attribute("a"))
        reader.next()
        assertNull(reader.attribute("a"))
    }
}
