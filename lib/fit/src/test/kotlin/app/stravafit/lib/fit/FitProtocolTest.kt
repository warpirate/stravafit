// SPDX-License-Identifier: Apache-2.0
package app.stravafit.lib.fit

import app.stravafit.lib.fit.protocol.DeveloperFieldDefinition
import app.stravafit.lib.fit.protocol.FieldDefinition
import app.stravafit.lib.fit.protocol.FitBaseType
import app.stravafit.lib.fit.protocol.FitCrc
import app.stravafit.lib.fit.protocol.FitDeveloperField
import app.stravafit.lib.fit.protocol.FitFileReader
import app.stravafit.lib.fit.protocol.FitFileWriter
import app.stravafit.lib.fit.protocol.FitFormatException
import app.stravafit.lib.fit.protocol.FitValue
import app.stravafit.lib.fit.protocol.MessageDefinition
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FitProtocolTest {
    private val reader = FitFileReader()

    @Test
    fun `CRC is CRC-16 ARC`() {
        assertEquals(0xBB3D, FitCrc.compute("123456789".toByteArray()))
        assertEquals(0, FitCrc.compute(ByteArray(0)))
    }

    @Test
    fun `empty file has a valid 14-byte header and CRC`() {
        val bytes = FitFileWriter().toByteArray()
        assertEquals(16, bytes.size)
        assertContentEquals(byteArrayOf(14, 0x10, 0x5C, 0x08, 0, 0, 0, 0, '.'.code.toByte(), 'F'.code.toByte(), 'I'.code.toByte(), 'T'.code.toByte()), bytes.copyOf(12))
        assertEquals(FitCrc.compute(bytes, 0, 12), (bytes[12].toInt() and 0xFF) or ((bytes[13].toInt() and 0xFF) shl 8))
        assertEquals(0, FitCrc.compute(bytes), "CRC over a file including its trailing CRC is zero")
        val file = reader.read(bytes)
        assertEquals(2140, file.headers.single().profileVersion)
        assertTrue(file.messages.isEmpty())
    }

    @Test
    fun `big-endian definitions, arrays, strings, floats and developer fields round trip`() {
        val writer = FitFileWriter(protocolVersion = FitFileWriter.PROTOCOL_2_0)
        val definition = MessageDefinition(
            globalNumber = 0xFF01,
            fields = listOf(
                FieldDefinition(253, FitBaseType.UINT32),
                FieldDefinition(1, FitBaseType.SINT16),
                FieldDefinition(2, FitBaseType.UINT16, size = 6),
                FieldDefinition(3, FitBaseType.STRING, size = 8),
                FieldDefinition(4, FitBaseType.FLOAT32),
                FieldDefinition(5, FitBaseType.SINT64),
                FieldDefinition(6, FitBaseType.BYTE, size = 3),
                FieldDefinition(7, FitBaseType.UINT8Z),
            ),
            developerFields = listOf(DeveloperFieldDefinition(number = 0, size = 2, developerDataIndex = 0)),
            bigEndian = true,
        )
        writer.writeDefinition(0, definition)
        writer.writeData(
            0,
            listOf(1_000_000L, -1234, listOf(1, null, 3), "héllo wörld", 1.5, -5_000_000_000L, byteArrayOf(9, 8, 7), null),
            developerValues = listOf(byteArrayOf(0x12, 0x34)),
        )
        val message = reader.read(writer.toByteArray()).messages.single()
        assertEquals(0xFF01, message.globalNumber)
        assertEquals(FitValue.Integer(1_000_000), message.fields[253])
        assertEquals(FitValue.Integer(-1234), message.fields[1])
        assertEquals(FitValue.IntegerArray(listOf(1, null, 3)), message.fields[2])
        assertEquals(FitValue.Text("héllo "), message.fields[3], "truncated on a character boundary")
        assertEquals(FitValue.Real(1.5), message.fields[4])
        assertEquals(FitValue.Integer(-5_000_000_000L), message.fields[5])
        assertEquals(FitValue.Bytes(byteArrayOf(9, 8, 7)), message.fields[6])
        assertNull(message.fields[7], "invalid z-type value is omitted")
        assertEquals(listOf(FitDeveloperField(0, 0, listOf(0x12, 0x34))), message.developerFields)
    }

    @Test
    fun `compressed timestamps roll over the 5-bit offset`() {
        assertEquals(0x41L, FitFileReader.resolveCompressedTimestamp(0x3EL, 0x01))
        assertEquals(0x3FL, FitFileReader.resolveCompressedTimestamp(0x3EL, 0x1F))
        assertEquals(0x3EL, FitFileReader.resolveCompressedTimestamp(0x3EL, 0x1E))
    }

    @Test
    fun `writer validates its input`() {
        val writer = FitFileWriter()
        val dev = MessageDefinition(1, emptyList(), listOf(DeveloperFieldDefinition(0, 1, 0)))
        assertFailsWith<IllegalArgumentException> { writer.writeDefinition(0, dev) }
        assertFailsWith<IllegalArgumentException> { writer.writeData(3, emptyList()) }
        writer.writeDefinition(0, MessageDefinition(20, listOf(FieldDefinition(3, FitBaseType.UINT8))))
        assertFailsWith<IllegalArgumentException> { writer.writeData(0, listOf(255)) } // 0xFF is the invalid value
        assertFailsWith<IllegalArgumentException> { writer.writeData(0, listOf(1, 2)) }
        assertFailsWith<IllegalArgumentException> { writer.writeCompressedTimestampData(4, 0, emptyList()) }
    }

    @Test
    fun `reader rejects corrupt files`() {
        val good = FitActivityEncoder().encode(SampleActivity.activity)
        val flipped = good.copyOf().also { it[40] = (it[40].toInt() xor 0x01).toByte() }
        assertFailsWith<FitFormatException> { reader.read(flipped) }
        assertFailsWith<FitFormatException> { reader.read(good.copyOf(good.size - 10)) }
        assertFailsWith<FitFormatException> { reader.read(good.copyOf().also { it[8] = 'X'.code.toByte() }) }
        assertFailsWith<FitFormatException> { reader.read(ByteArray(0)) }
        // Without CRC validation a payload bit-flip goes unnoticed, but structure is still checked.
        FitFileReader(validateCrc = false).read(flipped)

        // A data message for a local type that was never defined.
        val noDefinition = FitFileWriter().also {
            it.writeDefinition(0, MessageDefinition(20, listOf(FieldDefinition(3, FitBaseType.UINT8))))
            it.writeData(0, listOf(1))
        }.toByteArray().also { it[14 + 6 + 3] = 0x05 } // retarget the data header to local type 5
        val error = assertFailsWith<FitFormatException> { FitFileReader(validateCrc = false).read(noDefinition) }
        assertTrue("undefined local type 5" in error.message!!, error.message)
    }

    @Test
    fun `chained files are decoded in sequence`() {
        val one = FitActivityEncoder().encode(FitActivity(FitFileId(product = 1), emptyList()))
        val two = FitActivityEncoder().encode(FitActivity(FitFileId(product = 2), emptyList()))
        val file = reader.read(one + two)
        assertEquals(2, file.headers.size)
        assertEquals(listOf(1, 2), file.messages.map { it.int(FileIdField.PRODUCT) })
    }

    @Test
    fun `12-byte legacy header without CRC is accepted`() {
        val modern = FitActivityEncoder().encode(FitActivity(FitFileId(product = 7), emptyList()))
        val data = modern.copyOfRange(14, modern.size - 2)
        val header = modern.copyOf(12).also { it[0] = 12 }
        val body = header + data
        val crc = FitCrc.compute(body)
        val legacy = body + byteArrayOf((crc and 0xFF).toByte(), (crc shr 8).toByte())
        assertEquals(7, reader.read(legacy).messages.single().int(FileIdField.PRODUCT))
    }
}
