package ai.wiro.quickstart.generate

/**
 * Sealed UI state for the generate-image demo.
 */
public sealed class GenerationState {
    public data object Idle : GenerationState()

    public data class Running(
        public val status: String,
    ) : GenerationState()

    public data class Succeeded(
        public val outputs: List<String>,
    ) : GenerationState()

    public data class Failed(
        public val message: String,
    ) : GenerationState()
}
