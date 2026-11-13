package com.pakohan.hips

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.pakohan.hips.ui.HipsApp
import com.pakohan.hips.ui.IntroScreen
import com.pakohan.hips.ui.theme.HIPSTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HIPSTheme {
                val state by viewModel.uiState.collectAsState()
                // Saved, so the intro plays once per launch and not again after a rotation.
                var introShown by rememberSaveable { mutableStateOf(false) }
                Crossfade(targetState = introShown, label = "intro") { shown ->
                    if (shown) {
                        HipsApp(
                            state = state,
                            onScanPhoto = viewModel::scanPhoto,
                            onScanPdf = viewModel::scanPdf,
                            onCancelScan = viewModel::cancelScan,
                            canWrite = viewModel::canWrite,
                            placeholderText = viewModel::placeholderText,
                            onWrite = viewModel::write,
                        )
                    } else {
                        IntroScreen(onFinished = { introShown = true })
                    }
                }
            }
        }
    }
}
