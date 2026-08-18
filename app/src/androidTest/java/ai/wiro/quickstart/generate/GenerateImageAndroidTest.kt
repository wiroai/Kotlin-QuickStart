package ai.wiro.quickstart.generate

import ai.wiro.quickstart.MainActivity
import ai.wiro.quickstart.core.credentials.CredentialsStore
import ai.wiro.quickstart.live.AppLiveCredentials
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/**
 * Compose UI automation for the example app.
 *
 * Offline flows always run. Live generation/cancel/kill require credentials.
 */
@RunWith(AndroidJUnit4::class)
class GenerateImageAndroidTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun clearCredentials() {
        val context =
            InstrumentationRegistry
                .getInstrumentation()
                .targetContext
        val store = CredentialsStore(context)
        store.apiKey = ""
        store.apiSecret = ""
        store.proxyUrlString = ""
        store.useProxy = false
    }

    @Test
    fun settingsAndProxyToggle() {
        composeRule.onNodeWithTag("open_settings").performClick()
        composeRule.onNodeWithTag("settings_sheet").assertIsDisplayed()
        composeRule.onNodeWithTag("security_warning").assertIsDisplayed()
        composeRule.onNodeWithTag("use_proxy_switch").performClick()
        composeRule.onNodeWithTag("proxy_url_field").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_done").performClick()
    }

    @Test
    fun emptyPromptAndMissingCredentials() {
        composeRule.onNodeWithTag("prompt_field").performTextClearance()
        composeRule.onNodeWithTag("generate_button").performClick()
        composeRule
            .onNodeWithText("Enter a prompt before generating.")
            .assertIsDisplayed()

        composeRule.onNodeWithTag("prompt_field").performTextInput("hi")
        composeRule.onNodeWithTag("generate_button").performClick()
        composeRule
            .onNodeWithText("Add an API key or proxy URL in Settings.")
            .assertIsDisplayed()
    }

    @Test
    fun secureCredentialPersistenceAfterRestart() {
        composeRule.onNodeWithTag("open_settings").performClick()
        composeRule
            .onNodeWithTag("api_key_field")
            .performTextInput("persist-key")
        composeRule
            .onNodeWithTag("api_secret_field")
            .performTextInput("persist-secret")
        composeRule.onNodeWithTag("settings_done").performClick()

        composeRule.activityRule.scenario.recreate()

        composeRule.onNodeWithTag("open_settings").performClick()
        val context =
            InstrumentationRegistry
                .getInstrumentation()
                .targetContext
        val store = CredentialsStore(context)
        assertEquals("persist-key", store.apiKey)
        assertEquals("persist-secret", store.apiSecret)
        composeRule.onNodeWithTag("settings_done").performClick()
    }

    @Test
    fun dimensionControls() {
        composeRule.onNodeWithTag("width_dropdown").performClick()
        composeRule.onNodeWithTag("width_option_512").performClick()
        composeRule.onNodeWithTag("height_dropdown").performClick()
        composeRule.onNodeWithTag("height_option_768").performClick()
        composeRule.onNodeWithTag("generate_screen").assertIsDisplayed()
    }

    @Test
    fun idleGenerateEnabled() {
        composeRule.onNodeWithTag("status_idle").assertIsDisplayed()
        composeRule.onNodeWithTag("generate_button").assertIsEnabled()
        composeRule
            .onAllNodesWithTag("cancel_button")
            .assertCountEquals(0)
    }

    @LargeTest
    @Test
    fun liveGenerationRendersOutput() {
        startLiveGeneration()
        composeRule.waitUntil(timeoutMillis = GENERATION_TIMEOUT_MS) {
            isPresent("status_succeeded") || isPresent("status_failed")
        }

        composeRule.onNodeWithTag("status_succeeded").assertIsDisplayed()
        composeRule.onNodeWithTag("output_image_0").assertIsDisplayed()
        composeRule.onNodeWithTag("generate_button").assertIsEnabled()
    }

    @LargeTest
    @Test
    fun liveLocalCancel() {
        startLiveGeneration()
        composeRule.waitUntil(timeoutMillis = RUNNING_TIMEOUT_MS) {
            isPresent("status_running")
        }

        composeRule.onNodeWithTag("cancel_button").assertIsDisplayed()
        composeRule.onNodeWithTag("cancel_button").performClick()
        composeRule.onNodeWithTag("cancel_local").assertIsDisplayed()
        composeRule.onNodeWithTag("cancel_local").performClick()

        awaitStopped()
    }

    @LargeTest
    @Test
    fun liveApiCancel() {
        startLiveGeneration()
        openCancelOptionsWithTaskIds()

        composeRule.onNodeWithTag("cancel_api").assertIsDisplayed()
        composeRule.onNodeWithTag("cancel_api").performClick()

        awaitStopped()
    }

    @LargeTest
    @Test
    fun liveApiKill() {
        startLiveGeneration()
        openCancelOptionsWithTaskIds()

        composeRule.onNodeWithTag("kill_api").assertIsDisplayed()
        composeRule.onNodeWithTag("kill_api").performClick()

        awaitStopped()
    }

    private fun startLiveGeneration() {
        AppLiveCredentials.assumeEnabled()
        val config = AppLiveCredentials.load()

        composeRule.onNodeWithTag("open_settings").performClick()
        if (config.useProxy) {
            composeRule.onNodeWithTag("use_proxy_switch").performClick()
            composeRule
                .onNodeWithTag("proxy_url_field")
                .performTextClearance()
            composeRule
                .onNodeWithTag("proxy_url_field")
                .performTextInput(config.proxyUrl!!)
        } else {
            composeRule
                .onNodeWithTag("api_key_field")
                .performTextClearance()
            composeRule
                .onNodeWithTag("api_key_field")
                .performTextInput(config.apiKey!!)
            if (!config.apiSecret.isNullOrBlank()) {
                composeRule
                    .onNodeWithTag("api_secret_field")
                    .performTextClearance()
                composeRule
                    .onNodeWithTag("api_secret_field")
                    .performTextInput(config.apiSecret)
            }
        }
        composeRule.onNodeWithTag("settings_done").performClick()

        composeRule.onNodeWithTag("prompt_field").performTextClearance()
        composeRule
            .onNodeWithTag("prompt_field")
            .performTextInput("compose live smoke square")
        composeRule.onNodeWithTag("width_dropdown").performClick()
        composeRule.onNodeWithTag("width_option_512").performClick()
        composeRule.onNodeWithTag("height_dropdown").performClick()
        composeRule.onNodeWithTag("height_option_512").performClick()
        composeRule.onNodeWithTag("generate_button").performClick()
    }

    /**
     * API cancel and kill stay hidden until a tracking update carries task
     * identifiers, so the dialog is opened and then observed until they appear.
     */
    private fun openCancelOptionsWithTaskIds() {
        composeRule.waitUntil(timeoutMillis = RUNNING_TIMEOUT_MS) {
            isPresent("status_running")
        }
        composeRule.onNodeWithTag("cancel_button").performClick()
        composeRule.waitUntil(timeoutMillis = TASK_ID_TIMEOUT_MS) {
            isPresent("cancel_api") && isPresent("kill_api")
        }
        composeRule.onNodeWithTag("cancel_local").assertIsDisplayed()
    }

    private fun awaitStopped() {
        composeRule.waitUntil(timeoutMillis = STOP_TIMEOUT_MS) {
            isPresent("status_failed")
        }
        composeRule.onNodeWithTag("generate_button").assertIsEnabled()
    }

    private fun isPresent(tag: String): Boolean = composeRule
        .onAllNodesWithTag(tag)
        .fetchSemanticsNodes()
        .isNotEmpty()

    private companion object {
        val GENERATION_TIMEOUT_MS = TimeUnit.MINUTES.toMillis(10)
        val RUNNING_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(30)
        val TASK_ID_TIMEOUT_MS = TimeUnit.MINUTES.toMillis(3)
        val STOP_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(30)
    }
}
