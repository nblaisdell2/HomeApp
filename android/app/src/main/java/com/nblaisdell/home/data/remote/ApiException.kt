package com.nblaisdell.home.data.remote

import retrofit2.Response

/** A non-2xx API response. `code == 401` means the device key is missing, wrong or revoked. */
class ApiException(val code: Int, message: String) : Exception(message) {
    val isUnauthorized get() = code == 401
}

/** Returns the body of a successful response, or throws [ApiException]. */
fun <T> Response<T>.bodyOrThrow(): T {
    val body = body()
    if (isSuccessful && body != null) return body
    throw ApiException(code(), "HTTP ${code()} ${errorBody()?.string().orEmpty()}".trim())
}
