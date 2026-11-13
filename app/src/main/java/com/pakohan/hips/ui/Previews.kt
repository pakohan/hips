package com.pakohan.hips.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.pakohan.hips.ScanState
import com.pakohan.hips.WriteState
import com.pakohan.hips.ui.theme.HIPSTheme

private val foundMessage = ScanState.Done(
    message = "Meet me at the old harbour office at nine.",
    lines = 30,
    symbols = 1887,
    xHeight = 37f,
    packetsDecoded = 29,
    packetSlots = 30,
    correctedBits = 13,
    seconds = 4.2f,
)

@Composable
private fun Frame(selected: Int, content: @Composable () -> Unit) {
    HIPSTheme {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = { BrandHeader { ModeSwitch(listOf("Reveal", "Hide"), selected, onSelect = {}) } },
        ) { innerPadding ->
            Box(Modifier.padding(innerPadding)) { content() }
        }
    }
}

@Preview(name = "Reveal, message found", widthDp = 400, heightDp = 860)
@Composable
private fun RevealFoundPreview() {
    Frame(selected = 0) { ScanScreen(state = foundMessage, onScanPhoto = {}, onScanPdf = {}, onCancel = {}) }
}

@Preview(name = "Reveal, working, dark", widthDp = 400, heightDp = 860, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun RevealWorkingDarkPreview() {
    Frame(selected = 0) { ScanScreen(state = ScanState.Working, onScanPhoto = {}, onScanPdf = {}, onCancel = {}) }
}

@Preview(name = "Hide, written", widthDp = 400, heightDp = 860)
@Composable
private fun HideWrittenPreview() {
    Frame(selected = 1) {
        WriteScreen(
            state = WriteState.Done(pages = 1, packetsNeeded = 8, packetSlots = 30),
            canWrite = { _, _ -> true },
            placeholderText = { null },
            onWrite = { _, _, _ -> },
        )
    }
}

@Preview(name = "Hide, dark", widthDp = 400, heightDp = 860, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HideDarkPreview() {
    Frame(selected = 1) {
        WriteScreen(state = WriteState.Idle, canWrite = { _, _ -> true }, placeholderText = { null }, onWrite = { _, _, _ -> })
    }
}
