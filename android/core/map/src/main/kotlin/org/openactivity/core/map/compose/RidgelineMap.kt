// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.map.compose

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.maplibre.android.MapLibre
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.openactivity.core.designsystem.theme.RidgelineTheme
import org.openactivity.core.map.MapLocation
import org.openactivity.core.map.camera.MapCamera
import org.openactivity.core.map.overlay.MapOverlays
import org.openactivity.core.map.overlay.MapPalette
import org.openactivity.core.map.overlay.OverlayPlan
import org.openactivity.core.map.overlay.OverlayPlanner
import org.openactivity.core.map.render.LocationPuck
import org.openactivity.core.map.render.OverlayRenderer
import org.openactivity.core.map.render.toModel
import org.openactivity.core.map.style.MapStyleDefinition
import org.openactivity.core.map.style.MapStyleKind
import org.openactivity.model.LatLng

/**
 * An interactive MapLibre map with our overlays, location puck and visible OSM attribution.
 *
 * State is hoisted: the caller owns [state] (camera, follow mode), [styleKind], [overlays] and
 * [location]. The map never reads GPS itself; pass fixes from the app's location stream.
 * Lifecycle events of the hosting screen are forwarded to the underlying `MapView`, and the view is
 * destroyed when this composable leaves composition.
 *
 * @param state camera and tracking state; see [rememberMapState].
 * @param styleKind base map to show, resolved through [LocalMapStyleProvider].
 * @param overlays live track, planned route and activity route.
 * @param location the user's last fix, or null to hide the puck.
 * @param palette overlay colours; defaults to the Ridgeline theme.
 * @param gesturesEnabled false for a static, non-interactive map.
 * @param contentPadding insets for the attribution line (e.g. above a bottom sheet).
 * @param textureMode render into a TextureView; use inside scrolling or animated containers.
 * @param onMapClick called with the tapped position.
 * @param onMapLongClick called with the long-pressed position.
 */
@Composable
fun RidgelineMap(
    modifier: Modifier = Modifier,
    state: MapState = rememberMapState(),
    styleKind: MapStyleKind = MapStyleKind.default(isMapThemeDark()),
    overlays: MapOverlays = MapOverlays.Empty,
    location: MapLocation? = null,
    palette: MapPalette = mapPalette(),
    gesturesEnabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    textureMode: Boolean = false,
    onMapClick: ((LatLng) -> Unit)? = null,
    onMapLongClick: ((LatLng) -> Unit)? = null,
) {
    val provider = LocalMapStyleProvider.current
    val definition = remember(provider, styleKind) { provider.style(styleKind) }
    Box(modifier) {
        if (LocalInspectionMode.current) {
            // Previews and screenshot tests cannot load the native renderer.
            Box(Modifier.fillMaxSize().background(RidgelineTheme.colors.mapLand))
        } else {
            MapViewHost(
                state = state,
                definition = definition,
                overlays = overlays,
                location = location,
                palette = palette,
                gesturesEnabled = gesturesEnabled,
                textureMode = textureMode,
                onMapClick = onMapClick,
                onMapLongClick = onMapLongClick,
            )
        }
        MapAttribution(
            definition = definition,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(contentPadding)
                .padding(4.dp),
        )
    }
}

