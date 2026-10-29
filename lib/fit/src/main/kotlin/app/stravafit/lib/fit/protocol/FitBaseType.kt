// SPDX-License-Identifier: Apache-2.0
package app.stravafit.lib.fit.protocol

/**
 * FIT base types as defined by the FIT protocol.
 *
 * @property id Base type byte as written in definition messages (bit 7 = endian-capable flag).
 * @property size Size of one element in bytes.
 * @property invalidRaw The raw bit pattern (zero-extended to 64 bits) that marks an invalid value.
 */
public enum class FitBaseType(
    public val id: Int,
    public val size: Int,
    internal val kind: Kind,
    internal val invalidRaw: Long,
) {
    ENUM(0x00, 1, Kind.UNSIGNED, 0xFF),
    SINT8(0x01, 1, Kind.SIGNED, 0x7F),
    UINT8(0x02, 1, Kind.UNSIGNED, 0xFF),
    SINT16(0x83, 2, Kind.SIGNED, 0x7FFF),
    UINT16(0x84, 2, Kind.UNSIGNED, 0xFFFF),
    SINT32(0x85, 4, Kind.SIGNED, 0x7FFF_FFFF),
    UINT32(0x86, 4, Kind.UNSIGNED, 0xFFFF_FFFFL),
    STRING(0x07, 1, Kind.STRING, 0x00),
    FLOAT32(0x88, 4, Kind.FLOAT, 0xFFFF_FFFFL),
    FLOAT64(0x89, 8, Kind.FLOAT, -1L),
    UINT8Z(0x0A, 1, Kind.UNSIGNED, 0x00),
    UINT16Z(0x8B, 2, Kind.UNSIGNED, 0x00),
    UINT32Z(0x8C, 4, Kind.UNSIGNED, 0x00),
    BYTE(0x0D, 1, Kind.BYTES, 0xFF),
    SINT64(0x8E, 8, Kind.SIGNED, Long.MAX_VALUE),
    UINT64(0x8F, 8, Kind.UNSIGNED, -1L),
    UINT64Z(0x90, 8, Kind.UNSIGNED, 0x00),
    ;

    internal enum class Kind { SIGNED, UNSIGNED, FLOAT, STRING, BYTES }

    /** `true` for types introduced with protocol 2.0 (64-bit integers). */
    internal val requiresProtocol2: Boolean get() = this == SINT64 || this == UINT64 || this == UINT64Z

    /** Interprets zero-extended [raw] bits as this type's integer value (sign-extending signed types). */
    internal fun signExtend(raw: Long): Long =
        if (kind == Kind.SIGNED && size < 8) {
            val shift = 64 - 8 * size
            (raw shl shift) shr shift
        } else {
            raw
        }

    /** Inclusive range of valid values for integer types narrower than 64 bits. */
    internal val validRange: LongRange
        get() {
            val bits = 8 * size
            return when {
                kind == Kind.SIGNED -> (-(1L shl (bits - 1)))..((1L shl (bits - 1)) - 2)
                invalidRaw == 0L -> 1L..((1L shl bits) - 1)
                else -> 0L..((1L shl bits) - 2)
            }
        }

    public companion object {
        /** Looks up a base type by its definition byte; the low 5 bits identify the type. */
        public fun fromId(id: Int): FitBaseType? = entries.firstOrNull { (it.id and 0x1F) == (id and 0x1F) }
    }
}
