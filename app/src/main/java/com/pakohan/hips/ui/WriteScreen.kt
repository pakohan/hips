package com.pakohan.hips.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pakohan.hips.R
import com.pakohan.hips.WriteState
import com.pakohan.hips.pdf.ComposeProblem
import com.pakohan.hips.ui.theme.BrandAmber

@Composable
fun WriteScreen(
    state: WriteState,
    canWrite: (text: String, message: String) -> Boolean,
    placeholderText: (message: String) -> String?,
    onWrite: (text: String, message: String, target: Uri) -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by rememberSaveable { mutableStateOf("") }
    var message by rememberSaveable { mutableStateOf("") }
    val saveAs = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { target ->
        if (target != null) onWrite(text, message, target)
    }
    val fileName = stringResource(R.string.write_file_name)
    val working = state is WriteState.Working

    Column(
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.write_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                stringResource(R.string.write_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        StepCard(number = 1, title = stringResource(R.string.write_message_label)) {
            OutlinedTextField(
                value = message,
                onValueChange = { message = it },
                placeholder = { Text(stringResource(R.string.write_message_hint)) },
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        StepCard(number = 2, title = stringResource(R.string.write_text_label)) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(stringResource(R.string.write_text_hint)) },
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp),
            )
            TextButton(
                onClick = { placeholderText(message)?.let { text = it } },
                enabled = !working && message.isNotEmpty(),
            ) {
                Text(stringResource(R.string.write_placeholder))
            }
        }
        AmberButton(
            onClick = { if (canWrite(text, message)) saveAs.launch(fileName) },
            enabled = !working && text.isNotBlank() && message.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(painterResource(R.drawable.ic_document), contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.write_create), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        when (state) {
            WriteState.Idle -> Unit

            WriteState.Working -> {
                Text(stringResource(R.string.write_working), style = MaterialTheme.typography.bodyMedium)
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().clip(CircleShape),
                    color = BrandAmber,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }

            is WriteState.Done -> Notice(
                stringResource(R.string.write_done, state.pages, state.packetsNeeded, state.packetSlots / state.packetsNeeded),
                isError = false,
            )

            is WriteState.Refused -> Notice(problemText(state.problem), isError = true)
            is WriteState.Failed -> Notice(stringResource(R.string.write_failed, state.reason), isError = true)
        }
    }
}

@Composable
private fun problemText(problem: ComposeProblem): String =
    when (problem) {
        is ComposeProblem.UnsupportedCharacters ->
            stringResource(R.string.write_unsupported, problem.characters.joinToString(" "))

        ComposeProblem.MessageTooLong -> stringResource(R.string.write_message_too_long)

        is ComposeProblem.TextTooShort ->
            stringResource(R.string.write_text_too_short, problem.packetsNeeded, problem.packetSlots)
    }
