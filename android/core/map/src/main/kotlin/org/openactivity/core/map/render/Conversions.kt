// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.map.render

import android.location.Location
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.maps.Style
import org.openactivity.core.map.MapLocation
import org.openactivity.core.map.camera.MapCamera
import org.openactivity.core.map.style.MapStyleDefinition
import org.openactivity.core.map.style.StyleSource
import org.openactivity.model.LatLng
import org.maplibre.android.geometry.LatLng as MlLatLng

/** Our model position as a MapLibre one. */
internal fun LatLng.toMapLibre(): MlLatLng = MlLatLng(latitude, longitude)

/** A MapLibre position as ours, wrapping the longitude MapLibre may report outside ±180. */
internal fun MlLatLng.toModel(): LatLng = LatLng(latitude.coerceIn(-90.0, 90.0), wrap().longitude)

internal fun MapCamera.toCameraPosition(): CameraPosition = CameraPosition.Builder()
    .target(target.toMapLibre())
    .zoom(zoom)
    .bearing(bearing)
    .tilt(tilt)
    .build()

internal fun CameraPosition.toMapCamera(): MapCamera? {
    val t = target ?: return null
    return MapCamera(t.toModel(), zoom, bearing, tilt)
}

internal fun MapStyleDefinition.toStyleBuilder(): Style.Builder = when (val s = source) {
    is StyleSource.Url -> Style.Builder().fromUri(s.url)
    is StyleSource.Json -> Style.Builder().fromJson(s.json)
}

/** A platform [Location] for MapLibre's LocationComponent, which only accepts that type. */
internal fun MapLocation.toAndroidLocation(): Location = Location(PROVIDER).also {
    it.latitude = position.latitude
    it.longitude = position.longitude
    it.time = timeMillis
    accuracyMeters?.let { a -> it.accuracy = a }
    bearingDegrees?.let { b -> it.bearing = b }
    speedMetersPerSecond?.let { s -> it.speed = s }
}

private const val PROVIDER = "openactivity"
