// SPDX-License-Identifier: Apache-2.0
package app.stravafit.lib.gpxtcx.internal

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/** Parses a finite decimal number, tolerating surrounding whitespace; anything else yields `null`. */
internal fun String?.toFiniteDoubleOrNull(): Double? =
    this?.trim()?.toDoubleOrNull()?.takeIf { it.isFinite() }

/** Parses an integer that some devices write with a fractional part (e.g. `"131.0"`). */
internal fun String?.toRoundedIntOrNull(): Int? =
    toFiniteDoubleOrNull()?.takeIf { it in Int.MIN_VALUE.toDouble()..Int.MAX_VALUE.toDouble() }?.roundToInt()

/**
 * Parses an XML Schema `dateTime`. Accepts `Z` or numeric offsets, fractional seconds of any
 * precision, and (leniently) a missing zone, which is taken as UTC. Unparseable input yields `null`.
 */
internal fun String?.toInstantOrNull(): Instant? {
    val text = this?.trim()?.replace(' ', 'T') ?: return null
    return try {
        OffsetDateTime.parse(text).toInstant()
    } catch (_: DateTimeParseException) {
        try {
            LocalDateTime.parse(text).toInstant(ZoneOffset.UTC)
        } catch (_: DateTimeParseException) {
            null
        }
    }
}

/** Formats [value] with at most [maxDecimals] fraction digits (half-up), no exponent, no trailing zeros. */
internal fun formatDecimal(value: Double, maxDecimals: Int): String {
    require(value.isFinite()) { "Cannot write non-finite number $value" }
    val text = BigDecimal.valueOf(value).setScale(maxDecimals, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
    return if (text == "-0") "0" else text
}

/** ISO-8601 UTC timestamp, truncated to milliseconds; the fraction is omitted when it is zero. */
internal fun formatTime(time: Instant): String = time.truncatedTo(ChronoUnit.MILLIS).toString()
