package com.example.aivoicechangersounds.data.api
/**
 * Single source of truth for every base URL in the app.
 * Add new URLs here whenever you integrate a new backend.
 */
object ApiUrls {

    /** Main backend – voices, languages, speech-to-speech */
    const val MAIN_BASE_URL = "https://pollux.aspire.pics/"

    /** Text-to-speech backend */
    const val TTS_BASE_URL = "https://pollux.aspire.pics/api/generate"

    // If you ever add a second backend just drop it here, e.g.:
    // const val ANALYTICS_BASE_URL = "https://analytics.yourapi.com/v1/"
}
