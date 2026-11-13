package com.pakohan.hips.ui

import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.pakohan.hips.R
import com.pakohan.hips.UiState

private enum class AppTab(val title: Int) {
    SCAN(R.string.tab_scan),
    WRITE(R.string.tab_write),
}

@Composable
fun HipsApp(
    state: UiState,
    onScan: (Uri) -> Unit,
    canWrite: (text: String, message: String) -> Boolean,
    placeholderText: (message: String) -> String?,
    onWrite: (text: String, message: String, target: Uri) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selected by rememberSaveable { mutableIntStateOf(AppTab.SCAN.ordinal) }
    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            PrimaryTabRow(selectedTabIndex = selected) {
                AppTab.entries.forEach { tab ->
                    Tab(
                        selected = selected == tab.ordinal,
                        onClick = { selected = tab.ordinal },
                        text = { Text(stringResource(tab.title)) },
                    )
                }
            }
            when (AppTab.entries[selected]) {
                AppTab.SCAN -> ScanScreen(state = state.scan, onScan = onScan)
                AppTab.WRITE -> WriteScreen(
                    state = state.write,
                    canWrite = canWrite,
                    placeholderText = placeholderText,
                    onWrite = onWrite,
                )
            }
        }
    }
}
