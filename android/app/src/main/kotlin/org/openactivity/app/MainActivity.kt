// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import org.openactivity.core.datastore.LaunchTab
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
import org.openactivity.app.feature.healthconnect.HealthConnectStartup
import org.openactivity.app.feature.onboarding.OnboardingScreen
import org.openactivity.app.model.ThemeMode
import org.openactivity.app.ui.OpenActivityApp
import org.openactivity.core.designsystem.theme.RidgelineTheme
import javax.inject.Inject

/**
 * The single activity. Draws edge to edge, follows the in-app theme preference and shows first-run
 * onboarding until it is finished. Notification taps, shared files and links open their target
 * ([LaunchRequest]).
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    @Inject lateinit var healthConnect: HealthConnectStartup

    /** Set when a recording notification launched or re-opened the app; cleared once handled. */
    private val launchRequest = MutableStateFlow<LaunchRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val coldStart = savedInstanceState == null
        // Links on a fresh start are left to the navigation graph, which builds the back stack for them.
        if (coldStart) launchRequest.value = LaunchRequest.consume(intent, includeLinks = false)
        setContent {
            val pendingRequest by launchRequest.collectAsStateWithLifecycle()
            val preferences by viewModel.preferences.collectAsStateWithLifecycle()
            val start by viewModel.start.collectAsStateWithLifecycle()
            val launchTab by viewModel.launchTab.collectAsStateWithLifecycle()
            // "Open app on Record": a cold launch from the launcher goes straight to the recorder; a
            // notification tap still goes where it asked to.
            LaunchedEffect(launchTab) {
                if (coldStart && launchTab == LaunchTab.RECORD && launchRequest.value == null && intent?.data == null) {
                    launchRequest.value = LaunchRequest.OpenRecorder
                }
            }
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
                        launchRequest = pendingRequest,
                        onLaunchRequestHandled = { launchRequest.value = null },
                        startTab = launchTab ?: LaunchTab.HOME,
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Phones whose Health Connect cannot read in the background stay in sync this way.
        healthConnect.onAppOpened()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        LaunchRequest.consume(intent, includeLinks = true)?.let { launchRequest.value = it }
    }
}
