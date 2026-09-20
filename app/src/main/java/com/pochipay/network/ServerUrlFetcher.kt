package com.pochipay.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Fetches server URLs from a remote HTML page.
 * 
 * The HTML page at https://galvaniytechs.odoo.com/json contains the server URLs
 * in the format:
 * PRIMARY:https://server-url.com
 * SECONDARY:https://server-url.com
 */
object ServerUrlFetcher {
    
    private const val SERVER_CONFIG_URL = "https://galvaniytechs.odoo.com/json"
    
    // Fallback URLs in case fetching fails
    private const val FALLBACK_PRIMARY = "https://galvaniy-technologies-servers.onrender.com"
    private const val FALLBACK_SECONDARY = "https://mint-monitor-peer.onrender.com"
    
    /**
     * Fetches server URLs from the remote HTML page.
     * Returns ServerUrls with the fetched URLs, or fallback URLs if fetch fails.
     */
    suspend fun fetchServerUrls(): ServerUrls {
        return withContext(Dispatchers.IO) {
            try {
                val client = OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.SECONDS)
                    .build()
                
                val request = Request.Builder()
                    .url(SERVER_CONFIG_URL)
                    .get()
                    .build()
                
                val response = client.newCall(request).execute()
                
                if (response.isSuccessful) {
                    val htmlContent = response.body?.string()
                    if (htmlContent != null) {
                        val urls = parseServerUrls(htmlContent)
                        if (urls != null) {
                            Timber.i("✅ Successfully fetched server URLs from remote config")
                            Timber.d("Primary: ${urls.primary}, Secondary: ${urls.secondary}")
                            return@withContext urls
                        }
                    }
                }
                
                Timber.w("⚠️ Failed to fetch server URLs, using fallback values")
                ServerUrls(FALLBACK_PRIMARY, FALLBACK_SECONDARY, isFallback = true)
                
            } catch (e: Exception) {
                Timber.e(e, "❌ Error fetching server URLs, using fallback values")
                ServerUrls(FALLBACK_PRIMARY, FALLBACK_SECONDARY, isFallback = true)
            }
        }
    }
    
    /**
     * Parses the HTML content to extract PRIMARY and SECONDARY URLs.
     * 
     * Expected format in HTML:
     * PRIMARY:https://server1.com
     * SECONDARY:https://server2.com
     */
    private fun parseServerUrls(htmlContent: String): ServerUrls? {
        try {
            // Look for patterns like "PRIMARY:https://..." and "SECONDARY:https://..."
            val primaryPattern = """PRIMARY:\s*(https?://[^\s<]+)""".toRegex(RegexOption.IGNORE_CASE)
            val secondaryPattern = """SECONDARY:\s*(https?://[^\s<]+)""".toRegex(RegexOption.IGNORE_CASE)
            
            val primaryMatch = primaryPattern.find(htmlContent)
            val secondaryMatch = secondaryPattern.find(htmlContent)
            
            val primaryUrl = primaryMatch?.groupValues?.getOrNull(1)?.trim()
            val secondaryUrl = secondaryMatch?.groupValues?.getOrNull(1)?.trim()
            
            if (primaryUrl != null && secondaryUrl != null) {
                Timber.d("Parsed URLs - Primary: $primaryUrl, Secondary: $secondaryUrl")
                return ServerUrls(primaryUrl, secondaryUrl, isFallback = false)
            } else {
                Timber.w("Could not parse both URLs from HTML content")
                return null
            }
        } catch (e: Exception) {
            Timber.e(e, "Error parsing server URLs from HTML")
            return null
        }
    }
}

/**
 * Data class holding the primary and secondary server URLs
 */
data class ServerUrls(
    val primary: String,
    val secondary: String,
    val isFallback: Boolean = false
)
