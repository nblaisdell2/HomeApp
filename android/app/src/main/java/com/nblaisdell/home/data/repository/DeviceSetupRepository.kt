package com.nblaisdell.home.data.repository

import com.nblaisdell.home.data.remote.ApiException
import com.nblaisdell.home.data.remote.DeviceKeyStore
import com.nblaisdell.home.data.remote.bodyOrThrow
import com.nblaisdell.home.data.remote.generated.api.HealthApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import okhttp3.HttpUrl
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

sealed interface KeyCheck {
    /** The server accepted the key; [deviceName] is the name it was issued under. */
    data class Accepted(val deviceName: String) : KeyCheck
    data object Rejected : KeyCheck
    data object NotFormattedLikeAKey : KeyCheck
    data class Unreachable(val reason: String) : KeyCheck
}

/** Setting up this device's API key (HOME-63): save it, check it against the server, remove it. */
@Singleton
class DeviceSetupRepository @Inject constructor(
    private val keyStore: DeviceKeyStore,
    private val healthApi: HealthApi,
    baseUrl: HttpUrl,
) {
    val serverUrl: String = baseUrl.toString()
    val isSetUp: Flow<Boolean> = keyStore.apiKey.map { it != null }

    /** Saves [key] and checks it. A key the server rejects is not kept. */
    suspend fun saveKey(key: String): KeyCheck {
        val trimmed = key.trim()
        if (!DeviceKeyStore.looksValid(trimmed)) return KeyCheck.NotFormattedLikeAKey
        keyStore.save(trimmed)
        val check = checkKey()
        if (check is KeyCheck.Rejected) keyStore.clear()
        return check
    }

    /**
     * For `adb shell am start ... --es apiKey <key>`. Only fills in a missing key, so another app
     * on the device can't replace a working key by sending the same intent.
     */
    suspend fun provisionIfMissing(key: String): KeyCheck? =
        if (keyStore.apiKey.first() == null) saveKey(key) else null

    suspend fun checkKey(): KeyCheck = try {
        KeyCheck.Accepted(healthApi.getAuthenticatedHealth().bodyOrThrow().device)
    } catch (e: CancellationException) {
        throw e
    } catch (e: ApiException) {
        if (e.isUnauthorized) KeyCheck.Rejected else KeyCheck.Unreachable("Server error ${e.code}")
    } catch (e: IOException) {
        KeyCheck.Unreachable(e.message ?: "No connection")
    }

    suspend fun removeKey() = keyStore.clear()
}
