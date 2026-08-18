package ai.wiro.quickstart.generate

import ai.wiro.quickstart.WiroQuickStartTheme
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [26])
class GenerateImageScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `renders idle status and settings entry`() {
        composeRule.setContent {
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

        composeRule.onNodeWithTag("status_idle").assertIsDisplayed()
        composeRule.onNodeWithTag("generate_button").assertIsEnabled()
        composeRule.onNodeWithTag("open_settings").assertIsDisplayed()
    }

    @Test
    fun `shows failed error message`() {
        composeRule.setContent {
            WiroQuickStartTheme {
                GenerateImageScreen(
                    state =
                    GenerateImageUiState(
                        generation =
                        GenerationState.Failed(
                            "Add an API key or proxy URL in Settings.",
                        ),
                    ),
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

        composeRule.onNodeWithTag("status_failed").assertIsDisplayed()
        composeRule
            .onNodeWithText(
                "Add an API key or proxy URL in Settings.",
            ).assertIsDisplayed()
    }

    @Test
    fun `running state disables generate and shows cancel`() {
        composeRule.setContent {
            WiroQuickStartTheme {
                GenerateImageScreen(
                    state =
                    GenerateImageUiState(
                        generation = GenerationState.Running("task_start"),
                        taskId = "1",
                        taskToken = "tok",
                    ),
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

        composeRule.onNodeWithTag("generate_button").assertIsNotEnabled()
        composeRule.onNodeWithTag("cancel_button").assertIsDisplayed()
        composeRule.onNodeWithTag("status_running").assertIsDisplayed()
    }

    @Test
    fun `cancel dialog shows api actions when identifiers exist`() {
        composeRule.setContent {
            WiroQuickStartTheme {
                GenerateImageScreen(
                    state =
                    GenerateImageUiState(
                        generation = GenerationState.Running("task_start"),
                        taskId = "1",
                        taskToken = "tok",
                        showCancelOptions = true,
                    ),
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

        composeRule.onNodeWithTag("cancel_local").assertIsDisplayed()
        composeRule.onNodeWithTag("cancel_api").assertIsDisplayed()
        composeRule.onNodeWithTag("kill_api").assertIsDisplayed()
    }

    @Test
    fun `settings sheet switches between direct and proxy fields`() {
        var useProxy = false
        composeRule.setContent {
            WiroQuickStartTheme {
                GenerateImageScreen(
                    state =
                    GenerateImageUiState(
                        showSettings = true,
                        useProxy = useProxy,
                    ),
                    onPromptChange = {},
                    onWidthChange = {},
                    onHeightChange = {},
                    onGenerate = {},
                    onRequestCancel = {},
                    onOpenSettings = {},
                    onDismissSettings = {},
                    onUseProxyChange = { useProxy = it },
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

        composeRule.onNodeWithTag("security_warning").assertIsDisplayed()
        composeRule.onNodeWithTag("api_key_field").assertIsDisplayed()
        composeRule.onNodeWithTag("use_proxy_switch").performClick()
    }

    @Test
    fun `prompt field accepts edits`() {
        var prompt = "old"
        composeRule.setContent {
            WiroQuickStartTheme {
                GenerateImageScreen(
                    state = GenerateImageUiState(prompt = prompt),
                    onPromptChange = { prompt = it },
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

        composeRule.onNodeWithTag("prompt_field").performTextClearance()
        composeRule
            .onNodeWithTag("prompt_field")
            .performTextInput("a calm lake")
    }
}
