package ai.wiro.quickstart.generate

/**
 * Immutable screen state for the generate-image demo.
 *
 * Credentials are never stored here — only in [CredentialsRepository].
 */
public data class GenerateImageUiState(
    public val prompt: String =
        "A cinematic mountain lake at sunrise",
    public val width: Int = 1024,
    public val height: Int = 1024,
    public val generation: GenerationState = GenerationState.Idle,
    public val taskId: String? = null,
    public val taskToken: String? = null,
    public val showCancelOptions: Boolean = false,
    public val showSettings: Boolean = false,
    public val allowsDirectCredentials: Boolean = true,
    public val useProxy: Boolean = false,
    public val apiKey: String = "",
    public val apiSecret: String = "",
    public val proxyUrlString: String = "",
) {
    public val isRunning: Boolean
        get() = generation is GenerationState.Running

    public val canCancelViaApi: Boolean
        get() = taskId != null

    public val canKillViaApi: Boolean
        get() = taskToken != null || taskId != null
}
