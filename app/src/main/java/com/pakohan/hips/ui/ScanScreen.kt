package com.pakohan.hips.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.pakohan.hips.R
import com.pakohan.hips.ScanState
import java.io.File

@Composable
fun ScanScreen(
    state: ScanState,
    onScanPhoto: (Uri) -> Unit,
    onScanPdf: (Uri) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val photoUri = remember { photoUri(context) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        if (taken) onScanPhoto(photoUri)
    }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { picked ->
        if (picked != null) onScanPhoto(picked)
    }
    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { picked ->
        if (picked != null) onScanPdf(picked)
    }
    val working = state is ScanState.Working

    Column(
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.scan_intro))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { camera.launch(photoUri) }, enabled = !working) {
                Text(stringResource(R.string.scan_take_photo))
            }
            OutlinedButton(
                onClick = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                enabled = !working,
            ) {
                Text(stringResource(R.string.scan_pick_photo))
            }
            OutlinedButton(onClick = { pdfPicker.launch(arrayOf("application/pdf")) }, enabled = !working) {
                Text(stringResource(R.string.scan_open_pdf))
            }
        }
        when (state) {
            ScanState.Idle -> Unit

            ScanState.Working -> {
                Text(stringResource(R.string.scan_working))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.scan_cancel)) }
            }

            is ScanState.Failed -> Text(
                stringResource(R.string.scan_failed, state.reason),
                color = MaterialTheme.colorScheme.error,
            )

            is ScanState.Done -> ScanResult(state)
        }
    }
}

@Composable
private fun ScanResult(result: ScanState.Done) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (result.message == null) {
                Text(stringResource(R.string.scan_no_message), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.scan_no_message_hint), style = MaterialTheme.typography.bodyMedium)
            } else {
                Text(stringResource(R.string.scan_message_title), style = MaterialTheme.typography.labelLarge)
                SelectionContainer { Text(result.message, style = MaterialTheme.typography.titleLarge) }
            }
        }
    }
    Text(
        stringResource(
            R.string.scan_details,
            result.lines,
            result.symbols,
            result.xHeight,
            result.packetsDecoded,
            result.packetSlots,
            result.correctedBits,
            result.seconds,
        ),
        style = MaterialTheme.typography.bodySmall,
    )
}

/** Where the system camera puts the photo; the path is declared in `res/xml/file_paths.xml`. */
private fun photoUri(context: Context): Uri {
    val directory = File(context.cacheDir, "photos").apply { mkdirs() }
    return FileProvider.getUriForFile(context, "${context.packageName}.files", File(directory, "scan.jpg"))
}
