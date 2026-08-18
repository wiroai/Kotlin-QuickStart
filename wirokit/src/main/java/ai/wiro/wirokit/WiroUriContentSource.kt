package ai.wiro.wirokit

import android.content.ContentResolver
import android.net.Uri
import java.io.InputStream

/**
 * Opens Android content or file URIs as input streams for uploads.
 */
public fun interface WiroUriContentSource {
    public fun openInputStream(uri: Uri): InputStream?

    public companion object {
        public fun from(contentResolver: ContentResolver): WiroUriContentSource = WiroUriContentSource { uri ->
            contentResolver.openInputStream(uri)
        }
    }
}
