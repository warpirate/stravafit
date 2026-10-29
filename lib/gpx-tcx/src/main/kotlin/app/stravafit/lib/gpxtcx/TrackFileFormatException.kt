// SPDX-License-Identifier: Apache-2.0
package app.stravafit.lib.gpxtcx

/**
 * Thrown when a GPX or TCX file cannot be read: it is not well-formed XML, is not the expected
 * format, or lacks a value the format requires.
 *
 * @property line 1-based line of the problem, or `-1` when unknown.
 * @property column 1-based column of the problem, or `-1` when unknown.
 */
public class TrackFileFormatException(
    message: String,
    public val line: Int = -1,
    public val column: Int = -1,
    cause: Throwable? = null,
) : Exception(if (line > 0) "$message (line $line, column $column)" else message, cause)
