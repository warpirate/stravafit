// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import org.openactivity.app.feature.onboarding.OnboardingScreen
import org.openactivity.app.model.ThemeMode
import org.openactivity.app.ui.OpenActivityApp
import org.openactivity.core.designsystem.theme.RidgelineTheme
import org.openactivity.core.recordingservice.notification.RecordingNotifications

/**
 * The single activity. Draws edge to edge, follows the in-app theme preference and shows first-run
 * onboarding until it is finished.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    /** Set when the recording notification launched or re-opened the app. */
    private val openRecorder = MutableStateFlow(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) consumeRecorderExtra(intent)
        setContent {
            val openRecorderRequested by openRecorder.collectAsStateWithLifecycle()
            val preferences by viewModel.preferences.collectAsStateWithLifecycle()
            val start by viewModel.start.collectAsStateWithLifecycle()
            val dark = when (preferences.themeMode) {
                ThemeMode.System -> isSystemInDarkTheme()
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }
            DisposableEffect(dark) {
                val style = if (dark) {
                    SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            RidgelineTheme(darkTheme = dark, reduceMotion = preferences.reduceMotion) {
                when (start) {
                    AppStart.Loading -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
                    // Onboarding writes its completion flag; [start] then switches to the app by itself.
                    AppStart.Onboarding -> OnboardingScreen(onFinished = {})
                    AppStart.Ready -> OpenActivityApp(
                        openRecorderRequested = openRecorderRequested,
                        onRecorderOpened = { openRecorder.value = false },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeRecorderExtra(intent)
    }

    private fun consumeRecorderExtra(intent: Intent?) {
        if (intent?.getBooleanExtra(RecordingNotifications.EXTRA_OPEN_RECORDING, false) == true) {
            intent.removeExtra(RecordingNotifications.EXTRA_OPEN_RECORDING)
            openRecorder.value = true
        }
    }
}
