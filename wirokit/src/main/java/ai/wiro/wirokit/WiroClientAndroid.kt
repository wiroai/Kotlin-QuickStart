package ai.wiro.wirokit

import android.content.ContentResolver

/**
 * Android [ContentResolver] convenience overloads.
 *
 * Kept in a dedicated file so the JVM unit-test coverage gate can exclude
 * ContentResolver wiring while still covering [WiroUriContentSource] streaming
 * through injectable fakes.
 */
public suspend fun WiroClient.uploadFile(
    input: WiroFileInput.ContentUri,
    contentResolver: ContentResolver,
): WiroUploadResult = uploadFile(
    input = input,
    contentSource = WiroUriContentSource.from(contentResolver),
)

/**
 * Convenience [runModel] overload that opens content URIs through
 * [ContentResolver.openInputStream].
 */
public suspend fun WiroClient.runModel(
    modelId: WiroModelId,
    parameters: WiroJson = emptyMap(),
    callbackUrl: String? = null,
    contentResolver: ContentResolver,
): WiroRunResult = runModel(
    modelId = modelId,
    parameters = parameters,
    callbackUrl = callbackUrl,
    contentSource = WiroUriContentSource.from(contentResolver),
)
