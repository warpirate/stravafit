// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.feature.widgets

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.openactivity.core.recordingservice.PendingStart
import org.openactivity.core.recordingservice.RecordingController
import org.openactivity.core.recordingservice.RecordingUiSnapshot
import org.openactivity.core.recordingservice.StartCountdown
import org.openactivity.model.SportType
import org.openactivity.recording.RecordingStatus
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

/** What the Quick Settings tile shows and what a tap on it does. Pure, so it is unit-tested. */
data class TileModel(val kind: Kind, val sport: SportType?, val elapsedSeconds: Long) {
    enum class Kind { READY, STARTING, WAITING_FOR_GPS, RECORDING, PAUSED }

    /** Tile.STATE_ACTIVE while something is going on, so the tile lights up in the shade. */
    val active: Boolean get() = kind != Kind.READY

    /** A tap: open the recorder (ready to start), or pause / resume the recording. */
    val tap: TileTap
        get() = when (kind) {
            Kind.READY, Kind.STARTING, Kind.WAITING_FOR_GPS -> TileTap.OPEN_RECORDER
            Kind.RECORDING -> TileTap.PAUSE
            Kind.PAUSED -> TileTap.RESUME
        }
}

/** What tapping the tile does. */
enum class TileTap { OPEN_RECORDER, PAUSE, RESUME }

/** Maps the recording to the tile. */
fun tileModel(snapshot: RecordingUiSnapshot, pending: PendingStart?): TileModel = when {
    snapshot.isActive -> TileModel(
        kind = if (snapshot.status == RecordingStatus.PAUSED || snapshot.status == RecordingStatus.AUTO_PAUSED) TileModel.Kind.PAUSED else TileModel.Kind.RECORDING,
        sport = snapshot.sport,
        elapsedSeconds = snapshot.timerTime.inWholeSeconds,
    )
    pending != null -> TileModel(
        if (pending.waitingForGps) TileModel.Kind.WAITING_FOR_GPS else TileModel.Kind.STARTING,
        pending.sport,
        0,
    )
    else -> TileModel(TileModel.Kind.READY, null, 0)
}

/**
 * Quick Settings "Record" tile. Idle, a tap opens the recorder ready to start (the record screen keeps its
 * GPS and permission rules); while recording it shows the live state and a tap pauses or resumes through
 * the same [RecordingController] the record screen and the notification use.
 */
@AndroidEntryPoint
class RecordTileService : TileService() {
    @Inject lateinit var controller: RecordingController

    @Inject lateinit var countdown: StartCountdown

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var listening: Job? = null
    private var model = TileModel(TileModel.Kind.READY, null, 0)

    override fun onStartListening() {
        super.onStartListening()
        listening?.cancel()
        listening = scope.launch {
            // Whole seconds are enough for the subtitle; the tile is only listened to while the shade is open.
            combine(controller.state, countdown.pending, ::tileModel).distinctUntilChanged().collect {
                model = it
                render(it)
            }
        }
    }

    override fun onStopListening() {
        listening?.cancel()
        listening = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        when (model.tap) {
            TileTap.PAUSE -> scope.launch { controller.pause() }
            TileTap.RESUME -> scope.launch { controller.resume() }
            TileTap.OPEN_RECORDER -> if (isLocked) unlockAndRun { openRecorder() } else openRecorder()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openRecorder() {
        val sport = model.sport
        val intent = WidgetLinks.intent(this, if (sport != null) WidgetLinks.record(sport) else WidgetLinks.RECORD)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun render(model: TileModel) {
        val tile = qsTile ?: return
        tile.label = getString(R.string.tile_record)
        // The icon shows what a tap does: pause while recording, resume while paused.
        val icon = when (model.kind) {
            TileModel.Kind.RECORDING -> R.drawable.ic_widget_pause
            TileModel.Kind.PAUSED -> R.drawable.ic_widget_play
            else -> R.drawable.ic_tile_record
        }
        tile.icon = Icon.createWithResource(this, icon)
        tile.state = if (model.active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        val clock = WidgetFormat.clock(model.elapsedSeconds.seconds)
        val subtitle = when (model.kind) {
            TileModel.Kind.READY -> getString(R.string.tile_ready)
            TileModel.Kind.STARTING -> getString(R.string.tile_starting)
            TileModel.Kind.WAITING_FOR_GPS -> getString(R.string.tile_waiting_gps)
            TileModel.Kind.RECORDING -> getString(R.string.tile_recording, clock)
            TileModel.Kind.PAUSED -> getString(R.string.tile_paused, clock)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) tile.subtitle = subtitle
        tile.contentDescription = "${tile.label}. $subtitle"
        tile.updateTile()
    }
}
