package com.pochipay.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Utility class for managing and debugging server failover.
 * 
 * This provides convenience methods for:
 * - Checking server health manually
 * - Getting current server status
 * - Forcing a server switch (for testing)
 */
object ServerFailoverUtils {
    
    /**
     * Get a human-readable status of the current server configuration
     */
    fun getServerStatus(): ServerStatus {
        return ServerStatus(
            currentServer = ServerConfig.getActiveServerUrl(),
            isUsingPrimary = ServerConfig.isUsingPrimary(),
            primaryUrl = ServerConfig.getPrimaryUrl(),
            secondaryUrl = ServerConfig.getSecondaryUrl()
        )
    }
    
    /**
     * Manually check the health of both servers.
     * Returns a map of server URL to health status.
     */
    suspend fun checkAllServersHealth(): Map<String, Boolean> {
        return withContext(Dispatchers.IO) {
            val primaryHealth = checkServerHealth(ServerConfig.getPrimaryUrl())
            val secondaryHealth = checkServerHealth(ServerConfig.getSecondaryUrl())
            
            mapOf(
                ServerConfig.getPrimaryUrl() to primaryHealth,
                ServerConfig.getSecondaryUrl() to secondaryHealth
            )
        }
    }
    
    /**
     * Check if a specific server is healthy
     */
    private suspend fun checkServerHealth(serverUrl: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val healthService = DependencyProvider.createHealthCheckService(serverUrl)
                val response = healthService.checkHealth()
                response.isSuccessful
            } catch (e: Exception) {
                Timber.w("Health check failed for $serverUrl: ${e.message}")
                false
            }
        }
    }
    
    /**
     * Force a switch to the secondary server (for testing purposes).
     * This should only be used for debugging/testing!
     */
    fun forceSwitchToSecondary() {
        Timber.w("⚠️ FORCE SWITCH: Manually switching to secondary server")
        ServerConfig.markPrimaryFailed()
    }
    
    /**
     * Force a switch back to the primary server (for testing purposes).
     * This should only be used for debugging/testing!
     */
    fun forceSwitchToPrimary() {
        Timber.w("⚠️ FORCE SWITCH: Manually switching to primary server")
        ServerConfig.resetToPrimary()
    }
    
    /**
     * Get the health check URL for testing
     */
    fun getHealthCheckUrl(serverUrl: String? = null): String {
        return ServerConfig.getHealthCheckUrl(serverUrl ?: ServerConfig.getActiveServerUrl())
    }
}

/**
 * Data class representing the current server status
 */
data class ServerStatus(
    val currentServer: String,
    val isUsingPrimary: Boolean,
    val primaryUrl: String,
    val secondaryUrl: String
) {
    override fun toString(): String {
        val status = if (isUsingPrimary) "PRIMARY" else "SECONDARY"
        val initStatus = if (ServerConfig.isInitialized()) "✅ Initialized" else "⏳ Using fallback"
        return """
            Server Status: $status ($initStatus)
            Current: $currentServer
            Primary: $primaryUrl
            Secondary: $secondaryUrl
        """.trimIndent()
    }
}
