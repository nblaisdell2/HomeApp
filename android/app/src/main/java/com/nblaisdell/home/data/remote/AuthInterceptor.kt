package com.nblaisdell.home.data.remote

import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

/** Adds `Authorization: Bearer <device key>` to every API request once the device is set up. */
class AuthInterceptor @Inject constructor(
    private val keyStore: DeviceKeyStore,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val key = keyStore.currentKeyBlocking()
        val request = if (key != null) {
            chain.request().newBuilder().header("Authorization", "Bearer $key").build()
        } else {
            chain.request()
        }
        return chain.proceed(request)
    }
}
