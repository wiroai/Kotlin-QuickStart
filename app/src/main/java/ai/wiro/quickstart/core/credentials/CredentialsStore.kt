package ai.wiro.quickstart.core.credentials

import ai.wiro.quickstart.BuildConfig
import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Keystore-backed encrypted storage for development credentials.
 *
 * Does not ship with default API keys or secrets.
 */
public class CredentialsStore private constructor(
    private val prefs: SharedPreferences,
) : CredentialsRepository {
    public constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE,
        ),
    )

    override val allowsDirectCredentials: Boolean
        get() = BuildConfig.ALLOW_DIRECT_CREDENTIALS

    override var apiKey: String
        get() = if (allowsDirectCredentials) readEncrypted(KEY_API_KEY) else ""
        set(value) {
            writeEncrypted(
                KEY_API_KEY,
                value.takeIf { allowsDirectCredentials }.orEmpty(),
            )
        }

    override var apiSecret: String
        get() = if (allowsDirectCredentials) {
            readEncrypted(KEY_API_SECRET)
        } else {
            ""
        }
        set(value) {
            writeEncrypted(
                KEY_API_SECRET,
                value.takeIf { allowsDirectCredentials }.orEmpty(),
            )
        }

    override var proxyUrlString: String
        get() = readEncrypted(KEY_PROXY_URL)
        set(value) {
            writeEncrypted(KEY_PROXY_URL, value)
        }

    override var useProxy: Boolean
        get() = !allowsDirectCredentials ||
            readEncrypted(KEY_USE_PROXY).toBooleanStrictOrNull() == true
        set(value) {
            writeEncrypted(
                KEY_USE_PROXY,
                (value || !allowsDirectCredentials).toString(),
            )
        }

    private fun readEncrypted(key: String): String {
        val encoded = prefs.getString(key, null) ?: return ""
        return runCatching {
            val payload = Base64.decode(encoded, Base64.NO_WRAP)
            require(
                payload.size > VERSION_SIZE + GCM_IV_SIZE_BYTES,
            )
            require(payload.first() == PAYLOAD_VERSION)

            val ivStart = VERSION_SIZE
            val ciphertextStart = ivStart + GCM_IV_SIZE_BYTES
            val iv = payload.copyOfRange(ivStart, ciphertextStart)
            val ciphertext =
                payload.copyOfRange(ciphertextStart, payload.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateSecretKey(),
                javax.crypto.spec.GCMParameterSpec(GCM_TAG_SIZE_BITS, iv),
            )
            cipher.updateAAD(key.toByteArray(Charsets.UTF_8))
            cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
        }.getOrElse {
            prefs.edit().remove(key).apply()
            ""
        }
    }

    private fun writeEncrypted(
        key: String,
        value: String,
    ) {
        if (value.isEmpty()) {
            prefs.edit().remove(key).apply()
            return
        }

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        cipher.updateAAD(key.toByteArray(Charsets.UTF_8))
        val ciphertext =
            cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val payload =
            byteArrayOf(PAYLOAD_VERSION) + cipher.iv + ciphertext
        prefs
            .edit()
            .putString(
                key,
                Base64.encodeToString(payload, Base64.NO_WRAP),
            ).apply()
    }

    public companion object {
        private const val PREFS_NAME = "wiro.example.credentials"
        private const val KEY_API_KEY = "wiro.example.apiKey"
        private const val KEY_API_SECRET = "wiro.example.apiSecret"
        private const val KEY_PROXY_URL = "wiro.example.proxyURL"
        private const val KEY_USE_PROXY = "wiro.example.useProxy"
        private const val KEY_ALIAS = "wiro.example.credentials.key"
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_SIZE_BYTES = 12
        private const val GCM_TAG_SIZE_BITS = 128
        private const val VERSION_SIZE = 1
        private const val PAYLOAD_VERSION: Byte = 1
        private val keyLock = Any()

        private fun getOrCreateSecretKey(): SecretKey = synchronized(keyLock) {
            val keyStore =
                KeyStore.getInstance(KEYSTORE_PROVIDER).apply {
                    load(null)
                }
            val existing =
                keyStore.getKey(KEY_ALIAS, null) as? SecretKey
            existing ?: createSecretKey()
        }

        private fun createSecretKey(): SecretKey {
            val generator =
                KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES,
                    KEYSTORE_PROVIDER,
                )
            generator.init(
                KeyGenParameterSpec
                    .Builder(
                        KEY_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT or
                            KeyProperties.PURPOSE_DECRYPT,
                    ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(
                        KeyProperties.ENCRYPTION_PADDING_NONE,
                    ).setRandomizedEncryptionRequired(true)
                    .build(),
            )
            return generator.generateKey()
        }
    }
}

/**
 * In-memory credentials for unit tests.
 */
public class InMemoryCredentialsStore(
    override val allowsDirectCredentials: Boolean = true,
    override var apiKey: String = "",
    override var apiSecret: String = "",
    override var proxyUrlString: String = "",
    override var useProxy: Boolean = false,
) : CredentialsRepository
