package ai.wiro.quickstart.generate

import ai.wiro.wirokit.WiroModelRequest
import ai.wiro.wirokit.WiroTaskId
import ai.wiro.wirokit.WiroTaskToken
import ai.wiro.wirokit.WiroTaskUpdate
import kotlinx.coroutines.flow.Flow

/**
 * Narrow session surface used by the example ViewModel.
 *
 * Production wiring wraps [ai.wiro.wirokit.WiroClient]; tests inject fakes.
 */
public interface WiroSession : AutoCloseable {
    public suspend fun subscribeStream(
        request: WiroModelRequest,
    ): Flow<WiroTaskUpdate>

    public suspend fun cancelTask(id: WiroTaskId): Boolean

    public suspend fun killTask(token: WiroTaskToken): Boolean

    public suspend fun killTask(id: WiroTaskId): Boolean
}
