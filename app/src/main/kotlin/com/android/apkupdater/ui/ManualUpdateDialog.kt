package com.android.apkupdater.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.android.apkupdater.data.model.InstalledApp

@Composable
fun ManualUpdateDialog(
    app: InstalledApp,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    var versionCodeText by remember(app.packageName) { mutableStateOf("") }
    val versionCode = versionCodeText.toLongOrNull()
    val isValid = versionCode != null && versionCode > 0L

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Manual update") },
        text = {
            Column {
                Text(
                    text = app.appName,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 2.dp, bottom = 16.dp)
                )
                OutlinedTextField(
                    value = versionCodeText,
                    onValueChange = { value ->
                        if (value.length <= 20 && value.all(Char::isDigit)) {
                            versionCodeText = value
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Version code") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = versionCodeText.isNotEmpty() && !isValid,
                    supportingText = {
                        if (versionCodeText.isNotEmpty() && !isValid) {
                            Text("Enter a valid version code")
                        }
                    }
                )
            }
        },
        confirmButton = {
            FilledTonalButton(
                onClick = { onConfirm(versionCode!!) },
                enabled = isValid,
                shape = MaterialTheme.shapes.extraLarge
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
