// SPDX-License-Identifier: Apache-2.0
package app.stravafit.lib.fit.protocol

import java.io.InputStream

/**
 * Low-level FIT decoder. Parses headers, definition messages (either byte order), normal and
 * compressed-timestamp data messages, and skips developer fields (keeping their raw bytes).
 * Chained FIT files are decoded one after another into a single message list.
 *
 * Every data message is decoded generically, so messages unknown to this library are still
 * returned. Fields holding their type's invalid sentinel are omitted.
 *
 * @param validateCrc verify the header CRC (when present and non-zero) and the file CRC.
 */
public class FitFileReader(private val validateCrc: Boolean = true) {

    /** Decodes all of [input]. The stream is not closed. */
    public fun read(input: InputStream): FitFile = read(input.readBytes())

    /**
     * Decodes [bytes].
     *
     * @throws FitFormatException on a bad header, truncation, CRC mismatch or an undecodable record.
     */
    public fun read(bytes: ByteArray): FitFile {
        if (bytes.isEmpty()) throw FitFormatException("Empty input")
        val headers = mutableListOf<FitHeader>()
        val messages = mutableListOf<FitMessage>()
        var offset = 0
        while (offset < bytes.size) {
            val header = readHeader(bytes, offset)
            headers += header
            val dataStart = offset + header.headerSize
            val dataEnd = dataStart.toLong() + header.dataSize
            if (dataEnd + 2 > bytes.size) {
                throw FitFormatException("Truncated file: header declares ${header.dataSize} data bytes", offset)
            }
            if (validateCrc) {
                val expected = (bytes[dataEnd.toInt()].toInt() and 0xFF) or ((bytes[dataEnd.toInt() + 1].toInt() and 0xFF) shl 8)
                val actual = FitCrc.compute(bytes, offset, dataEnd.toInt())
                if (expected != actual) {
                    throw FitFormatException("File CRC mismatch: expected %04x, computed %04x".format(expected, actual), dataEnd.toInt())
                }
            }
            RecordParser(bytes, dataStart, dataEnd.toInt()).parseInto(messages)
            offset = dataEnd.toInt() + 2
        }
        return FitFile(headers, messages)
    }

    private fun readHeader(bytes: ByteArray, offset: Int): FitHeader {
        if (bytes.size - offset < 12) throw FitFormatException("Too short for a FIT header", offset)
        val headerSize = bytes[offset].toInt() and 0xFF
        if (headerSize < 12 || offset + headerSize > bytes.size) {
            throw FitFormatException("Invalid header size $headerSize", offset)
        }
        val signature = String(bytes, offset + 8, 4, Charsets.US_ASCII)
        if (signature != ".FIT") throw FitFormatException("Missing .FIT signature", offset + 8)
        if (validateCrc && headerSize >= 14) {
            val stored = (bytes[offset + 12].toInt() and 0xFF) or ((bytes[offset + 13].toInt() and 0xFF) shl 8)
            if (stored != 0 && stored != FitCrc.compute(bytes, offset, offset + 12)) {
                throw FitFormatException("Header CRC mismatch", offset + 12)
            }
        }
        return FitHeader(
            headerSize = headerSize,
            protocolVersion = bytes[offset + 1].toInt() and 0xFF,
            profileVersion = readLe(bytes, offset + 2, 2).toInt(),
            dataSize = readLe(bytes, offset + 4, 4),
        )
    }

    /** Parses the record section `bytes[start until end]`; state is per file. */
    private class RecordParser(private val bytes: ByteArray, start: Int, private val end: Int) {
        private var pos = start
        private val definitions = arrayOfNulls<MessageDefinition>(16)
        private var lastTimestamp: Long? = null

        fun parseInto(messages: MutableList<FitMessage>) {
            while (pos < end) {
                val recordStart = pos
                val header = u8()
                when {
                    header and FitFileWriter.COMPRESSED_FLAG != 0 -> {
                        val local = (header shr 5) and 0x03
                        val previous = lastTimestamp
                            ?: throw FitFormatException("Compressed timestamp before any timestamp", recordStart)
                        val timestamp = resolveCompressedTimestamp(previous, header and 0x1F)
                        val message = readData(local, recordStart)
                        lastTimestamp = timestamp
                        messages += message.copy(fields = message.fields + (TIMESTAMP_FIELD to FitValue.Integer(timestamp)))
                    }
                    header and FitFileWriter.DEFINITION_FLAG != 0 ->
                        definitions[header and 0x0F] = readDefinition(header and FitFileWriter.DEVELOPER_FLAG != 0, recordStart)
                    else -> messages += readData(header and 0x0F, recordStart)
                }
            }
        }

