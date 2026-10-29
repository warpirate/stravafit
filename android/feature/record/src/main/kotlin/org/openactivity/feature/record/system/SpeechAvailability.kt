// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.feature.record.system

import kotlinx.coroutines.flow.StateFlow
import org.openactivity.core.recordingservice.audio.SpeechStatus
import org.openactivity.core.recordingservice.audio.TextToSpeechCueSpeaker
import javax.inject.Inject

/** Whether spoken audio cues can play, so the settings can warn about a missing voice (record detail 7.5). */
interface SpeechAvailability {
    /** The speech engine's status; it is only known once the engine has started (the first cue). */
    val status: StateFlow<SpeechStatus>
}

/** [SpeechAvailability] read from the recording service's speaker. */
class CueSpeakerAvailability @Inject constructor(speaker: TextToSpeechCueSpeaker) : SpeechAvailability {
    override val status: StateFlow<SpeechStatus> = speaker.status
}
