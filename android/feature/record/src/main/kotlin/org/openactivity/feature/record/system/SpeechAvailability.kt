// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.feature.record.system

import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.openactivity.core.recordingservice.audio.SpeechStatus
import org.openactivity.core.recordingservice.audio.TextToSpeechCueSpeaker
import javax.inject.Inject

/** Whether spoken audio cues can play, so the settings can warn about a missing voice (record detail 7.5). */
interface SpeechAvailability {
    /**
     * The speech engine's status. A missing engine is known straight away; a missing voice only once the
     * engine has started (the first cue of a recording).
     */
    val status: Flow<SpeechStatus>
}

/**
 * [SpeechAvailability] read from the recording service's speaker. Before the speaker has started its engine,
 * the installed text-to-speech services are checked so a phone without any engine is warned about before
 * the first recording rather than after it.
 */
class CueSpeakerAvailability @Inject constructor(
    @ApplicationContext private val context: Context,
    private val speaker: TextToSpeechCueSpeaker,
) : SpeechAvailability {
    override val status: Flow<SpeechStatus> =
        combine(speaker.status, flow { emit(engineInstalled(context)) }.flowOn(Dispatchers.IO)) { status, installed ->
            if (status == SpeechStatus.NOT_STARTED && !installed) SpeechStatus.UNAVAILABLE else status
        }
}

/** True when at least one text-to-speech engine is installed (needs the TTS_SERVICE `<queries>` entry). */
internal fun engineInstalled(context: Context): Boolean =
    context.packageManager
        .queryIntentServices(Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE), 0)
        .isNotEmpty()
