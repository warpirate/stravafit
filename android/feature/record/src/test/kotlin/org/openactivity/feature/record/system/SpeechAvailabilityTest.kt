// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.feature.record.system

import android.content.ComponentName
import android.content.IntentFilter
import android.speech.tts.TextToSpeech
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.openactivity.core.recordingservice.audio.SpeechStatus
import org.openactivity.core.recordingservice.audio.TextToSpeechCueSpeaker
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class SpeechAvailabilityTest {
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()

    private fun availability() = CueSpeakerAvailability(context, TextToSpeechCueSpeaker(context))

    @Test fun noEngineInstalledIsReportedBeforeTheFirstCue() = runTest {
        assertEquals(SpeechStatus.UNAVAILABLE, availability().status.first())
    }

    @Test fun anInstalledEngineWaitsForTheFirstCue() = runTest {
        val engine = ComponentName("com.example.tts", "com.example.tts.Engine")
        shadowOf(context.packageManager).apply {
            addOrUpdateService(android.content.pm.ServiceInfo().apply {
                packageName = engine.packageName
                name = engine.className
            })
            addIntentFilterForService(engine, IntentFilter(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE))
        }
        assertEquals(SpeechStatus.NOT_STARTED, availability().status.first())
    }
}
