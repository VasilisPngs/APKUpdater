package com.android.gupdater.ui

import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.android.gupdater.R
import com.android.gupdater.data.model.InstalledApp

@Composable
fun ManualUpdateDialog(
    app: InstalledApp,
    onDismiss: () -> Unit,
    onConfirm: (versionCode: Long, email: String, aasToken: String) -> Unit
) {
    var versionCodeText by remember(app.packageName) { mutableStateOf("") }
    var email by remember(app.packageName) { mutableStateOf("") }
    var aasToken by remember(app.packageName) { mutableStateOf("") }
    val versionCode = versionCodeText.toLongOrNull()
    val isValid = versionCode != null && versionCode > app.versionCode && email.isNotBlank() && aasToken.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.manual_update)) },
        text = {
            Column {
                Text(app.appName, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 2.dp, bottom = 16.dp)
                )
                OutlinedTextField(
                    value = versionCodeText,
                    onValueChange = { value ->
                        if (value.length <= 20 && value.all(Char::isDigit)) versionCodeText = value
                    },
                    label = { Text(stringResource(R.string.version_code)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = versionCodeText.isNotEmpty() && (versionCode == null || versionCode <= app.versionCode),
                    supportingText = {
                        if (versionCodeText.isNotEmpty() && (versionCode == null || versionCode <= app.versionCode)) {
                            Text(stringResource(R.string.enter_newer_version_code, app.versionCode))
                        }
                    }
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text(stringResource(R.string.google_play_email)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.padding(top = 12.dp)
                )
                OutlinedTextField(
                    value = aasToken,
                    onValueChange = { aasToken = it },
                    label = { Text(stringResource(R.string.google_play_aas_token)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.padding(top = 12.dp)
                )
                Text(
                    text = stringResource(R.string.google_play_aas_token_help),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        },
        confirmButton = {
            FilledTonalButton(
                onClick = { onConfirm(versionCode!!, email.trim(), aasToken.trim()) },
                enabled = isValid,
                shape = MaterialTheme.shapes.extraLarge
            ) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
