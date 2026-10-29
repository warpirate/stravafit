// SPDX-License-Identifier: Apache-2.0
package app.stravafit.lib.fit.protocol

/** A decoded FIT field value. Invalid (sentinel) values never appear; such fields are omitted. */
public sealed interface FitValue {
    /** An integer value; 64-bit unsigned values above `Long.MAX_VALUE` keep their raw bits. */
    public data class Integer(val value: Long) : FitValue

    /** A float32/float64 value. */
    public data class Real(val value: Double) : FitValue

    /** A NUL-terminated UTF-8 string. */
    public data class Text(val value: String) : FitValue

    /** An integer array; invalid elements are `null`. */
    public data class IntegerArray(val values: List<Long?>) : FitValue

    /** A float array; invalid elements are `null`. */
    public data class RealArray(val values: List<Double?>) : FitValue

    /** An opaque byte field (base type `byte`, or a field whose size does not fit its base type). */
    public class Bytes(public val value: ByteArray) : FitValue {
        override fun equals(other: Any?): Boolean = other is Bytes && value.contentEquals(other.value)
        override fun hashCode(): Int = value.contentHashCode()
        override fun toString(): String = "Bytes(${value.joinToString("") { "%02x".format(it) }})"
    }
}

/** Raw bytes of a developer field that the decoder skipped over without interpreting. */
public data class FitDeveloperField(val number: Int, val developerDataIndex: Int, val bytes: List<Byte>)

/**
 * One decoded data message.
 *
 * @property globalNumber Global message number (see [app.stravafit.lib.fit.FitMessageNumber]).
 * @property fields Valid fields by field number. For a compressed-timestamp message the decoder
 *   adds the reconstructed timestamp as field 253.
 */
public data class FitMessage(
    val globalNumber: Int,
    val fields: Map<Int, FitValue>,
    val developerFields: List<FitDeveloperField> = emptyList(),
) {
    /** Integer value of [field], or `null` if absent or not an integer. */
    public fun long(field: Int): Long? = (fields[field] as? FitValue.Integer)?.value

    /** Integer value of [field] converted to `Int`, or `null`. */
    public fun int(field: Int): Int? = long(field)?.toInt()

    /** Scaled value `raw / scale - offset` of an integer or real [field], or `null`. */
    public fun scaled(field: Int, scale: Double, offset: Double = 0.0): Double? =
        when (val v = fields[field]) {
            is FitValue.Integer -> v.value / scale - offset
            is FitValue.Real -> v.value / scale - offset
            else -> null
        }

    /** String value of [field], or `null`. */
    public fun text(field: Int): String? = (fields[field] as? FitValue.Text)?.value
}

/**
 * FIT file header.
 *
 * @property protocolVersion Protocol version byte (0x10 = 1.0, 0x20 = 2.0).
 * @property profileVersion Profile version × 100 (e.g. 2140 = 21.40).
 * @property dataSize Length of the record section in bytes.
 */
public data class FitHeader(
    val headerSize: Int,
    val protocolVersion: Int,
    val profileVersion: Int,
    val dataSize: Long,
)

/** A decoded FIT file. Chained files (several FIT files back to back) yield several [headers]. */
public data class FitFile(val headers: List<FitHeader>, val messages: List<FitMessage>)

/** Thrown when bytes are not a valid FIT file: bad header, truncation, CRC mismatch or bad records. */
public class FitFormatException(message: String, public val offset: Int = -1) :
    Exception(if (offset >= 0) "$message (at byte $offset)" else message)
