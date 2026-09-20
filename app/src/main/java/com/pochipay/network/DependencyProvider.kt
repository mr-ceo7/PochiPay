package com.pochipay.network

import com.pochipay.BuildConfig
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object DependencyProvider {
    // WARNING: Storing API keys directly in source code is not secure for production applications.
    // For a real app, consider using BuildConfig, encrypted SharedPreferences, or a secure secrets management system.
    private const val API_KEY = "nfty5-pochipay"

    /**
     * Main API service with automatic failover support.
     * The FailoverInterceptor automatically switches to the secondary server if primary fails.
     */
    val apiService: ApiService by lazy {
        val logging = HttpLoggingInterceptor().apply {
            // Only log bodies in debug builds
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

        val authInterceptor = Interceptor { chain ->
            val originalRequest = chain.request()
            val newRequest = originalRequest.newBuilder()
                .header("X-API-Key", API_KEY)
                .build()
            chain.proceed(newRequest)
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(authInterceptor) // Add the API key interceptor
            .addInterceptor(FailoverInterceptor()) // Add failover interceptor BEFORE retry
            .addInterceptor(RetryInterceptor(maxRetries = 3)) // Add retry interceptor
            .addInterceptor(logging) // Add the logging interceptor (should be last)
            .connectTimeout(10, TimeUnit.SECONDS) // Reduced timeout for faster failover
            .readTimeout(10, TimeUnit.SECONDS)    // Reduced timeout for faster failover
            .writeTimeout(10, TimeUnit.SECONDS)   // Reduced timeout for faster failover
            .build()

        Retrofit.Builder()
            .baseUrl(ServerConfig.getActiveBaseUrl()) // Use dynamic base URL
            .addConverterFactory(GsonConverterFactory.create())
            .client(client)
            .build()
            .create(ApiService::class.java)
    }
    
    /**
     * Health check service for checking server availability.
     * This uses a separate lightweight client without retries or failover,
     * and uses the base server URL (without /ntfy/ path).
     */
    fun createHealthCheckService(serverUrl: String): HealthCheckService {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(5, TimeUnit.SECONDS) // Short timeout for health checks
            .readTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(serverUrl) // Use the server URL directly
            .client(client)
            .build()
            .create(HealthCheckService::class.java)
    }
}
