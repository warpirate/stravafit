// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.recordingservice.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.openactivity.model.UnitSystem
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/** Whether spoken cues can play. */
enum class SpeechStatus {
    /** The speech engine has not been started yet. */
    NOT_STARTED,

    READY,

    /** No speech engine, or it failed to start. */
    UNAVAILABLE,

    /** The engine works but has no voice for the device language (record detail 7.5: warn the user). */
    LANGUAGE_MISSING,
}

/**
 * Speaks cues with the system text-to-speech engine (record detail 7.5).
 *
 * Speech uses navigation-guidance audio attributes and transient focus with ducking, so music dips rather
 * than stops. Cues queue in order; cues older than [STALE_MILLIS] when the engine becomes ready are
 * dropped. The engine starts lazily on the first cue.
 */
@Singleton
class TextToSpeechCueSpeaker @Inject constructor(
    @ApplicationContext private val context: Context,
) : CueSpeaker {
    private val phrases = CuePhrases(context.resources)
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attributes)
        .build()
    private val pending = ArrayDeque<Pair<String, Long>>()
    private val inFlight = AtomicInteger(0)
    private val utteranceIds = AtomicInteger(0)
    private var tts: TextToSpeech? = null
    private var shutdownWhenIdle = false
    private val _status = MutableStateFlow(SpeechStatus.NOT_STARTED)

    /** Current speech availability, for a warning in the audio settings. */
    val status: StateFlow<SpeechStatus> = _status.asStateFlow()

    @Synchronized
    override fun speak(cue: AudioCue, units: UnitSystem) {
        shutdownWhenIdle = false // a new recording wants the engine after all
        val text = phrases.phrase(cue, units)
        when (_status.value) {
            SpeechStatus.READY -> say(text)
            SpeechStatus.NOT_STARTED -> {
                pending += text to System.currentTimeMillis()
                startEngine()
            }
            SpeechStatus.UNAVAILABLE, SpeechStatus.LANGUAGE_MISSING -> Unit
        }
    }

    /**
     * Releases the engine once the cues already queued (such as "Activity saved", spoken just before the
     * service stops) have played, rather than cutting them off.
     */
    @Synchronized
    override fun shutdown() {
        if (inFlight.get() > 0 || (tts != null && pending.isNotEmpty())) {
            shutdownWhenIdle = true
        } else {
            shutdownNow()
        }
    }

    @Synchronized
    private fun shutdownNow() {
        shutdownWhenIdle = false
        tts?.stop()
        tts?.shutdown()
        tts = null
        pending.clear()
        inFlight.set(0)
        _status.value = SpeechStatus.NOT_STARTED
        audioManager?.abandonAudioFocusRequest(focusRequest)
    }

    private fun startEngine() {
        if (tts != null) return
        // Without any speech engine installed, the constructor reports ERROR synchronously (before `tts`
        // is assigned), so onInit must not depend on `tts` for the failure path.
        val engine = TextToSpeech(context.applicationContext) { result -> onInit(result) }
        if (_status.value == SpeechStatus.UNAVAILABLE) engine.shutdown() else tts = engine
    }

    @Synchronized
    private fun onInit(result: Int) {
        if (result != TextToSpeech.SUCCESS) {
            _status.value = SpeechStatus.UNAVAILABLE
            pending.clear()
            tts?.shutdown()
            tts = null
            shutdownWhenIdle = false
            return
        }
        val engine = tts ?: return
        val language = engine.setLanguage(Locale.getDefault())
        if (language == TextToSpeech.LANG_MISSING_DATA || language == TextToSpeech.LANG_NOT_SUPPORTED) {
            _status.value = SpeechStatus.LANGUAGE_MISSING
            pending.clear()
            if (shutdownWhenIdle) shutdownNow()
            return
        }
        engine.setAudioAttributes(attributes)
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit

            override fun onDone(utteranceId: String?) = finished()

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = finished()

            override fun onError(utteranceId: String?, errorCode: Int) = finished()
        })
        _status.value = SpeechStatus.READY
        val now = System.currentTimeMillis()
        while (pending.isNotEmpty()) {
            val (text, at) = pending.removeFirst()
            if (now - at <= STALE_MILLIS) say(text)
        }
        if (shutdownWhenIdle && inFlight.get() == 0) shutdownNow()
    }

    private fun say(text: String) {
        val engine = tts ?: return
        if (inFlight.getAndIncrement() == 0) audioManager?.requestAudioFocus(focusRequest)
        val id = "cue-${utteranceIds.incrementAndGet()}"
        if (engine.speak(text, TextToSpeech.QUEUE_ADD, Bundle(), id) != TextToSpeech.SUCCESS) finished()
    }

    @Synchronized
    private fun finished() {
        if (inFlight.decrementAndGet() <= 0) {
            inFlight.set(0)
            audioManager?.abandonAudioFocusRequest(focusRequest)
            if (shutdownWhenIdle) shutdownNow()
        }
    }

    private companion object {
        const val STALE_MILLIS = 10_000L
    }
}
