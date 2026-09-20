# Server Failover Implementation - NTFY5

## Overview

The NTFY5 app now includes automatic server failover functionality. When the primary server becomes unavailable, the app automatically switches to a secondary backup server to ensure uninterrupted service.

## Architecture

### Key Components

1. **ServerConfig** (`ServerConfig.kt`)
   - Manages primary and secondary server URLs
   - Tracks which server is currently active
   - Provides thread-safe server switching

2. **FailoverInterceptor** (`FailoverInterceptor.kt`)
   - OkHttp interceptor that handles automatic failover
   - Detects network failures and 5xx errors
   - Automatically retries failed requests on the secondary server
   - Rebuilds request URLs to use the active server

3. **ServerHealthMonitor** (`ServerHealthMonitor.kt`)
   - Periodically checks primary server health
   - Automatically switches back to primary when it recovers
   - Runs health checks every 30 seconds (or 10 seconds when on secondary)

4. **HealthCheckService** (`ApiService.kt`)
   - Dedicated Retrofit service for health checks
   - Uses `/sync/health/` endpoint as specified by the server

## Server URLs

- **Primary**: `https://mint-monitor.onrender.com`
- **Secondary**: `https://galvaniy-technologies-servers.onrender.com`

Both servers support all NTFY5 API endpoints under `/ntfy/` path.

## How It Works

### 1. Normal Operation (Primary Server)
```
App Request → FailoverInterceptor → Primary Server → Success → Response
```

### 2. Failover Scenario (Primary Fails)
```
App Request → FailoverInterceptor → Primary Server → FAIL (network/5xx error)
           → Switch to Secondary
           → FailoverInterceptor → Secondary Server → Success → Response
           
ServerHealthMonitor → Checks Primary every 10s → Primary Healthy → Switch back
```

### 3. Automatic Recovery
The `ServerHealthMonitor` continuously checks the primary server health in the background:
- Every 30 seconds when using primary (normal operation)
- Every 10 seconds when using secondary (faster recovery)
- Automatically switches back to primary when it's confirmed healthy

## Implementation Details

### Interceptor Chain Order
```kotlin
OkHttpClient.Builder()
    .addInterceptor(authInterceptor)      // 1. Add API key header
    .addInterceptor(FailoverInterceptor()) // 2. Handle failover
    .addInterceptor(RetryInterceptor())    // 3. Retry transient failures
    .addInterceptor(logging)               // 4. Log requests (debug only)
```

**Important**: `FailoverInterceptor` must come BEFORE `RetryInterceptor` so that failover happens before retry attempts.

### Timeout Configuration
Timeouts have been reduced from 30s to 10s for faster failover:
- **Connect timeout**: 10 seconds
- **Read timeout**: 10 seconds
- **Write timeout**: 10 seconds

Health checks use even shorter timeouts (5 seconds) for quick detection.

### Thread Safety
`ServerConfig` uses `@Volatile` annotations to ensure thread-safe access to the current server URL across multiple threads.

## Testing the Failover

### Manual Testing Steps

1. **Test Normal Operation**
   ```
   - Launch app
   - Perform any API operation (check updates, register, etc.)
   - Verify logs show "Primary server" in use
   ```

2. **Test Failover**
   ```
   - Block access to primary server (network rules, VPN, etc.)
   - OR temporarily disable primary Render service
   - Perform an API operation
   - Check logs for "⚠️ Primary server unreachable, attempting failover to secondary"
   - Verify request succeeds using secondary server
   ```

3. **Test Recovery**
   ```
   - With app running on secondary server
   - Re-enable primary server
   - Wait 10-30 seconds
   - Check logs for "✅ Primary server is healthy again, switching back"
   - Verify subsequent requests use primary server
   ```

### Log Messages to Watch For

**Failover Events:**
- `🔄 Switched to secondary server: https://galvaniy-technologies-servers.onrender.com`
- `⚠️ Primary server unreachable, attempting failover to secondary`
- `⚠️ Primary server returned 5XX, attempting failover to secondary`

**Recovery Events:**
- `✅ Reset to primary server: https://mint-monitor.onrender.com`
- `✅ Primary server is healthy again, switching back`

**Health Monitoring:**
- `🔍 Starting ServerHealthMonitor`
- `✅ Primary server is healthy`
- `⚠️ Primary server health check failed`

**Error Conditions:**
- `❌ Secondary server also failed!` (both servers unavailable)
- `❌ Both servers appear unhealthy!` (health monitor detected both down)

## Error Handling

### Both Servers Down
If both primary and secondary servers are unavailable:
1. The last request will fail with an IOException
2. The app should handle this gracefully and inform the user
3. ServerHealthMonitor will continue checking and auto-recover when any server comes back

