// SPDX-License-Identifier: Apache-2.0
package app.stravafit.lib.fit.protocol

/**
 * One field of a definition message.
 *
 * @property number Field definition number from the FIT profile (e.g. 253 = timestamp).
 * @property baseType Encoding of each element.
 * @property size Total size in bytes; a multiple of the base type size makes the field an array,
 *   and for [FitBaseType.STRING] it is the fixed byte length including the terminating NUL.
 */
public data class FieldDefinition(
    val number: Int,
    val baseType: FitBaseType,
    val size: Int = baseType.size,
) {
    init {
        require(number in 0..255) { "Field number must fit in a byte: $number" }
        require(size in 1..255) { "Field size must be 1..255 bytes: $size" }
    }

    /** Number of elements, or 1 for strings and byte blobs. */
    internal val count: Int get() = if (baseType.kind.isScalarPerByte()) 1 else size / baseType.size
}

private fun FitBaseType.Kind.isScalarPerByte() = this == FitBaseType.Kind.STRING || this == FitBaseType.Kind.BYTES

/**
 * A developer data field in a definition message (protocol 2.0). Its meaning is described by
 * `field_description` messages; decoders that do not need it can skip [size] bytes.
 */
public data class DeveloperFieldDefinition(
    val number: Int,
    val size: Int,
    val developerDataIndex: Int,
) {
    init {
        require(number in 0..255 && developerDataIndex in 0..255) { "Developer field ids must fit in a byte" }
        require(size in 1..255) { "Developer field size must be 1..255 bytes: $size" }
    }
}

/**
 * The layout of data messages for one local message type.
 *
 * @property globalNumber Global message number from the FIT profile (e.g. 20 = record).
 * @property bigEndian Byte order of multi-byte fields in the data messages.
 */
public data class MessageDefinition(
    val globalNumber: Int,
    val fields: List<FieldDefinition>,
    val developerFields: List<DeveloperFieldDefinition> = emptyList(),
    val bigEndian: Boolean = false,
) {
    init {
        require(globalNumber in 0..0xFFFF) { "Global message number must be a uint16: $globalNumber" }
        require(fields.size <= 255 && developerFields.size <= 255) { "At most 255 fields per message" }
    }

    /** Size in bytes of one data message body. */
    val dataSize: Int get() = fields.sumOf { it.size } + developerFields.sumOf { it.size }
}
