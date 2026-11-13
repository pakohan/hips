package com.pakohan.hips.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.pakohan.hips.R
import com.pakohan.hips.ScanState
import com.pakohan.hips.ui.theme.BrandAmber
import com.pakohan.hips.ui.theme.BrandNavy
import com.pakohan.hips.ui.theme.BrandNavyDeep
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
    // Resolved on first use: the file provider does not exist where previews are drawn.
    val photoUri = remember { lazy { photoUri(context) } }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        if (taken) onScanPhoto(photoUri.value)
    }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { picked ->
        if (picked != null) onScanPhoto(picked)
    }
    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { picked ->
        if (picked != null) onScanPdf(picked)
    }
    val working = state is ScanState.Working

    Column(
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.scan_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                stringResource(R.string.scan_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ActionTile(
            icon = R.drawable.ic_camera,
            label = stringResource(R.string.scan_take_photo),
            onClick = { camera.launch(photoUri.value) },
            enabled = !working,
            emphasized = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ActionTile(
                icon = R.drawable.ic_image,
                label = stringResource(R.string.scan_pick_photo),
                onClick = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                enabled = !working,
                modifier = Modifier.weight(1f),
            )
            ActionTile(
                icon = R.drawable.ic_document,
                label = stringResource(R.string.scan_open_pdf),
                onClick = { pdfPicker.launch(arrayOf("application/pdf")) },
                enabled = !working,
                modifier = Modifier.weight(1f),
            )
        }
        when (state) {
            ScanState.Idle -> Unit
            ScanState.Working -> Working(onCancel)
            is ScanState.Failed -> Notice(stringResource(R.string.scan_failed, state.reason), isError = true)
            is ScanState.Done -> ScanResult(state)
        }
    }
}

@Composable
private fun Working(onCancel: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(stringResource(R.string.scan_working), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().clip(CircleShape),
                color = BrandAmber,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.scan_cancel)) }
        }
    }
}

@Composable
private fun ScanResult(result: ScanState.Done) {
    if (result.message == null) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.scan_no_message), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(R.string.scan_no_message_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    } else {
        // The revealed message wears the icon: amber on navy, in both themes.
        Surface(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = BrandNavy, contentColor = Color.White) {
            Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(color = BrandAmber, contentColor = BrandNavyDeep, shape = CircleShape) {
                    Text(
                        stringResource(R.string.scan_message_title),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                    )
                }
                SelectionContainer {
                    Text(result.message, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        StatPill(stringResource(R.string.stat_packets, result.packetsDecoded, result.packetSlots))
        StatPill(stringResource(R.string.stat_corrected, result.correctedBits))
        StatPill(stringResource(R.string.stat_lines, result.lines, result.symbols))
        StatPill(stringResource(R.string.stat_letter_height, result.xHeight))
        StatPill(stringResource(R.string.stat_seconds, result.seconds))
    }
}

/** Where the system camera puts the photo; the path is declared in `res/xml/file_paths.xml`. */
private fun photoUri(context: Context): Uri {
    val directory = File(context.cacheDir, "photos").apply { mkdirs() }
    return FileProvider.getUriForFile(context, "${context.packageName}.files", File(directory, "scan.jpg"))
}
