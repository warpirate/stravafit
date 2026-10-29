// SPDX-License-Identifier: Apache-2.0
package app.stravafit.lib.fit.protocol

import java.io.ByteArrayOutputStream

/**
 * Low-level FIT encoder: writes definition and data messages and produces a complete file with
 * a 14-byte header (including header CRC) and the trailing file CRC.
 *
 * Data values are given per field, in definition order:
 * - integer types: any [Number] (range-checked), or a `List<Number?>` for array fields;
 * - float types: any [Number];
 * - [FitBaseType.STRING]: a [String], NUL-padded/truncated to the field size (UTF-8 safe);
 * - [FitBaseType.BYTE]: a [ByteArray] of exactly the field size;
 * - `null` writes the type's invalid value.
 *
 * @param protocolVersion 0x10 (1.0) or 0x20 (2.0); 2.0 is required for developer fields and
 *   64-bit types.
 * @param profileVersion Profile version × 100 written to the header.
 */
public class FitFileWriter(
    private val protocolVersion: Int = PROTOCOL_1_0,
    private val profileVersion: Int = DEFAULT_PROFILE_VERSION,
) {
    private val body = ByteArrayOutputStream()
    private val definitions = arrayOfNulls<MessageDefinition>(16)

    init {
        require(protocolVersion == PROTOCOL_1_0 || protocolVersion == PROTOCOL_2_0) { "Unsupported protocol version" }
        require(profileVersion in 0..0xFFFF) { "Profile version must be a uint16" }
    }

    /** Writes a definition message binding [definition] to [localType] (0..15). */
    public fun writeDefinition(localType: Int, definition: MessageDefinition) {
        require(localType in 0..15) { "Local message type must be 0..15: $localType" }
        if (protocolVersion < PROTOCOL_2_0) {
            require(definition.developerFields.isEmpty()) { "Developer fields need protocol 2.0" }
            require(definition.fields.none { it.baseType.requiresProtocol2 }) { "64-bit types need protocol 2.0" }
        }
        val devFlag = if (definition.developerFields.isNotEmpty()) DEVELOPER_FLAG else 0
        body.write(DEFINITION_FLAG or devFlag or localType)
        body.write(0) // reserved
        body.write(if (definition.bigEndian) 1 else 0)
        writeUInt(definition.globalNumber.toLong(), 2, definition.bigEndian)
        body.write(definition.fields.size)
        for (field in definition.fields) {
            body.write(field.number)
            body.write(field.size)
            body.write(field.baseType.id)
        }
        if (devFlag != 0) {
            body.write(definition.developerFields.size)
            for (dev in definition.developerFields) {
                body.write(dev.number)
                body.write(dev.size)
                body.write(dev.developerDataIndex)
            }
        }
        definitions[localType] = definition
    }

    /** Writes a data message for [localType] with a normal header. */
    public fun writeData(localType: Int, values: List<Any?>, developerValues: List<ByteArray> = emptyList()) {
        val definition = definitionFor(localType)
        body.write(localType)
        writeBody(definition, values, developerValues)
    }

    /**
     * Writes a data message with a compressed-timestamp header. [timeOffset] is the low 5 bits of
     * the message's timestamp; decoders reconstruct the full value from the previous timestamp, so
     * it must be less than 32 s after it. Only local types 0..3 can be used.
     */
    public fun writeCompressedTimestampData(
        localType: Int,
        timeOffset: Int,
        values: List<Any?>,
        developerValues: List<ByteArray> = emptyList(),
    ) {
        require(localType in 0..3) { "Compressed-timestamp headers support local types 0..3 only" }
        val definition = definitionFor(localType)
        body.write(COMPRESSED_FLAG or (localType shl 5) or (timeOffset and 0x1F))
        writeBody(definition, values, developerValues)
    }

    /** Returns the complete FIT file: header, all messages written so far, and the file CRC. */
    public fun toByteArray(): ByteArray {
        val data = body.toByteArray()
        val out = ByteArrayOutputStream(HEADER_SIZE + data.size + 2)
        val header = ByteArray(HEADER_SIZE)
        header[0] = HEADER_SIZE.toByte()
        header[1] = protocolVersion.toByte()
        putLe(header, 2, profileVersion.toLong(), 2)
        putLe(header, 4, data.size.toLong(), 4)
        ".FIT".forEachIndexed { i, c -> header[8 + i] = c.code.toByte() }
        putLe(header, 12, FitCrc.compute(header, 0, 12).toLong(), 2)
        out.write(header)
        out.write(data)
        var crc = FitCrc.compute(header)
        for (b in data) crc = FitCrc.update(crc, b.toInt() and 0xFF)
        out.write(crc and 0xFF)
        out.write((crc shr 8) and 0xFF)
        return out.toByteArray()
    }

    private fun definitionFor(localType: Int): MessageDefinition =
        requireNotNull(definitions.getOrNull(localType)) { "No definition written for local type $localType" }

    private fun writeBody(definition: MessageDefinition, values: List<Any?>, developerValues: List<ByteArray>) {
        require(values.size == definition.fields.size) {
            "Expected ${definition.fields.size} values for message ${definition.globalNumber}, got ${values.size}"
        }
        require(developerValues.size == definition.developerFields.size) { "Developer value count mismatch" }
        definition.fields.forEachIndexed { i, field -> writeField(field, values[i], definition.bigEndian) }
        definition.developerFields.forEachIndexed { i, dev ->
            val bytes = developerValues[i]
            require(bytes.size == dev.size) { "Developer field ${dev.number} needs ${dev.size} bytes" }
            body.write(bytes)
        }
    }

    private fun writeField(field: FieldDefinition, value: Any?, bigEndian: Boolean) {
        val type = field.baseType
        when (type.kind) {
            FitBaseType.Kind.STRING -> writeString(value as String?, field)
            FitBaseType.Kind.BYTES -> {
                val bytes = value as ByteArray? ?: ByteArray(field.size) { 0xFF.toByte() }
                require(bytes.size == field.size) { "Field ${field.number} needs ${field.size} bytes" }
                body.write(bytes)
            }
            else -> {
                val elements: List<Any?> = when {
                    field.count == 1 -> listOf(value)
                    value == null -> List(field.count) { null }
                    else -> (value as List<*>).also {
                        require(it.size == field.count) { "Field ${field.number} needs ${field.count} elements" }
                    }
                }
                for (element in elements) writeUInt(rawBits(field, element as Number?), type.size, bigEndian)
            }
        }
    }

    private fun rawBits(field: FieldDefinition, value: Number?): Long {
        val type = field.baseType
        if (value == null) return type.invalidRaw
        return when (type) {
            FitBaseType.FLOAT32 -> value.toFloat().toRawBits().toLong() and 0xFFFF_FFFFL
            FitBaseType.FLOAT64 -> value.toDouble().toRawBits()
            else -> {
                val v = value.toLong()
                if (type.size < 8) {
                    require(v in type.validRange) { "Value $v out of range for field ${field.number} ($type)" }
                }
                v
            }
        }
    }

    private fun writeString(value: String?, field: FieldDefinition) {
        val out = ByteArray(field.size)
        if (value != null) {
            // Truncate on a character boundary so the UTF-8 stays valid, leaving room for the NUL.
            var bytes = value.toByteArray(Charsets.UTF_8)
            var chars = value.length
            while (bytes.size > field.size - 1 && chars > 0) {
                chars--
                bytes = value.substring(0, chars).toByteArray(Charsets.UTF_8)
            }
            bytes.copyInto(out)
        }
        body.write(out)
    }

    private fun writeUInt(value: Long, size: Int, bigEndian: Boolean) {
        for (i in 0 until size) {
            val shift = 8 * (if (bigEndian) size - 1 - i else i)
            body.write(((value ushr shift) and 0xFF).toInt())
        }
    }

    private fun putLe(target: ByteArray, at: Int, value: Long, size: Int) {
        for (i in 0 until size) target[at + i] = ((value ushr (8 * i)) and 0xFF).toByte()
    }

    public companion object {
        /** Protocol version 1.0 byte. */
        public const val PROTOCOL_1_0: Int = 0x10

        /** Protocol version 2.0 byte. */
        public const val PROTOCOL_2_0: Int = 0x20

        /** Profile version written by default (21.40). */
        public const val DEFAULT_PROFILE_VERSION: Int = 2140

        internal const val HEADER_SIZE = 14
        internal const val COMPRESSED_FLAG = 0x80
        internal const val DEFINITION_FLAG = 0x40
        internal const val DEVELOPER_FLAG = 0x20
    }
}
