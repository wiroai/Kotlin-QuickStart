package ai.wiro.quickstart

import ai.wiro.quickstart.core.credentials.CredentialsStore
import ai.wiro.quickstart.core.credentials.InMemoryCredentialsStore
import ai.wiro.quickstart.generate.GenerateImageScreen
import ai.wiro.quickstart.generate.GenerateImageUiState
import ai.wiro.quickstart.generate.GenerateImageViewModel
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val credentials = CredentialsStore(applicationContext)
        setContent {
            WiroQuickStartTheme {
                val viewModel: GenerateImageViewModel = viewModel(
                    factory = GenerateImageViewModel.factory(credentials),
                )
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                GenerateImageRoute(
                    state = state,
                    viewModel = viewModel,
                )
            }
        }
    }
}

@Composable
private fun GenerateImageRoute(
    state: GenerateImageUiState,
    viewModel: GenerateImageViewModel,
) {
    GenerateImageScreen(
        state = state,
        onPromptChange = viewModel::onPromptChange,
        onWidthChange = viewModel::onWidthChange,
        onHeightChange = viewModel::onHeightChange,
        onGenerate = viewModel::generate,
        onRequestCancel = viewModel::requestCancelOptions,
        onOpenSettings = viewModel::openSettings,
        onDismissSettings = viewModel::dismissSettings,
        onUseProxyChange = viewModel::onUseProxyChange,
        onApiKeyChange = viewModel::onApiKeyChange,
        onApiSecretChange = viewModel::onApiSecretChange,
        onProxyUrlChange = viewModel::onProxyUrlChange,
        onDismissCancelOptions = viewModel::dismissCancelOptions,
        onCancelLocal = viewModel::cancelLocal,
        onCancelRemote = viewModel::cancelRemote,
        onKillRemote = viewModel::killRemote,
    )
}

@Composable
fun WiroQuickStartTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(),
        content = content,
    )
}

@Preview(showBackground = true)
@Composable
@Suppress("UnusedPrivateFunction")
private fun GenerateImagePreview() {
    WiroQuickStartTheme {
        GenerateImageScreen(
            state = GenerateImageUiState(),
            onPromptChange = {},
            onWidthChange = {},
            onHeightChange = {},
            onGenerate = {},
            onRequestCancel = {},
            onOpenSettings = {},
            onDismissSettings = {},
            onUseProxyChange = {},
            onApiKeyChange = {},
            onApiSecretChange = {},
            onProxyUrlChange = {},
            onDismissCancelOptions = {},
            onCancelLocal = {},
            onCancelRemote = {},
            onKillRemote = {},
        )
    }
}

@Suppress("unused")
private val previewCredentials = InMemoryCredentialsStore()
