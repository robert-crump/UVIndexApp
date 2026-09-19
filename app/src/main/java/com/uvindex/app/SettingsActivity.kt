package com.uvindex.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.core.view.WindowCompat
import com.uvindex.app.ui.settings.SettingsScreen
import com.uvindex.app.ui.settings.SettingsViewModel
import com.uvindex.app.ui.theme.UVIndexTheme

class SettingsActivity : ComponentActivity() {

    companion object {
        const val EXTRA_HIGHLIGHT_SKIN_TYPE = "highlight_skin_type"
    }

    private val viewModel: SettingsViewModel by viewModels { SettingsViewModel.factory(application) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val highlightSkinType = intent.getBooleanExtra(EXTRA_HIGHLIGHT_SKIN_TYPE, false)

        // Enable edge-to-edge for seamless display
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            UVIndexTheme {
                // Set status bar color
                LaunchedEffect(Unit) {
                    window.statusBarColor = android.graphics.Color.TRANSPARENT
                    window.navigationBarColor = android.graphics.Color.TRANSPARENT
                    WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false
                }

                SettingsScreen(
                    viewModel = viewModel,
                    onBackPressed = { finish() },
                    highlightSkinType = highlightSkinType
                )
            }
        }
    }
}
