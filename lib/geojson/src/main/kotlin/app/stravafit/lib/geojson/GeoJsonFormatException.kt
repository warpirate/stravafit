// SPDX-License-Identifier: Apache-2.0
package app.stravafit.lib.geojson

/** Thrown when input is not valid JSON or not a valid RFC 7946 GeoJSON object. */
public class GeoJsonFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)
