package com.pochipay

import android.app.Application
import com.pochipay.data.AppDatabase
import com.pochipay.data.Repository
import com.pochipay.network.ServerConfig
import com.pochipay.network.ServerHealthMonitor
import com.pochipay.network.ServerUrlFetcher
import com.pochipay.utils.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber

class PochiPayApplication : Application() {

    val database by lazy { AppDatabase.getDatabase(this) }
    val repository by lazy { Repository(database) }
    
    // Server health monitor for automatic failover recovery
    private lateinit var serverHealthMonitor: ServerHealthMonitor
    
    // Application-level coroutine scope
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        // Always plant memory logger for the in-app log viewer
        Timber.plant(com.pochipay.utils.MemoryLogTree())

        // Create notification channel for incoming messages
        NotificationHelper(this).createNotificationChannel()
    }
    
    /**
     * Initialize server URLs. This should be called and awaited before making any API calls.
     * This is called from MainActivity or any entry point that needs the API.
     */
    suspend fun initializeServerUrls() {
        if (ServerConfig.isInitialized()) {
            return // Already initialized
        }
        
        try {
            Timber.i("🌐 Fetching server URLs from remote config...")
            
            // Check for custom override
            val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
            val customUrl = prefs.getString("custom_server_url", "")?.trim()
            
            if (!customUrl.isNullOrEmpty()) {
                Timber.w("⚠️ USING CUSTOM SERVER URL: $customUrl")
                // Create a config that uses the custom URL for everything
                val customConfig = com.pochipay.network.ServerUrls(
                    primary = customUrl,
                    secondary = customUrl,
                    isFallback = true
                )
                ServerConfig.initializeUrls(customConfig)
            } else {
                // Normal flow
                val serverUrls = ServerUrlFetcher.fetchServerUrls()
                ServerConfig.initializeUrls(serverUrls)
            }
            
            // Start server health monitoring after URLs are initialized
            if (!::serverHealthMonitor.isInitialized) {
                serverHealthMonitor = ServerHealthMonitor(this)
                serverHealthMonitor.startMonitoring()
                Timber.i("Server health monitoring started")
            }
        } catch (e: Exception) {
            Timber.e(e, "❌ Failed to initialize server configuration")
            throw e // Re-throw so caller knows initialization failed
        }
    }
    
    override fun onTerminate() {
        super.onTerminate()
        // Stop health monitoring when app terminates
        if (::serverHealthMonitor.isInitialized) {
            serverHealthMonitor.stopMonitoring()
        }
    }
}
