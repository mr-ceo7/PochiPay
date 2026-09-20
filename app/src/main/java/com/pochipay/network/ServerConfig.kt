package com.pochipay.network

import timber.log.Timber

/**
 * ServerConfig manages the primary and secondary server URLs for automatic failover.
 * 
 * Server URLs are fetched dynamically from https://galvaniytechs.odoo.com/json on app start.
 * Falls back to hardcoded URLs if fetching fails.
 * 
 * When the primary server fails, the app automatically switches to the secondary server.
 * The ServerHealthMonitor can periodically check if the primary server is back online
 * and switch back automatically.
 */
object ServerConfig {
    // Fallback URLs (used until dynamic URLs are fetched or if fetching fails)
    private const val FALLBACK_PRIMARY_URL = "https://uon-smart-backend.onrender.com"
    private const val FALLBACK_SECONDARY_URL = "https://mint-monitor.onrender.com"
    
    const val HEALTH_ENDPOINT = "/sync/health/"
    
    @Volatile
    private var primaryUrl = FALLBACK_PRIMARY_URL
    
    @Volatile
    private var secondaryUrl = FALLBACK_SECONDARY_URL
    
    @Volatile
    private var currentServerUrl = primaryUrl
    
    @Volatile
    private var primaryFailed = false
    
    @Volatile
    private var urlsInitialized = false
    
    /**
     * Initialize server URLs from remote config.
     * This should be called once during app startup.
     */
    suspend fun initializeUrls(urls: ServerUrls) {
        primaryUrl = urls.primary
        secondaryUrl = urls.secondary
        currentServerUrl = primaryUrl
        primaryFailed = false
        urlsInitialized = true
        
        if (urls.isFallback) {
            Timber.w("⚠️ Using fallback server URLs")
        } else {
            Timber.i("✅ Server URLs initialized from remote config")
        }
        Timber.d("Primary: $primaryUrl")
        Timber.d("Secondary: $secondaryUrl")
    }
    
    /**
     * Check if URLs have been initialized from remote config
     */
    fun isInitialized(): Boolean = urlsInitialized
    
    /**
     * Get the primary server URL
     */
    fun getPrimaryUrl(): String = primaryUrl
    
    /**
     * Get the secondary server URL
     */
    fun getSecondaryUrl(): String = secondaryUrl
    
    /**
     * Get the currently active server URL (without trailing slash)
     */
    fun getActiveServerUrl(): String {
        return currentServerUrl
    }
    
    /**
     * Get the full base URL with /ntfy/ path for Retrofit
     */
    fun getActiveBaseUrl(): String {
        return "$currentServerUrl/ntfy/"
    }
    
    /**
     * Check if we're currently using the primary server
     */
    fun isUsingPrimary(): Boolean {
        return currentServerUrl == primaryUrl && !primaryFailed
    }
    
    /**
     * Mark the primary server as failed and switch to secondary
     */
    fun markPrimaryFailed() {
        if (currentServerUrl == primaryUrl) {
            primaryFailed = true
            currentServerUrl = secondaryUrl
            Timber.w("🔄 Switched to secondary server: $secondaryUrl")
        }
    }
    
    /**
     * Reset back to the primary server
     * This should be called when the primary server is confirmed to be healthy
     */
    fun resetToPrimary() {
        if (currentServerUrl != primaryUrl || primaryFailed) {
            primaryFailed = false
            currentServerUrl = primaryUrl
            Timber.i("✅ Reset to primary server: $primaryUrl")
        }
    }
    
    /**
     * Get the health check URL for a specific server
     */
    fun getHealthCheckUrl(serverUrl: String = currentServerUrl): String {
        return "$serverUrl$HEALTH_ENDPOINT"
    }
}
