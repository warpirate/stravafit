// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import org.openactivity.app.model.ThemeMode
import org.openactivity.app.ui.OpenActivityApp
import org.openactivity.core.designsystem.theme.RidgelineTheme

/** The single activity. Draws edge to edge and follows the in-app theme preference. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val preferences by viewModel.preferences.collectAsStateWithLifecycle()
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
                OpenActivityApp()
            }
        }
    }
}
