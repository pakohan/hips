package com.pakohan.hips.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pakohan.hips.R
import com.pakohan.hips.WriteState
import com.pakohan.hips.pdf.ComposeProblem

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
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text(stringResource(R.string.write_text_label)) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp),
        )
        OutlinedTextField(
            value = message,
            onValueChange = { message = it },
            label = { Text(stringResource(R.string.write_message_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = { if (canWrite(text, message)) saveAs.launch(fileName) },
                enabled = !working && text.isNotBlank() && message.isNotEmpty(),
            ) {
                Text(stringResource(R.string.write_create))
            }
            OutlinedButton(
                onClick = { placeholderText(message)?.let { text = it } },
                enabled = !working && message.isNotEmpty(),
            ) {
                Text(stringResource(R.string.write_placeholder))
            }
        }
        when (state) {
            WriteState.Idle -> Unit

            WriteState.Working -> {
                Text(stringResource(R.string.write_working))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            is WriteState.Done -> Text(
                stringResource(R.string.write_done, state.pages, state.packetsNeeded, state.packetSlots / state.packetsNeeded),
            )

            is WriteState.Refused -> Text(problemText(state.problem), color = MaterialTheme.colorScheme.error)

            is WriteState.Failed -> Text(
                stringResource(R.string.write_failed, state.reason),
                color = MaterialTheme.colorScheme.error,
            )
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
