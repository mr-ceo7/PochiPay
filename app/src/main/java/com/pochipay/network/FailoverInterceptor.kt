package com.pochipay.network

import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import timber.log.Timber
import java.io.IOException

/**
 * OkHttp Interceptor that implements automatic failover to a secondary server.
 * 
 * When a request to the primary server fails (network error or 5xx error),
 * this interceptor automatically retries the request against the secondary server.
 * 
 * This should be added to the OkHttpClient BEFORE the RetryInterceptor.
 */
class FailoverInterceptor : Interceptor {
    
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        
        // Try the request with the current server
        val response = try {
            chain.proceed(buildRequestForCurrentServer(originalRequest))
        } catch (e: IOException) {
            Timber.w(e, "Request to ${ServerConfig.getActiveServerUrl()} failed with network error")
            
            // If we're using primary, try secondary
            if (ServerConfig.isUsingPrimary()) {
                Timber.w("⚠️ Primary server unreachable, attempting failover to secondary")
                ServerConfig.markPrimaryFailed()
                
                // Retry with secondary server
                return try {
                    chain.proceed(buildRequestForCurrentServer(originalRequest))
                } catch (secondaryException: IOException) {
                    Timber.e(secondaryException, "❌ Secondary server also failed!")
                    throw secondaryException
                }
            } else {
                // Already on secondary and it failed
                throw e
            }
        }
        
        // Check if we got a server error (5xx) that indicates server unavailability
        if (!response.isSuccessful && response.code >= 500) {
            Timber.w("Request to ${ServerConfig.getActiveServerUrl()} failed with HTTP ${response.code}")
            
            // If we're using primary, try secondary
            if (ServerConfig.isUsingPrimary()) {
                response.close() // Close the failed response
                
                Timber.w("⚠️ Primary server returned ${response.code}, attempting failover to secondary")
                ServerConfig.markPrimaryFailed()
                
                // Retry with secondary server
                return chain.proceed(buildRequestForCurrentServer(originalRequest))
            }
        }
        
        // If request succeeded and we were on secondary, check if primary is back
        if (response.isSuccessful && !ServerConfig.isUsingPrimary()) {
            Timber.d("Request to secondary successful, primary recovery will be checked by ServerHealthMonitor")
        }
        
        return response
    }
    
    /**
     * Rebuilds the request with the current active server URL
     */
    private fun buildRequestForCurrentServer(originalRequest: Request): Request {
        val originalUrl = originalRequest.url
        val newBaseUrl = ServerConfig.getActiveBaseUrl()
        
        // Extract the path after /ntfy/
        val path = originalUrl.encodedPath
        val pathAfterNtfy = if (path.startsWith("/ntfy/")) {
            path.substring(6) // Remove "/ntfy/"
        } else {
            path
        }
        
        // Build new URL with current server
        val newUrl = originalRequest.url.newBuilder()
            .scheme("https")
            .host(ServerConfig.getActiveServerUrl().removePrefix("https://"))
            .encodedPath("/ntfy/$pathAfterNtfy")
            .build()
        
        Timber.d("Request URL: ${newUrl}")
        
        return originalRequest.newBuilder()
            .url(newUrl)
            .build()
    }
}
