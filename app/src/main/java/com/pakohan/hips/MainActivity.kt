package com.pakohan.hips

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.pakohan.hips.ui.HipsApp
import com.pakohan.hips.ui.theme.HIPSTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HIPSTheme {
                val state by viewModel.uiState.collectAsState()
                HipsApp(
                    state = state,
                    onScan = viewModel::scan,
                    canWrite = viewModel::canWrite,
                    onWrite = viewModel::write,
                )
            }
        }
    }
}
