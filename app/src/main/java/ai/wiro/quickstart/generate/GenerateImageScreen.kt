package ai.wiro.quickstart.generate

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun GenerateImageScreen(
    state: GenerateImageUiState,
    onPromptChange: (String) -> Unit,
    onWidthChange: (Int) -> Unit,
    onHeightChange: (Int) -> Unit,
    onGenerate: () -> Unit,
    onRequestCancel: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismissSettings: () -> Unit,
    onUseProxyChange: (Boolean) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onApiSecretChange: (String) -> Unit,
    onProxyUrlChange: (String) -> Unit,
    onDismissCancelOptions: () -> Unit,
    onCancelLocal: () -> Unit,
    onCancelRemote: () -> Unit,
    onKillRemote: () -> Unit,
) {
    Scaffold(
        modifier = Modifier.testTag("generate_screen"),
        topBar = {
            TopAppBar(
                title = { Text("Generate Image") },
                actions = {
                    TextButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.testTag("open_settings"),
                    ) {
                        Text("Settings")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Prompt",
                style = MaterialTheme.typography.titleMedium,
            )
            OutlinedTextField(
                value = state.prompt,
                onValueChange = onPromptChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("prompt_field"),
                minLines = 3,
                maxLines = 6,
                placeholder = { Text("Describe an image") },
                enabled = !state.isRunning,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                DimensionDropdown(
                    label = "Width",
                    value = state.width,
                    enabled = !state.isRunning,
                    onValueChange = onWidthChange,
                    optionTagPrefix = "width",
                    modifier = Modifier
                        .weight(1f)
                        .testTag("width_dropdown"),
                )
                DimensionDropdown(
                    label = "Height",
                    value = state.height,
                    enabled = !state.isRunning,
                    onValueChange = onHeightChange,
                    optionTagPrefix = "height",
                    modifier = Modifier
                        .weight(1f)
                        .testTag("height_dropdown"),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = onGenerate,
                    enabled = !state.isRunning,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("generate_button"),
                ) {
                    Text(
                        if (state.isRunning) {
                            "Generating…"
                        } else {
                            "Generate"
                        },
                    )
                }
                if (state.isRunning) {
                    OutlinedButton(
                        onClick = onRequestCancel,
                        modifier = Modifier.testTag("cancel_button"),
                    ) {
                        Text("Cancel")
                    }
                }
            }

            StatusSection(state.generation)

            if (state.generation is GenerationState.Succeeded) {
                Text(
                    text = "Outputs",
                    style = MaterialTheme.typography.titleMedium,
                )
                state.generation.outputs.forEachIndexed { index, url ->
                    AsyncImage(
                        model = url,
                        contentDescription = "Generated image $index",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp)
                            .testTag("output_image_$index"),
                        contentScale = ContentScale.Fit,
                    )
                }
            }
        }
    }

    if (state.showSettings) {
        SettingsSheet(
            state = state,
            onDismiss = onDismissSettings,
            onUseProxyChange = onUseProxyChange,
            onApiKeyChange = onApiKeyChange,
            onApiSecretChange = onApiSecretChange,
            onProxyUrlChange = onProxyUrlChange,
        )
    }

    if (state.showCancelOptions) {
        CancelOptionsDialog(
            state = state,
            onDismiss = onDismissCancelOptions,
            onCancelLocal = onCancelLocal,
            onCancelRemote = onCancelRemote,
            onKillRemote = onKillRemote,
        )
    }
}

@Composable
private fun StatusSection(generation: GenerationState) {
    when (generation) {
        is GenerationState.Idle -> {
            Text(
                text = "Ready. Enter a prompt and tap Generate.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("status_idle"),
            )
        }

        is GenerationState.Running -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.testTag("status_running"),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                Text(
                    text = "Status: ${generation.status}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        is GenerationState.Succeeded -> {
            Text(
                text = "Done",
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag("status_succeeded"),
            )
        }

        is GenerationState.Failed -> {
            Text(
                text = generation.message,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("status_failed"),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DimensionDropdown(
    label: String,
    value: Int,
    enabled: Boolean,
    onValueChange: (Int) -> Unit,
    optionTagPrefix: String,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = value.toString(),
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(
                    expanded = expanded,
                )
            },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            GenerateImageViewModel.DIMENSION_CHOICES.forEach { choice ->
                DropdownMenuItem(
                    text = { Text(choice.toString()) },
                    onClick = {
                        onValueChange(choice)
                        expanded = false
                    },
                    modifier = Modifier.testTag(
                        "${optionTagPrefix}_option_$choice",
                    ),
                )
            }
        }
    }
}