@Composable
private fun MapViewHost(
    state: MapState,
    definition: MapStyleDefinition,
    overlays: MapOverlays,
    location: MapLocation?,
    palette: MapPalette,
    gesturesEnabled: Boolean,
    textureMode: Boolean,
    onMapClick: ((LatLng) -> Unit)?,
    onMapLongClick: ((LatLng) -> Unit)?,
) {
    val context = LocalContext.current
    val reducedMotion by rememberUpdatedState(RidgelineTheme.motion.reduced)
    val mapView = remember(textureMode) { createMapView(context, textureMode) }
    MapLifecycleEffect(mapView)

    var map by remember(mapView) { mutableStateOf<MapLibreMap?>(null) }
    var style by remember(mapView) { mutableStateOf<Style?>(null) }

    AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())

    LaunchedEffect(mapView) {
        val m = mapView.awaitMap()
        m.configureDefaults()
        val camera = state.camera
        if (camera != null) m.jumpTo(camera) else m.jumpTo(WORLD_CAMERA)
        map = m
    }
    LaunchedEffect(map, gesturesEnabled) { map?.uiSettings?.setAllGesturesEnabled(gesturesEnabled) }
    LaunchedEffect(map, definition) {
        val m = map ?: return@LaunchedEffect
        state.isStyleLoaded = false
        style = null
        style = m.awaitStyle(definition)
        state.isStyleLoaded = true
    }

    // Overlays: plan off the main thread (GeoJSON for long tracks), apply on it.
    val renderer = remember(style) { style?.let(::OverlayRenderer) }
    val plan by produceState<OverlayPlan?>(null, overlays, palette) {
        value = withContext(Dispatchers.Default) { OverlayPlanner.plan(overlays, palette) }
    }
    LaunchedEffect(renderer, plan) {
        val p = plan ?: return@LaunchedEffect
        renderer?.apply(p)
    }

    // Puck and follow mode.
    val puck = remember(map) { map?.let { m -> LocationPuck(context, m) { state.tracking = CameraTracking.Free } } }
    DisposableEffect(puck) { onDispose { puck?.release() } }
    LaunchedEffect(puck, style, location, palette) {
        val s = style ?: return@LaunchedEffect
        puck?.update(s, location, palette)
    }
    LaunchedEffect(puck, style, state.tracking) {
        if (style != null) puck?.setFollow(state.tracking == CameraTracking.Follow)
    }

    // One-shot camera commands.
    LaunchedEffect(map) {
        val m = map ?: return@LaunchedEffect
        state.commands.collect { command ->
            puck?.cancelTracking()
            mapView.awaitLaidOut()
            m.execute(command, mapView, animate = command.animate && !reducedMotion)
        }
    }

    // Camera bookkeeping and taps.
    val clickHandler by rememberUpdatedState(onMapClick)
    val longClickHandler by rememberUpdatedState(onMapLongClick)
    DisposableEffect(map) {
        val m = map ?: return@DisposableEffect onDispose { }
        val idle = MapLibreMap.OnCameraIdleListener { m.cameraPosition.toMapCameraOrNull()?.let { state.camera = it } }
        val click = MapLibreMap.OnMapClickListener { p ->
            val h = clickHandler ?: return@OnMapClickListener false
            h(p.toModel())
            true
        }
        val longClick = MapLibreMap.OnMapLongClickListener { p ->
            val h = longClickHandler ?: return@OnMapLongClickListener false
            h(p.toModel())
            true
        }
        m.addOnCameraIdleListener(idle)
        m.addOnMapClickListener(click)
        m.addOnMapLongClickListener(longClick)
        onDispose {
            m.removeOnCameraIdleListener(idle)
            m.removeOnMapClickListener(click)
            m.removeOnMapLongClickListener(longClick)
        }
    }
}

private fun createMapView(context: Context, textureMode: Boolean): MapView {
    MapLibre.getInstance(context.applicationContext)
    val options = MapLibreMapOptions.createFromAttributes(context)
        // Attribution is drawn by MapAttribution as visible text; the (i) button alone is not enough.
        .attributionEnabled(false)
        .logoEnabled(false)
        .compassEnabled(true)
        .textureMode(textureMode)
    return MapView(context, options).also { it.onCreate(null) }
}

/**
 * Forwards the host lifecycle and low-memory callbacks to [mapView], and destroys it when the
 * composable leaves composition.
 */
@Composable
private fun MapLifecycleEffect(mapView: MapView) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val context = LocalContext.current
    val forwarder = remember(mapView) { MapLifecycleForwarder(mapView.asMapViewLifecycle()) }
    // Compose disposes effects in reverse order, so declaring this first destroys the view last.
    DisposableEffect(forwarder) { onDispose { forwarder.destroy() } }
    DisposableEffect(lifecycle, forwarder) {
        val observer = LifecycleEventObserver { _, event -> forwarder.moveTo(event.targetState) }
        // addObserver replays the current state, so the view catches up to STARTED/RESUMED here.
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            forwarder.moveTo(Lifecycle.State.CREATED)
        }
    }
    DisposableEffect(context, mapView) {
        val memory = object : ComponentCallbacks2 {
            override fun onConfigurationChanged(newConfig: Configuration) = Unit

            @Deprecated("Deprecated in Java")
            override fun onLowMemory() = mapView.onLowMemory()

            override fun onTrimMemory(level: Int) {
                @Suppress("DEPRECATION")
                if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) mapView.onLowMemory()
            }
        }
        context.registerComponentCallbacks(memory)
        onDispose { context.unregisterComponentCallbacks(memory) }
    }
}

private val WORLD_CAMERA = MapCamera(LatLng(20.0, 0.0), zoom = 1.0)
