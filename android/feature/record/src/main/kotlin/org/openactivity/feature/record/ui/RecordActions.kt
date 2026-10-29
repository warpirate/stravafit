// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.feature.record.ui

import androidx.compose.runtime.Immutable
import org.openactivity.feature.record.RecordSheet
import org.openactivity.model.SportType

/** Callbacks of the stateless Record screen. Defaults do nothing, for previews and screenshots. */
@Immutable
data class RecordActions(
    val onClose: () -> Unit = {},
    val onShowSheet: (RecordSheet) -> Unit = {},
    val onSelectSport: (SportType) -> Unit = {},
    val onSelectRoute: (Long?) -> Unit = {},
    val onToggleAutoPause: () -> Unit = {},
    val onToggleAudioCues: () -> Unit = {},
    val onSetCountdown: (Boolean) -> Unit = {},
    val onSetKeepScreenOn: (Boolean) -> Unit = {},
    val onFixLocation: () -> Unit = {},
    val onStart: () -> Unit = {},
    val onCancelCountdown: () -> Unit = {},
    val onPause: () -> Unit = {},
    val onResume: () -> Unit = {},
    val onLap: () -> Unit = {},
    val onFinish: () -> Unit = {},
    val onSave: () -> Unit = {},
    val onDiscard: () -> Unit = {},
    val onConfirmDiscard: () -> Unit = {},
    val onCyclePaceMode: () -> Unit = {},
    val onResumeInterrupted: () -> Unit = {},
    val onSaveInterrupted: () -> Unit = {},
    val onDiscardInterrupted: () -> Unit = {},
    val onAllowBackground: () -> Unit = {},
    val onDismissBatteryPrompt: () -> Unit = {},
    /** Sensors (Bluetooth heart rate, power, cadence) on the pre-start screen. */
    val onSensors: () -> Unit = {},
    /** Beacon (live location sharing) on the pre-start screen. */
    val onBeacon: () -> Unit = {},
)
