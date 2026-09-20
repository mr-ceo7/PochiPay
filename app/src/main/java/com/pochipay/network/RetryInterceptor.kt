package com.pochipay.network

import okhttp3.Interceptor
import okhttp3.Response
import timber.log.Timber
import java.io.IOException

/**
 * OkHttp Interceptor that automatically retries failed requests with exponential backoff.
 * 
 * Retries are attempted for:
 * - Network failures (IOException)
 * - Server errors that are typically transient (408, 429, 500, 502, 503, 504)
 * 
 * @param maxRetries Maximum number of retry attempts (default: 3)
 */
class RetryInterceptor(private val maxRetries: Int = 3) : Interceptor {
    
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        var response: Response? = null
        var exception: IOException? = null
        var attempt = 0
        
        while (attempt < maxRetries) {
            try {
                response?.close() // Close previous response if exists
                response = chain.proceed(request)
                
                // Success or non-retryable error - return immediately
                if (response.isSuccessful || !isRetryable(response)) {
                    return response
                }
                
                // Retryable HTTP error
                Timber.w("Request failed with code ${response.code}, attempt ${attempt + 1}/$maxRetries")
                
                if (attempt < maxRetries - 1) {
                    response.close()
                    Thread.sleep(exponentialBackoff(attempt))
                }
            } catch (e: IOException) {
                exception = e
                Timber.w(e, "Network request failed, attempt ${attempt + 1}/$maxRetries")
                
                if (attempt < maxRetries - 1) {
                    Thread.sleep(exponentialBackoff(attempt))
                }
            }
            
            attempt++
        }
        
        // All retries exhausted
        if (exception != null) {
            Timber.e(exception, "Request failed after $maxRetries attempts")
            throw exception
        }
        
        // Return the last failed response
        Timber.e("Request failed after $maxRetries attempts with code ${response!!.code}")
        return response!!
    }
    
    /**
     * Determines if an HTTP response code is retryable.
     */
    private fun isRetryable(response: Response): Boolean {
        return response.code in listOf(
            408, // Request Timeout
            429, // Too Many Requests
            500, // Internal Server Error
            502, // Bad Gateway
            503, // Service Unavailable
            504  // Gateway Timeout
        )
    }
    
    /**
     * Calculates exponential backoff delay.
     * Returns: 1s, 2s, 4s, 8s, etc.
     */
    private fun exponentialBackoff(attempt: Int): Long {
        return (1000 * (1 shl attempt)).toLong()
    }
}
