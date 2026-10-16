package br.com.ryuukossei.gymprogress.data.settings

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class SettingsState(
    val baseUrl: String,
    val hasToken: Boolean,
    val lastImportAt: Long?,
)

/**
 * Configurações do app. O token de acesso à API é criptografado com uma chave
 * AES guardada no Android Keystore (a chave nunca sai do aparelho).
 */
class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences("gymprogress_settings", Context.MODE_PRIVATE)
    private val securePrefs = context.getSharedPreferences("gymprogress_secure", Context.MODE_PRIVATE)
    private val cipher = TokenCipher()

    private val _state = MutableStateFlow(loadState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    fun token(): String? {
        val stored = securePrefs.getString(KEY_TOKEN, null) ?: return null
        return cipher.decrypt(stored)
    }

    fun saveConnection(baseUrl: String, token: String) {
        prefs.edit { putString(KEY_BASE_URL, baseUrl) }
        securePrefs.edit { putString(KEY_TOKEN, cipher.encrypt(token)) }
        _state.value = loadState()
    }

    fun clearToken() {
        securePrefs.edit { remove(KEY_TOKEN) }
        _state.value = loadState()
    }

    fun setLastImportAt(epochMillis: Long) {
        prefs.edit { putLong(KEY_LAST_IMPORT, epochMillis) }
        _state.value = loadState()
    }

    private fun loadState() = SettingsState(
        baseUrl = prefs.getString(KEY_BASE_URL, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL,
        // Se o token não puder ser decifrado (ex.: chave perdida), o app pede o token de novo.
        hasToken = token() != null,
        lastImportAt = prefs.getLong(KEY_LAST_IMPORT, 0L).takeIf { it > 0L },
    )

    companion object {
        const val DEFAULT_BASE_URL = "https://api.ryuukossei.com.br"
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_LAST_IMPORT = "last_import_at"
        private const val KEY_TOKEN = "api_token"
    }
}

/** AES/GCM com chave no Android Keystore. Formato salvo: base64(iv):base64(texto cifrado). */
private class TokenCipher {

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    fun encrypt(plain: String): String {
        val c = Cipher.getInstance(TRANSFORMATION)
        c.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = c.doFinal(plain.toByteArray(Charsets.UTF_8))
        return b64(c.iv) + ":" + b64(encrypted)
    }

    fun decrypt(stored: String): String? = try {
        val (iv, data) = stored.split(":", limit = 2)
        val c = Cipher.getInstance(TRANSFORMATION)
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, unb64(iv)))
        String(c.doFinal(unb64(data)), Charsets.UTF_8)
    } catch (e: Exception) {
        null
    }

    private fun b64(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun unb64(text: String) = Base64.decode(text, Base64.NO_WRAP)

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "gymprogress_api_token"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