### Partial Failures
- Network errors → Immediate failover
- HTTP 5xx errors → Immediate failover
- HTTP 4xx errors → No failover (client error, not server issue)
- HTTP 429 (Rate Limit) → RetryInterceptor handles with exponential backoff

## Performance Considerations

### Failover Timing
- First request after primary fails: ~10-15 seconds (timeout + retry)
- Subsequent requests: Instant (using cached secondary URL)
- Recovery to primary: Within 10-30 seconds of primary becoming healthy

### Network Overhead
- Health checks: Lightweight HEAD request every 10-30 seconds
- No impact on actual API requests
- Health checks use separate, simpler HTTP client

## Configuration

### Adjusting Check Intervals
In `ServerHealthMonitor.kt`:
```kotlin
companion object {
    private const val CHECK_INTERVAL_MS = 30_000L        // Normal: 30s
    private const val CHECK_INTERVAL_FAST_MS = 10_000L   // On secondary: 10s
}
```

### Adjusting Timeouts
In `DependencyProvider.kt`:
```kotlin
.connectTimeout(10, TimeUnit.SECONDS)
.readTimeout(10, TimeUnit.SECONDS)
.writeTimeout(10, TimeUnit.SECONDS)
```

### Adding More Servers
To add a third fallback server:
1. Add URL constant to `ServerConfig`
2. Modify `FailoverInterceptor` to try third server
3. Update `ServerHealthMonitor` to check all servers

## Integration with Existing Code

### No Code Changes Required!
The failover system is transparent to the rest of the app. All existing code using `DependencyProvider.apiService` automatically gets failover support:

```kotlin
// This code automatically uses failover - no changes needed!
val response = DependencyProvider.apiService.checkUpdate(
    CheckUpdateRequest(BuildConfig.VERSION_CODE)
)
```

### Accessing Server Status
If you need to check which server is active:
```kotlin
val currentServer = ServerConfig.getActiveServerUrl()
val isUsingPrimary = ServerConfig.isUsingPrimary()
```

## Monitoring in Production

### Recommended Metrics to Track
1. Failover frequency (how often does app switch to secondary?)
2. Time spent on secondary server
3. Health check failure rate
4. Request success rate per server

### Timber Logs
All failover events are logged using Timber with appropriate levels:
- `Timber.i()` - Normal operations and successful recoveries
- `Timber.w()` - Warnings (server failures, failover triggered)
- `Timber.e()` - Errors (both servers down, unexpected issues)

## Troubleshooting

### Problem: Failover not working
**Check:**
- Is `FailoverInterceptor` added before `RetryInterceptor`?
- Are timeouts too long? (should be 10s or less)
- Check Timber logs for error messages

### Problem: Not switching back to primary
**Check:**
- Is `ServerHealthMonitor` running? (check logs for startup message)
- Is primary server actually healthy? (check `/sync/health/` endpoint)
- Check logs for health check results

### Problem: Too many failover switches
**Possible causes:**
- Primary server is unstable (intermittent failures)
- Network issues
- Health check intervals too short
**Solution:** Increase `CHECK_INTERVAL_MS` or add hysteresis logic

## Security Considerations

1. **HTTPS Only**: Both servers use HTTPS
2. **API Key**: All requests include X-API-Key header
3. **Certificate Pinning**: Consider adding certificate pinning for production
4. **URL Validation**: Server URLs are hardcoded and validated

## Future Enhancements

Possible improvements for the failover system:

1. **Geographic failover**: Ping multiple servers and choose the fastest
2. **Load balancing**: Distribute traffic across multiple healthy servers
3. **Circuit breaker**: Temporarily stop checking a server after repeated failures
4. **Metrics dashboard**: Real-time visualization of server health
5. **User notification**: Inform users when using backup server
6. **Configurable URLs**: Load server URLs from remote config
7. **Fallback queue**: Queue requests offline and sync when servers return

## Server Requirements

The backend servers must implement:
1. **Health endpoint**: `GET /sync/health/` returning 2xx status when healthy
2. **API endpoints**: All NTFY5 endpoints under `/ntfy/` path on both servers
3. **Data synchronization**: Both servers must share the same database/state
4. **API key validation**: Accept requests with `X-API-Key: nfty5-galvaniytechnologies`

## References

- Server Implementation Guide: `# Client Failover Implementation Guide.md`
- API Documentation: `NTFY5_API_Instructions.md`
- Retrofit Documentation: https://square.github.io/retrofit/
- OkHttp Interceptors: https://square.github.io/okhttp/features/interceptors/
