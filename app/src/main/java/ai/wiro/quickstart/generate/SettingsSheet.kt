package ai.wiro.quickstart.generate

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun SettingsSheet(
    state: GenerateImageUiState,
    onDismiss: () -> Unit,
    onUseProxyChange: (Boolean) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onApiSecretChange: (String) -> Unit,
    onProxyUrlChange: (String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .testTag("settings_sheet"),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text =
                if (state.allowsDirectCredentials) {
                    "Direct credentials are available for local " +
                        "development. Release builds require a backend " +
                        "proxy."
                } else {
                    "Release builds require a backend proxy that " +
                        "attaches Wiro credentials server-side."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("security_warning"),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Use proxy URL")
                Switch(
                    checked = state.useProxy,
                    onCheckedChange = onUseProxyChange,
                    enabled = state.allowsDirectCredentials,
                    modifier = Modifier.testTag("use_proxy_switch"),
                )
            }
            if (state.useProxy) {
                OutlinedTextField(
                    value = state.proxyUrlString,
                    onValueChange = onProxyUrlChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("proxy_url_field"),
                    label = { Text("Proxy URL") },
                    placeholder = {
                        Text("https://api.myapp.com/wiro/v1")
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                    ),
                )
            } else {
                OutlinedTextField(
                    value = state.apiKey,
                    onValueChange = onApiKeyChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("api_key_field"),
                    label = { Text("API key") },
                    singleLine = true,
                    visualTransformation =
                    PasswordVisualTransformation(),
                )
                OutlinedTextField(
                    value = state.apiSecret,
                    onValueChange = onApiSecretChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("api_secret_field"),
                    label = { Text("API secret (optional)") },
                    singleLine = true,
                    visualTransformation =
                    PasswordVisualTransformation(),
                )
            }
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.End)
                    .testTag("settings_done"),
            ) {
                Text("Done")
            }
        }
    }
}

@Composable
public fun CancelOptionsDialog(
    state: GenerateImageUiState,
    onDismiss: () -> Unit,
    onCancelLocal: () -> Unit,
    onCancelRemote: () -> Unit,
    onKillRemote: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Stop generation") },
        text = {
            Text(
                "Local cancel stops the stream immediately. " +
                    "API cancel/kill asks Wiro to stop the remote task " +
                    "when task identifiers are available.",
            )
        },
        confirmButton = {
            Column {
                TextButton(
                    onClick = onCancelLocal,
                    modifier = Modifier.testTag("cancel_local"),
                ) {
                    Text("Cancel local Task")
                }
                if (state.canCancelViaApi) {
                    TextButton(
                        onClick = onCancelRemote,
                        modifier = Modifier.testTag("cancel_api"),
                    ) {
                        Text("Cancel via API")
                    }
                }
                if (state.canKillViaApi) {
                    TextButton(
                        onClick = onKillRemote,
                        modifier = Modifier.testTag("kill_api"),
                    ) {
                        Text("Kill via API")
                    }
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("cancel_dismiss"),
                ) {
                    Text("Dismiss")
                }
            }
        },
        dismissButton = {},
    )
}
