// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.map.thumbnail

import android.graphics.Bitmap
import android.graphics.Color
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.openactivity.core.map.camera.CameraMath
import org.openactivity.core.map.overlay.MapPalette
import org.openactivity.core.map.style.MapStyleKind
import org.openactivity.model.LatLng
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ThumbnailTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val palette = MapPalette(
        liveTrack = 0xFF0F7E8A.toInt(),
        plannedRoute = 0xFF2F6FB3.toInt(),
        activityRoute = 0xFFB23A5A.toInt(),
        casing = Color.WHITE,
        marker = 0xFFB23A5A.toInt(),
        puck = 0xFF0F7E8A.toInt(),
        puckAccuracy = 0xFF0F7E8A.toInt(),
        land = 0xFFE8EEEB.toInt(),
    )
    private val route = listOf(LatLng(18.50, 73.80), LatLng(18.52, 73.83), LatLng(18.51, 73.86), LatLng(18.53, 73.88))

    private fun request(points: List<LatLng> = route, w: Int = 300, h: Int = 150, style: MapStyleKind = MapStyleKind.Standard) =
        ThumbnailRequest(points, w, h, density = 2f, style = style, palette = palette)

    @Test
    fun cacheKeyIsStableAndSensitiveToInputs() {
        assertEquals(request().cacheKey, request().cacheKey)
        assertEquals(64, request().cacheKey.length)
        assertNotEquals(request().cacheKey, request(w = 301).cacheKey)
        // An outline without markers (a privacy zone) is a different picture.
        assertNotEquals(request().cacheKey, request().copy(showEndpoints = false).cacheKey)
        assertNotEquals(request().cacheKey, request(style = MapStyleKind.Dark).cacheKey)
        assertNotEquals(request().cacheKey, request(points = route.reversed()).cacheKey)
        assertNotEquals(request().cacheKey, request().copy(palette = palette.copy(activityRoute = Color.RED)).cacheKey)
        // One cached map per activity (and per theme, through the style).
        assertNotEquals(request().copy(subjectId = "activity-1").cacheKey, request().copy(subjectId = "activity-2").cacheKey)
        assertNotEquals(request().cacheKey, request().copy(subjectId = "activity-1").cacheKey)
        assertNotEquals(
            request().copy(subjectId = "activity-1").cacheKey,
            request(style = MapStyleKind.Dark).copy(subjectId = "activity-1").cacheKey,
        )
    }

    @Test
    fun cameraFramesTheRouteWithPadding() {
        val r = request()
        val camera = r.camera!!
        for (p in route) {
            val o = CameraMath.project(p, camera, r.widthDp, r.heightDp)
            assertTrue("$p -> $o", o.x >= ThumbnailRequest.PADDING_DP - 0.01f && o.x <= r.widthDp - ThumbnailRequest.PADDING_DP + 0.01f)
            assertTrue("$p -> $o", o.y >= ThumbnailRequest.PADDING_DP - 0.01f && o.y <= r.heightDp - ThumbnailRequest.PADDING_DP + 0.01f)
        }
    }

    @Test
    fun offlineRendererDrawsTheRouteOnLand() {
        val r = request()
        val bitmap = CanvasThumbnailRenderer().renderNow(r)!!
        assertEquals(300, bitmap.width)
        assertEquals(150, bitmap.height)
        assertEquals(palette.land, bitmap.getPixel(1, 1))
        // Green start in a white ring, chequered finish flag (dark and white squares), as on the live map.
        val camera = r.camera!!
        val start = CameraMath.project(route.first(), camera, r.widthDp, r.heightDp)
        assertEquals(palette.start, bitmap.getPixel((start.x * 2).toInt(), (start.y * 2).toInt()))
        assertTrue("chequered finish", bitmap.countPixels(palette.finish) > 4)
        assertTrue("route colour present", bitmap.countPixels(palette.activityRoute) > 200)
    }

    @Test
    fun offlineRendererNeedsTwoPoints() {
        assertNull(CanvasThumbnailRenderer().renderNow(request(points = route.take(1))))
    }

    @Test
    fun diskCacheRoundTripAndEviction() = runTest {
        val dir = temp.newFolder("thumbs")
        val bitmap = solid(64, 32, Color.BLUE)
        val cache = DiskThumbnailCache(dir, maxBytes = Long.MAX_VALUE)
        assertNull(cache.get("abc"))
        cache.put("abc", bitmap)
        val loaded = cache.get("abc")!!
        assertEquals(64, loaded.width)
        assertEquals(32, loaded.height)

        // Evicts least recently used first once over budget.
        cache.put("old", bitmap)
        val oldFile = dir.resolve("old.jpg")
        oldFile.setLastModified(1_000)
        val size = oldFile.length()
        val small = DiskThumbnailCache(dir, maxBytes = size * 2 + 1)
        small.put("new", bitmap)
        assertTrue(dir.resolve("new.jpg").exists())
        assertTrue("oldest evicted", !oldFile.exists())
    }

    @Test
    fun corruptCacheEntryIsDropped() = runTest {
        val dir = temp.newFolder("corrupt")
        dir.resolve("bad.jpg").writeText("not an image")
        val cache = DiskThumbnailCache(dir)
        assertNull(cache.get("bad"))
        assertTrue(!dir.resolve("bad.jpg").exists())
    }

    @Test(expected = IllegalArgumentException::class)
    fun cacheKeysCannotEscapeTheDirectory() = runTest {
        DiskThumbnailCache(temp.newFolder("keys")).get("../secret")
    }

    @Test
    fun loaderPrefersCacheThenSnapshotThenFallback() = runTest {
        val cache = MemoryCache()
        val snapshotBitmap = solid(10, 10, Color.GREEN)
        val fallbackBitmap = solid(10, 10, Color.GRAY)
        var online = true
        var snapshots = 0
        val loader = RouteThumbnailLoader(
            cache = cache,
            snapshot = { snapshots++; snapshotBitmap },
            fallback = { fallbackBitmap },
            network = { online },
        )

        // Online: real snapshot, cached.
        val first = loader.load(request())!!
        assertEquals(ThumbnailSource.MapSnapshot, first.source)
        assertSame(snapshotBitmap, first.bitmap)
        assertNotNull(cache.map[request().cacheKey])

        // Cached: no second snapshot even offline.
        online = false
        assertEquals(ThumbnailSource.MapSnapshot, loader.load(request())!!.source)
        assertEquals(1, snapshots)

        // Offline and not cached: fallback, and it is not cached.
        val other = request(w = 200)
        val offline = loader.load(other)!!
        assertEquals(ThumbnailSource.Offline, offline.source)
        assertNull(cache.map[other.cacheKey])
    }

    @Test
    fun loaderFallsBackWhenSnapshotFailsOrHangs() = runTest {
        val fallbackBitmap = solid(10, 10, Color.GRAY)
        val failing = RouteThumbnailLoader(MemoryCache(), { null }, { fallbackBitmap }, { true })
        assertEquals(ThumbnailSource.Offline, failing.load(request())!!.source)

        val hanging = RouteThumbnailLoader(MemoryCache(), { delay(60_000); null }, { fallbackBitmap }, { true }, snapshotTimeoutMs = 1_000)
        assertEquals(ThumbnailSource.Offline, hanging.load(request())!!.source)
    }

    @Test
    fun loaderSkipsRoutesWithoutALine() = runTest {
        val loader = RouteThumbnailLoader(MemoryCache(), { error("not called") }, { error("not called") }, { true })
        assertNull(loader.load(request(points = route.take(1))))
    }

    @Test
    fun progressiveLoadShowsTheDrawingUntilTheSnapshotArrives() = runTest {
        val snapshotBitmap = solid(300, 150, Color.BLUE)
        val fallbackBitmap = solid(300, 150, Color.GRAY)
        val cache = MemoryCache()
        val loader = RouteThumbnailLoader(cache, { snapshotBitmap }, { fallbackBitmap }, { true })
        val first = loader.loadProgressively(request()).toList()
        assertEquals(listOf(ThumbnailSource.Offline, ThumbnailSource.MapSnapshot), first.map { it.source })
        assertSame(snapshotBitmap, first.last().bitmap)

        // Cached now: only the snapshot, no placeholder flash.
        val second = loader.loadProgressively(request()).toList()
        assertEquals(listOf(ThumbnailSource.MapSnapshot), second.map { it.source })

        assertTrue(loader.loadProgressively(request(points = route.take(1))).toList().isEmpty())
    }

    @Test
    fun offlineCardKeepsThePlainDrawingAndGetsTheMapWhenBackOnline() = runTest {
        val snapshotBitmap = solid(300, 150, Color.BLUE)
        val fallbackBitmap = solid(300, 150, Color.GRAY)
        var online = false
        var snapshots = 0
        val loader = RouteThumbnailLoader(MemoryCache(), { snapshots++; snapshotBitmap }, { fallbackBitmap }, { online })
        val seen = mutableListOf<ThumbnailSource>()
        val job = launch { loader.loadProgressively(request()).collect { seen += it.source } }
        advanceTimeBy(60_000)
        assertEquals(listOf(ThumbnailSource.Offline), seen)
        assertEquals("no snapshot while offline", 0, snapshots)
        online = true
        advanceTimeBy(NetworkMonitor.OFFLINE_POLL_MS + 1)
        job.join()
        assertEquals(listOf(ThumbnailSource.Offline, ThumbnailSource.MapSnapshot), seen)
    }

    @Test
    fun failedSnapshotIsRetried() = runTest {
        val snapshotBitmap = solid(300, 150, Color.BLUE)
        var calls = 0
        val loader = RouteThumbnailLoader(
            MemoryCache(),
            { calls++; if (calls < 3) null else snapshotBitmap },
            { solid(300, 150, Color.GRAY) },
            { true },
            retryDelaysMs = listOf(1_000, 1_000, 1_000),
        )
        val sources = loader.loadProgressively(request()).toList().map { it.source }
        assertEquals(listOf(ThumbnailSource.Offline, ThumbnailSource.MapSnapshot), sources)
        assertEquals(3, calls)
    }

    @Test
    fun snapshotFinishesInTheLoaderScopeAfterTheCardLeaves() = runTest {
        val snapshotBitmap = solid(300, 150, Color.BLUE)
        val cache = MemoryCache()
        var renders = 0
        val loader = RouteThumbnailLoader(
            cache,
            { renders++; delay(5_000); snapshotBitmap },
            { solid(300, 150, Color.GRAY) },
            { true },
            scope = backgroundScope,
            memory = ThumbnailMemoryCache(10 * 1024 * 1024),
        )
        // Two cards ask for the same picture; one scrolls away mid-render.
        val first = launch { loader.loadProgressively(request()).collect {} }
        val second = launch { loader.loadProgressively(request()).collect {} }
        advanceTimeBy(1_000)
        first.cancel()
        second.join()
        assertEquals("one shared render", 1, renders)
        assertSame(snapshotBitmap, cache.map[request().cacheKey])
        // Scrolled back into view: the memory copy is there synchronously, so no placeholder flash.
        assertEquals(ThumbnailSource.MapSnapshot, loader.peek(request())?.source)
    }

    private class MemoryCache : ThumbnailCache {
        val map = HashMap<String, Bitmap>()
        override suspend fun get(key: String): Bitmap? = map[key]
        override suspend fun put(key: String, bitmap: Bitmap) {
            map[key] = bitmap
        }
    }

    private fun solid(w: Int, h: Int, color: Int): Bitmap =
        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }

    private fun Bitmap.countPixels(color: Int): Int {
        val pixels = IntArray(width * height)
        getPixels(pixels, 0, width, 0, 0, width, height)
        return pixels.count { it == color }
    }
}