        private fun readDefinition(hasDeveloperFields: Boolean, recordStart: Int): MessageDefinition {
            u8() // reserved
            val bigEndian = when (u8()) {
                0 -> false
                1 -> true
                else -> throw FitFormatException("Invalid architecture byte", pos - 1)
            }
            val global = uint(2, bigEndian).toInt()
            val fields = List(u8()) {
                val number = u8()
                val size = u8()
                val typeId = u8()
                if (size == 0) throw FitFormatException("Field $number has size 0", pos - 2)
                // Unknown base types and sizes that are not a multiple of the type are read as bytes.
                val type = FitBaseType.fromId(typeId)?.takeIf { size % it.size == 0 } ?: FitBaseType.BYTE
                FieldDefinition(number, type, size)
            }
            val developerFields = if (hasDeveloperFields) {
                List(u8()) {
                    val number = u8()
                    val size = u8()
                    val index = u8()
                    if (size == 0) throw FitFormatException("Developer field $number has size 0", pos - 2)
                    DeveloperFieldDefinition(number, size, index)
                }
            } else {
                emptyList()
            }
            if (pos > end) throw FitFormatException("Definition runs past the data section", recordStart)
            return MessageDefinition(global, fields, developerFields, bigEndian)
        }

        private fun readData(localType: Int, recordStart: Int): FitMessage {
            val definition = definitions[localType]
                ?: throw FitFormatException("Data message for undefined local type $localType", recordStart)
            if (pos + definition.dataSize > end) throw FitFormatException("Data message runs past the data section", recordStart)
            val fields = LinkedHashMap<Int, FitValue>()
            for (field in definition.fields) {
                decodeField(field, definition.bigEndian)?.let { fields[field.number] = it }
            }
            val developerFields = definition.developerFields.map { dev ->
                FitDeveloperField(dev.number, dev.developerDataIndex, bytes.copyOfRange(pos, pos + dev.size).toList())
                    .also { pos += dev.size }
            }
            (fields[TIMESTAMP_FIELD] as? FitValue.Integer)?.let { lastTimestamp = it.value }
            return FitMessage(definition.globalNumber, fields, developerFields)
        }

        private fun decodeField(field: FieldDefinition, bigEndian: Boolean): FitValue? {
            val type = field.baseType
            return when (type.kind) {
                FitBaseType.Kind.STRING -> {
                    val raw = bytes.copyOfRange(pos, pos + field.size).also { pos += field.size }
                    val length = raw.indexOf(0).let { if (it < 0) raw.size else it }
                    if (length == 0) null else FitValue.Text(String(raw, 0, length, Charsets.UTF_8))
                }
                FitBaseType.Kind.BYTES -> {
                    val raw = bytes.copyOfRange(pos, pos + field.size).also { pos += field.size }
                    if (raw.all { it == 0xFF.toByte() }) null else FitValue.Bytes(raw)
                }
                FitBaseType.Kind.FLOAT -> {
                    val values = List(field.count) { decodeFloat(type, uint(type.size, bigEndian)) }
                    when {
                        values.all { it == null } -> null
                        values.size == 1 -> FitValue.Real(values[0]!!)
                        else -> FitValue.RealArray(values)
                    }
                }
                else -> {
                    val values = List(field.count) {
                        val raw = uint(type.size, bigEndian)
                        if (raw == type.invalidRaw) null else type.signExtend(raw)
                    }
                    when {
                        values.all { it == null } -> null
                        values.size == 1 -> FitValue.Integer(values[0]!!)
                        else -> FitValue.IntegerArray(values)
                    }
                }
            }
        }

        private fun decodeFloat(type: FitBaseType, raw: Long): Double? {
            if (raw == type.invalidRaw) return null
            val value = if (type == FitBaseType.FLOAT32) Float.fromBits(raw.toInt()).toDouble() else Double.fromBits(raw)
            return value.takeIf { it.isFinite() }
        }

        private fun u8(): Int {
            if (pos >= end) throw FitFormatException("Unexpected end of data section", pos)
            return bytes[pos++].toInt() and 0xFF
        }

        private fun uint(size: Int, bigEndian: Boolean): Long {
            if (pos + size > end) throw FitFormatException("Unexpected end of data section", pos)
            var value = 0L
            for (i in 0 until size) {
                val shift = 8 * (if (bigEndian) size - 1 - i else i)
                value = value or ((bytes[pos + i].toLong() and 0xFF) shl shift)
            }
            pos += size
            return value
        }
    }

    internal companion object {
        const val TIMESTAMP_FIELD = 253

        /**
         * Rebuilds a full timestamp from a 5-bit [offset] and the [previous] timestamp, rolling
         * over when the offset is smaller than the previous value's low 5 bits.
         */
        fun resolveCompressedTimestamp(previous: Long, offset: Int): Long {
            val base = previous and 0x1FL.inv()
            return if (offset >= (previous and 0x1F)) base + offset else base + offset + 0x20
        }

        fun readLe(bytes: ByteArray, at: Int, size: Int): Long {
            var value = 0L
            for (i in 0 until size) value = value or ((bytes[at + i].toLong() and 0xFF) shl (8 * i))
            return value
        }
    }
}
