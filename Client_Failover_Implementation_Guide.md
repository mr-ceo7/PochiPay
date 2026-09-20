# Client Failover Implementation Guide

## Overview

This guide demonstrates how to implement automatic failover in mobile clients (Royal Mint App and NTFY App) to use the secondary server when the primary is unavailable.

## Server URLs

- **Primary**: `https://mint-monitor.onrender.com`
- **Secondary**: `https://galvaniy-technologies-servers.onrender.com`

## Implementation Strategy

Clients should:
1. **Try primary server first** (default endpoint)
2. **Automatically fall back to secondary** if primary fails
3. **Check health endpoint** before making important requests
4. **Retry with exponential backoff** for transient failures

---

## Kotlin/Android Implementation

### 1. Server Configuration

```kotlin
object ServerConfig {
    const val PRIMARY_URL = "https://mint-monitor.onrender.com"
    const val SECONDARY_URL = "https://galvaniy-technologies-servers.onrender.com"
    
    private var currentServerUrl = PRIMARY_URL
    private var primaryFailed = false
    
    fun getActiveServerUrl(): String {
        return currentServerUrl
    }
    
    fun markPrimaryFailed() {
        primaryFailed = true
        currentServerUrl = SECONDARY_URL
        Log.w("ServerConfig", "Switched to secondary server")
    }
    
    fun resetToPrimary() {
        primaryFailed = false
        currentServerUrl = PRIMARY_URL
        Log.i("ServerConfig", "Reset to primary server")
    }
}
```

### 2. Health Check Function

```kotlin
suspend fun checkServerHealth(baseUrl: String): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            val response = httpClient.get("$baseUrl/sync/health/") {
                timeout {
                    requestTimeoutMillis = 5000
                }
            }
            response.status.isSuccess()
        } catch (e: Exception) {
            Log.e("HealthCheck", "Health check failed for $baseUrl: ${e.message}")
            false
        }
    }
}
```

### 3. Failover Request Function

```kotlin
suspend fun <T> makeRequestWithFailover(
    endpoint: String,
    method: HttpMethod = HttpMethod.Post,
    body: Any? = null,
    responseType: Class<T>
): Result<T> {
    return withContext(Dispatchers.IO) {
        // Try primary server first
        val primaryResult = makeRequest(
            "${ServerConfig.PRIMARY_URL}$endpoint",
            method,
            body,
            responseType
        )
        
        if (primaryResult.isSuccess) {
            // Primary worked, reset if we were using secondary
            ServerConfig.resetToPrimary()
            return@withContext primaryResult
        }
        
        // Primary failed, try secondary
        Log.w("Failover", "Primary server failed, trying secondary")
        ServerConfig.markPrimaryFailed()
        
        val secondaryResult = makeRequest(
            "${ServerConfig.SECONDARY_URL}$endpoint",
            method,
            body,
            responseType
        )
        
        if (secondaryResult.isFailure) {
            Log.e("Failover", "Both servers failed!")
        }
        
        return@withContext secondaryResult
    }
}

private suspend fun <T> makeRequest(
    url: String,
    method: HttpMethod,
    body: Any?,
    responseType: Class<T>
): Result<T> {
    return try {
        val response = when (method) {
            HttpMethod.Get -> httpClient.get(url)
            HttpMethod.Post -> httpClient.post(url) {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
            else -> throw IllegalArgumentException("Unsupported method")
        }
        
        if (response.status.isSuccess()) {
            val data = response.body<T>()
            Result.success(data)
        } else {
            Result.failure(Exception("HTTP ${response.status.value}"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
}
```

### 4. Usage Examples

#### Royal Mint App - Update Active Users

```kotlin
// Update user activity with automatic failover
suspend fun updateActiveUser(userName: String, accountType: String) {
    val requestBody = mapOf(
        "name" to userName,
        "account_type" to accountType
    )
    
    val result = makeRequestWithFailover<ActiveUserResponse>(
        endpoint = "/update-active-users/",
        method = HttpMethod.Post,
        body = requestBody,
        responseType = ActiveUserResponse::class.java
    )
    
    result.onSuccess { response ->
        Log.i("ActiveUser", "Successfully updated: ${response.status}")
    }.onFailure { error ->
        Log.e("ActiveUser", "Failed to update user: ${error.message}")
        // Optionally queue for retry later
    }
}
```

#### NTFY App - Check for Updates

