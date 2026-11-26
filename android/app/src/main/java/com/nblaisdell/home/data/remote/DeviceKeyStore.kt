package com.nblaisdell.home.data.remote

import android.content.Context
import android.util.Base64
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds this device's API key (HOME-63). The key is encrypted with AES-256-GCM (Tink) using a
 * master key that lives in the Android Keystore, and the ciphertext is stored in DataStore.
 * There is no sign-in: the key is issued on the server with `npm run issue-key` and entered
 * once in Settings (or sent with `adb`, see README).
 */
@Singleton
class DeviceKeyStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: DataStore<Preferences>,
) {
    private val aead: Aead by lazy {
        AeadConfig.register()
        AndroidKeysetManager.Builder()
            .withSharedPref(context, KEYSET_NAME, KEYSET_PREFS)
            .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
            .withMasterKeyUri(MASTER_KEY_URI)
            .build()
            .keysetHandle
            .getPrimitive(RegistryConfiguration.get(), Aead::class.java)
    }

    @Volatile private var cached: CachedKey? = null

    /** The current key, or null when this device hasn't been set up. */
    val apiKey: Flow<String?> = dataStore.data
        .map { prefs -> prefs[ENCRYPTED_KEY]?.let(::decrypt) }
        .distinctUntilChanged()

    /** For OkHttp interceptors, which run on background threads and can't suspend. */
    fun currentKeyBlocking(): String? =
        cached?.value ?: runBlocking { apiKey.first() }.also { cached = CachedKey(it) }

    suspend fun save(key: String) {
        val encrypted = Base64.encodeToString(aead.encrypt(key.toByteArray(), AAD), Base64.NO_WRAP)
        dataStore.edit { it[ENCRYPTED_KEY] = encrypted }
        cached = CachedKey(key)
    }

    suspend fun clear() {
        dataStore.edit { it.remove(ENCRYPTED_KEY) }
        cached = CachedKey(null)
    }

    private fun decrypt(encoded: String): String? = try {
        String(aead.decrypt(Base64.decode(encoded, Base64.NO_WRAP), AAD))
    } catch (e: Exception) {
        // E.g. the Keystore key was lost after a backup restore. Treat as "not set up".
        Log.w(TAG, "Could not decrypt the stored API key", e)
        null
    }

    private data class CachedKey(val value: String?)

    companion object {
        private const val TAG = "DeviceKeyStore"
        private const val KEYSET_NAME = "home_device_key_keyset"
        private const val KEYSET_PREFS = "home_device_key_keyset_prefs"
        private const val MASTER_KEY_URI = "android-keystore://home_device_key_master"
        private val AAD = "device_api_key".toByteArray()
        private val ENCRYPTED_KEY = stringPreferencesKey("device_api_key_encrypted")

        /** Keys issued by the API look like `home_<43 base64url chars>`. */
        fun looksValid(key: String) = Regex("^home_[A-Za-z0-9_-]{20,}$").matches(key.trim())
    }
}
