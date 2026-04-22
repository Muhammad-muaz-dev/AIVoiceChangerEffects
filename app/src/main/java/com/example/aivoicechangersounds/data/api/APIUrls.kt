package com.example.aivoicechangersounds.data.api
/**
 * Single source of truth for every base URL in the app.
 * Add new URLs here whenever you integrate a new backend.
 */

/**
 * Single source of truth for every base URL in the app.
 *
 * RULES:
 * - Base URLs must always end with "/"
 * - Never put full endpoint paths here, only the base domain
 * - Full endpoint paths belong in the interface methods (@GET, @POST etc.)
 */
object ApiUrls {

    /** Main backend – voices, languages, speech-to-speech */
    const val MAIN_BASE_URL = "https://pollux.aspire.pics/"

    /**
     * TTS backend – currently same server as main.
     * If it ever moves to its own server just change this one line
     * and every service using it updates automatically.
     */
    const val TTS_BASE_URL = "https://pollux.aspire.pics/api/generate/"
    const val Voices_Free ="https://pollux.aspire.pics/api/voices/free?/"
    const val Languages_All="https://pollux.aspire.pics/api/languages/"
}
