package com.pochipay.network

import android.content.Context
import kotlinx.coroutines.*
import timber.log.Timber

/**
 * ServerHealthMonitor periodically checks the health of the primary server.
 * 
 * When the app is using the secondary server (because primary failed),
 * this monitor will periodically check if the primary is back online.
 * Once the primary is confirmed healthy, it automatically switches back.
 * 
 * Usage:
 * ```
 * val monitor = ServerHealthMonitor(context)
 * monitor.startMonitoring()
 * // Later, when the app is shutting down:
 * monitor.stopMonitoring()
 * ```
 */
class ServerHealthMonitor(private val context: Context) {
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var monitoringJob: Job? = null
    
    companion object {
        private const val CHECK_INTERVAL_MS = 30_000L // Check every 30 seconds
        private const val CHECK_INTERVAL_FAST_MS = 10_000L // Check every 10 seconds when on secondary
    }
    
    /**
     * Start periodic health monitoring.
     * This should be called when the app starts or when failover occurs.
     */
    fun startMonitoring() {
        if (monitoringJob?.isActive == true) {
            Timber.d("ServerHealthMonitor already running")
            return
        }
        
        Timber.i("🔍 Starting ServerHealthMonitor")
        
        monitoringJob = scope.launch {
            while (isActive) {
                try {
                    performHealthCheck()
                } catch (e: Exception) {
                    Timber.e(e, "Error during health check")
                }
                
                // Use faster checks when on secondary to quickly recover to primary
                val interval = if (ServerConfig.isUsingPrimary()) {
                    CHECK_INTERVAL_MS
                } else {
                    CHECK_INTERVAL_FAST_MS
                }
                
                delay(interval)
            }
        }
    }
    
    /**
     * Stop health monitoring.
     * This should be called when the app is shutting down.
     */
    fun stopMonitoring() {
        Timber.i("⏸️ Stopping ServerHealthMonitor")
        monitoringJob?.cancel()
        monitoringJob = null
    }
    
    /**
     * Performs a health check on both servers and manages the active server.
     */
    private suspend fun performHealthCheck() {
        // Check primary server health
        val isPrimaryHealthy = checkServerHealth(ServerConfig.getPrimaryUrl())
        
        if (isPrimaryHealthy) {
            if (!ServerConfig.isUsingPrimary()) {
                Timber.i("✅ Primary server is healthy again, switching back")
                ServerConfig.resetToPrimary()
            } else {
                Timber.d("✅ Primary server is healthy")
            }
        } else {
            if (ServerConfig.isUsingPrimary()) {
                Timber.w("⚠️ Primary server health check failed")
                // Don't automatically switch here - let the FailoverInterceptor handle it on actual requests
                // This avoids switching based on a single health check failure
            } else {
                Timber.d("⚠️ Primary server still unhealthy, remaining on secondary")
                
                // Also check secondary health for monitoring purposes
                val isSecondaryHealthy = checkServerHealth(ServerConfig.getSecondaryUrl())
                if (!isSecondaryHealthy) {
                    Timber.e("❌ Both servers appear unhealthy!")
                }
            }
        }
    }
    
    /**
     * Checks if a specific server is healthy by calling its health endpoint.
     * 
     * @param serverUrl The base URL of the server to check
     * @return true if the server is healthy, false otherwise
     */
    private suspend fun checkServerHealth(serverUrl: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val healthService = DependencyProvider.createHealthCheckService(serverUrl)
                val response = healthService.checkHealth()
                val isHealthy = response.isSuccessful
                
                if (isHealthy) {
                    Timber.d("Health check succeeded for $serverUrl")
                } else {
                    Timber.w("Health check failed for $serverUrl: HTTP ${response.code()}")
                }
                
                isHealthy
            } catch (e: Exception) {
                Timber.w("Health check failed for $serverUrl: ${e.message}")
                false
            }
        }
    }
    
    /**
     * Manually trigger a health check (useful for testing or forced checks).
     * This is a suspend function that returns the health status of the primary server.
     */
    suspend fun checkPrimaryServerNow(): Boolean {
        return checkServerHealth(ServerConfig.getPrimaryUrl())
    }
    
    /**
     * Get the current monitoring status
     */
    fun isMonitoring(): Boolean {
        return monitoringJob?.isActive == true
    }
}
