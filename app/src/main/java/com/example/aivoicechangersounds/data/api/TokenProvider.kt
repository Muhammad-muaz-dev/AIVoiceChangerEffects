package com.example.aivoicechangersounds.data.api

/**
 * Single source of truth for the auth token.
 *
 * WHY THIS EXISTS:
 * Never hardcode a token inside a Retrofit @Header parameter.
 * If the token changes (expiry, refresh, logout) you would have
 * to hunt down every method and update it.
 * With this class you update ONE place and every request picks
 * up the new value automatically via the OkHttp interceptor
 * in ServiceFactory.
 *
 * HOW TO USE:
 * - On login / token refresh → TokenProvider.setToken("Bearer ey...")
 * - On logout               → TokenProvider.clearToken()
 * - You never need to call this from a Repository or ViewModel.
 *   ServiceFactory's OkHttp interceptor reads it for you.
 */
object TokenProvider {

    // Start with an empty token. Set it after login/refresh.
    @Volatile
    private var token: String = ""

    /** Call this after a successful login or token refresh. */
    fun setToken(bearerToken: String) {
        token = bearerToken
    }

    /** Call this on logout. */
    fun clearToken() {
        token = ""
    }

    /**
     * Read by the OkHttp interceptor in ServiceFactory.
     * Returns the full "Bearer eyJ..." string, or empty string if not set.
     */
    fun getToken(): String = token

    /** Returns true if a token has been set. */
    fun hasToken(): Boolean = token.isNotEmpty()
}