```kotlin
suspend fun checkForUpdates(currentVersionCode: Int): UpdateInfo? {
    val requestBody = mapOf("version_code" to currentVersionCode)
    
    val result = makeRequestWithFailover<UpdateCheckResponse>(
        endpoint = "/ntfy/check-update/",
        method = HttpMethod.Post,
        body = requestBody,
        responseType = UpdateCheckResponse::class.java
    )
    
    return result.getOrNull()?.let { response ->
        if (response.isUpdateAvailable) {
            UpdateInfo(
                versionName = response.newVersionName,
                downloadUrl = response.downloadUrl
            )
        } else null
    }
}
```

### 5. Periodic Health Monitoring

```kotlin
class ServerHealthMonitor(private val context: Context) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    fun startMonitoring() {
        scope.launch {
            while (isActive) {
                delay(30_000) // Check every 30 seconds
                
                // Check if primary is back online
                if (checkServerHealth(ServerConfig.PRIMARY_URL)) {
                    ServerConfig.resetToPrimary()
                    Log.i("HealthMonitor", "Primary server is healthy")
                } else {
                    // Ensure we're using secondary
                    ServerConfig.markPrimaryFailed()
                }
            }
        }
    }
    
    fun stopMonitoring() {
        scope.cancel()
    }
}
```

---

## Swift/iOS Implementation

### 1. Server Configuration

```swift
class ServerConfig {
    static let shared = ServerConfig()
    
    private let primaryURL = "https://mint-monitor.onrender.com"
    private let secondaryURL = "https://galvaniy-technologies-servers.onrender.com"
    
    private var currentServerURL: String
    private var primaryFailed = false
    
    private init() {
        currentServerURL = primaryURL
    }
    
    func getActiveServerURL() -> String {
        return currentServerURL
    }
    
    func markPrimaryFailed() {
        primaryFailed = true
        currentServerURL = secondaryURL
        print("🔄 Switched to secondary server")
    }
    
    func resetToPrimary() {
        primaryFailed = false
        currentServerURL = primaryURL
        print("✅ Reset to primary server")
    }
}
```

### 2. Failover Request Function

```swift
func makeRequestWithFailover<T: Decodable>(
    endpoint: String,
    method: HTTPMethod = .post,
    body: Encodable? = nil
) async throws -> T {
    // Try primary first
    do {
        let primaryURL = "\\(ServerConfig.shared.primaryURL)\\(endpoint)"
        let result: T = try await makeRequest(url: primaryURL, method: method, body: body)
        ServerConfig.shared.resetToTo Primary()
        return result
    } catch {
        print("⚠️ Primary server failed: \\(error.localizedDescription)")
    }
    
    // Fallback to secondary
    ServerConfig.shared.markPrimaryFailed()
    let secondaryURL = "\\(ServerConfig.shared.secondaryURL)\\(endpoint)"
    return try await makeRequest(url: secondaryURL, method: method, body: body)
}
```

---

## Testing Failover

### Manual Testing

1. **Simulate primary down**:
   - Block network access to primary URL in device settings  
   - Or temporarily disable primary Render service

2. **Make API request** from mobile app

3. **Verify**:
   - App automatically uses secondary server
   - Request succeeds
   - Logs show failover occurred

4. **Re-enable primary server**

5. **Wait for health check** (30 seconds)

6. **Verify app switched back** to primary

### Expected Behavior

- **First request after primary fails**: ~5-10 second delay (timeout + retry)
- **Subsequent requests**: Instant (using cached secondary URL)
- **Recovery**: Automatic within 30 seconds of primary coming back

---

## Best Practices

1. **Short timeouts**: Use 5-10 second timeouts to fail fast
2. **Retry logic**: Implement exponential backoff for transient failures
3. **User feedback**: Show connection status indicator in UI
4. **Offline queue**: Queue important requests when both servers are down
5. **Logging**: Log all failover events for debugging
6. **Health checks**: Periodically verify primary server health

---

## Environment-Specific Configuration

For development/testing, you may want to add a third option:

```kotlin
object ServerConfig {
    const val PRIMARY_URL = "https://mint-monitor.onrender.com"
    const val SECONDARY_URL = "https://galvaniy-technologies-servers.onrender.com"
    const val DEV_URL = "http://10.0.2.2:8000"  // Android emulator localhost
    
    fun getServerUrl(): String {
        return when (BuildConfig.BUILD_TYPE) {
            "debug" -> DEV_URL
            else -> getActiveServerUrlWithFailover()
        }
    }
}
```

This allows local development without hitting production servers.
