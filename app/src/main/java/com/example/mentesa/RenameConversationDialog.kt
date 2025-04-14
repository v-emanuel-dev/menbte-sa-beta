package com.example.mentesa

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.DialogProperties

@Composable
fun RenameConversationDialog(
    conversationId: Long,
    currentTitle: String?,
    onConfirm: (id: Long, newTitle: String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var newTitleText by remember(currentTitle) { mutableStateOf(currentTitle ?: "") }
    val isConfirmEnabled by remember(newTitleText) {
        derivedStateOf { newTitleText.isNotBlank() }
    }

    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rename_conversation_dialog_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = newTitleText,
                    onValueChange = { newTitleText = it },
                    label = { Text(stringResource(R.string.new_conversation_name_label)) },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(conversationId, newTitleText.trim())
                },
                enabled = isConfirmEnabled
            ) {
                Text(stringResource(R.string.save_button))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel_button))
            }
        },
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)
    )
}