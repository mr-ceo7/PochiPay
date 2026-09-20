package com.pochipay.viewmodels

import android.app.Application
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pochipay.BuildConfig
import com.pochipay.data.local.AppPreferences
import com.pochipay.network.*
import com.pochipay.update.UpdateDownloadEvent
import com.pochipay.update.UpdateEventManager
import com.pochipay.utils.ApkSignatureVerifier
import com.pochipay.utils.NetworkUtil
import com.pochipay.utils.UpdateCacheManager
import com.google.gson.JsonSyntaxException
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import timber.log.Timber

sealed class StartupState {
    object Loading : StartupState()
    object RegistrationNeeded : StartupState()
    object AuthorizationNeeded : StartupState()
    data class UpdateAvailable(val versionName: String, val downloadUrl: String) : StartupState()
    data class UpdateDownloadComplete(val downloadId: Long) : StartupState()
    data class UpdateDownloadFailed(val reason: String) : StartupState()
    object InstallPermissionNeeded : StartupState()
    data class AuthError(val message: String) : StartupState()
    data class ServerError(val message: String) : StartupState()

    // New states for better error handling
    data class NetworkError(val message: String, val canRetry: Boolean = true) : StartupState()

    object StartupComplete : StartupState()
}

class StartupViewModel(application: Application) : AndroidViewModel(application) {

    private val _startupState = MutableStateFlow<StartupState>(StartupState.Loading)
    val startupState: StateFlow<StartupState> = _startupState

    private val _downloadProgress = MutableStateFlow(0)
    val downloadProgress: StateFlow<Int> = _downloadProgress

    private var currentDownloadId: Long? = null // To track the current APK download

    private val appPreferences = AppPreferences(application)
    private val apiService: ApiService = DependencyProvider.apiService

    init {
        // Observe download completion events
        viewModelScope.launch {
            UpdateEventManager.events.collect { event ->
                if (event is UpdateDownloadEvent.DownloadCompleted &&
                                event.downloadId == currentDownloadId
                ) {

                    if (event.status == DownloadManager.STATUS_SUCCESSFUL) {
                        Timber.d("Update download completed successfully")

                        // Get the downloaded file and cache it
                        try {
                            val downloadManager =
                                    getApplication<Application>()
                                            .getSystemService(Context.DOWNLOAD_SERVICE) as
                                            DownloadManager
                            val uri = downloadManager.getUriForDownloadedFile(event.downloadId)

                            if (uri != null) {
                                val downloadedFile = File(uri.path!!)

                                // Get version name from current state
                                val currentState = _startupState.value
                                if (currentState is StartupState.UpdateAvailable) {
                                    val versionName = currentState.versionName

                                    // Cache the update for future use
                                    UpdateCacheManager.cacheUpdate(
                                            getApplication(),
                                            downloadedFile,
                                            versionName
                                    )

                                    // Clean up old cached updates
                                    UpdateCacheManager.cleanOldUpdates(
                                            getApplication(),
                                            keepVersion = versionName
                                    )
                                }
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "Error caching update")
                            // Don't fail the update if caching fails
                        }

                        _startupState.value = StartupState.UpdateDownloadComplete(event.downloadId)
                    } else {
                        Timber.e("Update download failed")
                        _startupState.value = StartupState.UpdateDownloadFailed("Download failed.")
                    }
                }
            }
        }
    }

    fun beginStartupChecks() {
        Toast.makeText(getApplication(), "Galvaniy Technologies", Toast.LENGTH_SHORT).show()
        viewModelScope.launch {
            // Check network connectivity first
            if (!NetworkUtil.isNetworkAvailable(getApplication())) {
                Timber.w("No network connection available")
                _startupState.value =
                        StartupState.NetworkError(
                                message =
                                        "No internet connection. Please check your network settings.",
                                canRetry = true
                        )
                return@launch
            }

            _startupState.value = StartupState.Loading
            
                // Initialize server URLs first before making any API calls
                try {
                val app = getApplication<com.pochipay.PochiPayApplication>()
                app.initializeServerUrls()
                } catch (e: Exception) {
                Timber.e(e, "Failed to initialize server URLs")
                _startupState.value =
                    StartupState.NetworkError(
                        message = "Failed to load server configuration. Please try again.",
                        canRetry = true
                    )
                return@launch
                }
            
                // Now check for an update
            checkForUpdate()
        }
    }

    private suspend fun checkForUpdate() {
        try {
            Timber.d("Checking for app updates...")
            val response =
                    apiService.checkUpdate(
                            CheckUpdateRequest(version_code = BuildConfig.VERSION_CODE)
                    )
            if (response.isUpdateAvailable &&
                            response.newVersionName != null &&
                            response.downloadUrl != null
            ) {
                Timber.i("Update available: ${response.newVersionName}")

                // Check if this update is already cached
                val cachedUpdate =
                        UpdateCacheManager.getCachedUpdate(
                                getApplication(),
                                response.newVersionName
                        )

                if (cachedUpdate != null) {
                    // We already have this update downloaded and verified
                    Timber.i("Update v${response.newVersionName} found in cache, skipping download")
                    _startupState.value =
                            StartupState.UpdateDownloadComplete(
                                    0L
                            ) // downloadId not needed for cached files
                    // Store the cached file path so installUpdate can use it
                    currentDownloadId = -1L // Special marker for cached update
                } else {
                    // Need to download the update
                    _startupState.value =
                            StartupState.UpdateAvailable(
                                    response.newVersionName,
                                    response.downloadUrl
                            )
                }
            } else {
                Timber.d("No update available, proceeding with checks")
                proceedToIdAndAuthChecks()
            }
        } catch (e: IOException) {
            // Network error - could be transient
            Timber.e(e, "Network error checking for updates")
            _startupState.value =
                    StartupState.NetworkError(
                            message =
                                    "Unable to connect to server. Please check your internet connection.",
                            canRetry = true
                    )
        } catch (e: HttpException) {
            // Server returned error response
            Timber.e(e, "Server error checking for updates: ${e.code()}")
            when (e.code()) {
                503 -> {
                    _startupState.value =
                            StartupState.ServerError(
                                    "Server is temporarily unavailable. Please try again later."
                            )
                }
                else -> {
                    // Non-critical server error, proceed anyway
                    Timber.w("Non-critical server error, proceeding")
                    proceedToIdAndAuthChecks()
                }
            }
        } catch (e: JsonSyntaxException) {
            // Response parsing error
            Timber.e(e, "Failed to parse update response")
            proceedToIdAndAuthChecks() // Proceed anyway
        } catch (e: Exception) {
            // Unknown error
            Timber.e(e, "Unexpected error checking for updates")
            proceedToIdAndAuthChecks()
        }
    }

    fun downloadAndInstallUpdate(downloadUrl: String, versionName: String) {
        val context = getApplication<Application>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                _startupState.value = StartupState.InstallPermissionNeeded
                return
            }
        }

        // Create updates directory in app cache
        // Use externalCacheDir to match UpdateCacheManager and FileProvider config
        val baseDir = context.externalCacheDir ?: context.cacheDir
        val updatesDir = File(baseDir, "updates")
        if (!updatesDir.exists()) {
            updatesDir.mkdirs()
            Timber.d("Created updates cache directory")
        }

        // Create destination file in cache directory
        val destinationFile = File(updatesDir, "ntfy5-v$versionName.apk")

        // Delete old file if it exists
        if (destinationFile.exists()) {
            destinationFile.delete()
            Timber.d("Deleted existing update file")
        }

        val request =
                DownloadManager.Request(Uri.parse(downloadUrl))
                        .setTitle("NTFY5 Update")
                        .setDescription("Downloading version $versionName")
                        .setNotificationVisibility(
                                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                        )
                        .setDestinationUri(Uri.fromFile(destinationFile)) // Download to app cache
                        .setAllowedOverMetered(true)
                        .setAllowedOverRoaming(true)

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        currentDownloadId = downloadManager.enqueue(request)

        Timber.d(
                "Started update download to cache: ${destinationFile.absolutePath}, ID: $currentDownloadId"
        )

        // Start monitoring the download progress
        currentDownloadId?.let { monitorDownloadProgress(it) }
    }

    private fun monitorDownloadProgress(downloadId: Long) {
        viewModelScope.launch {
            val downloadManager =
                    getApplication<Application>().getSystemService(Context.DOWNLOAD_SERVICE) as
                            DownloadManager
            var isDownloading = true
            while (isDownloading) {
                val query = DownloadManager.Query().setFilterById(downloadId)
                downloadManager.query(query).use { cursor ->
                    if (cursor.moveToFirst()) {
                        val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                        val bytesDownloadedIndex =
                                cursor.getColumnIndex(
                                        DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR
                                )
                        val totalBytesIndex =
                                cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)

                        if (statusIndex == -1 || bytesDownloadedIndex == -1 || totalBytesIndex == -1
                        ) {
                            isDownloading = false
                            _downloadProgress.value = 0
                            return@use
                        }

                        val status = cursor.getInt(statusIndex)
                        val bytesDownloaded = cursor.getInt(bytesDownloadedIndex)
                        val totalBytes = cursor.getInt(totalBytesIndex)

                        if (status == DownloadManager.STATUS_SUCCESSFUL ||
                                        status == DownloadManager.STATUS_FAILED
                        ) {
                            isDownloading = false
                        }

                        if (totalBytes > 0) {
                            _downloadProgress.value =
                                    ((bytesDownloaded * 100L) / totalBytes).toInt()
                        }
                    } else {
                        // Download not found
                        isDownloading = false
                    }
                }
                if (isDownloading) {
                    delay(250) // Poll every 250ms
                }
            }
        }
    }

    fun installUpdate(downloadId: Long) {
        val context = getApplication<Application>()

        // Get the APK file - either from cache or from DownloadManager
        val apkFile: File? =
                if (downloadId == -1L) {
                    // Special case: using cached update
                    // Get version name from state
                    Timber.d("Using cached update")
                    val cacheDir = File(context.cacheDir, "updates")
                    val cachedFiles =
                            cacheDir.listFiles { file ->
                                file.extension == "apk" && file.name.startsWith("ntfy5-v")
                            }
                    cachedFiles?.maxByOrNull { it.lastModified() }
                } else {
                    // Normal case: get from DownloadManager
                    Timber.d("Getting update from DownloadManager, id: $downloadId")
                    val downloadManager =
                            context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                    val uri = downloadManager.getUriForDownloadedFile(downloadId)
                    uri?.let { File(it.path!!) }
                }

        if (apkFile == null || !apkFile.exists()) {
            Timber.e("Update file not found. DownloadId: $downloadId")
            Toast.makeText(context, "Update file not found.", Toast.LENGTH_LONG).show()
            _startupState.value = StartupState.UpdateDownloadFailed("Update file not found.")
            return
        }

        // SECURITY: Verify APK signature before installation
        Timber.d("Verifying APK signature for: ${apkFile.name}")
        if (!ApkSignatureVerifier.verifyApkSignature(context, apkFile)) {
            Timber.e("APK signature verification failed - installation blocked")
            Toast.makeText(
                            context,
                            "Security Error: Update signature verification failed. Installation blocked.",
                            Toast.LENGTH_LONG
                    )
                    .show()
            _startupState.value =
                    StartupState.UpdateDownloadFailed("Security: Invalid APK signature detected")
            // Clean up the potentially malicious file
            try {
                apkFile.delete()
                Timber.i("Deleted unverified APK file")
            } catch (e: Exception) {
                Timber.e(e, "Failed to delete unverified APK")
            }
            return
        }

        Timber.i("APK signature verified successfully, proceeding with installation")

        val installIntent =
                Intent(Intent.ACTION_VIEW).apply {
                    val authority = "${BuildConfig.APPLICATION_ID}.provider"
                    // Ensure the file is accessible via FileProvider
                    val contentUri = FileProvider.getUriForFile(context, authority, apkFile)
                    setDataAndType(contentUri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                    ) // Grant read permission to the installer
                }
        try {
            context.startActivity(installIntent)
        } catch (e: Exception) {
            Timber.e(e, "Failed to start installer")
            Toast.makeText(context, "Failed to start installer: ${e.message}", Toast.LENGTH_LONG)
                    .show()
            _startupState.value = StartupState.UpdateDownloadFailed("Installation failed.")
        }
    }

    private suspend fun proceedToIdAndAuthChecks() {
        // BYPASS: Skip registration and authorization
        Timber.i("Bypassing registration and authorization checks")
        
        // Ensure we have a fake App ID if needed for local logic, or just ignore
        if (appPreferences.appId == null) {
            appPreferences.appId = "bypassed_id_" + UUID.randomUUID().toString()
        }
        
        _startupState.value = StartupState.StartupComplete
        
        /* Original Logic
        val currentAppId = appPreferences.appId
        if (currentAppId == null) {
            _startupState.value = StartupState.RegistrationNeeded
            registerAppId()
        } else {
            checkAuthorization(currentAppId)
        }
        */
    }

    private suspend fun registerAppId(maxAttempts: Int = 10) {
        var attempts = 0

        while (attempts < maxAttempts) {
            try {
                val proposedId = UUID.randomUUID().toString()
                Timber.d("Attempting to register app ID (attempt ${attempts + 1}/$maxAttempts)")
                val response = apiService.registerId(RegisterIdRequest(proposedId))

                if (response.status == "available") {
                    appPreferences.appId = proposedId
                    Timber.i("Successfully registered app ID: $proposedId")
                    checkAuthorization(proposedId)
                    return
                }

                // Status is "taken", try again with new UUID
                Timber.d("ID was taken, trying again...")
                attempts++
                if (attempts < maxAttempts) {
                    delay(100) // Small delay between attempts
                }
            } catch (e: IOException) {
                Timber.e(e, "Network error during ID registration")
                _startupState.value =
                        StartupState.NetworkError(
                                message =
                                        "Cannot connect to server. Please check your internet connection.",
                                canRetry = true
                        )
                return
            } catch (e: HttpException) {
                Timber.e(e, "Server error during ID registration: ${e.code()}")
                _startupState.value =
                        StartupState.ServerError("Server error (${e.code()}): ${e.message()}")
                return
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error during ID registration")
                _startupState.value =
                        StartupState.ServerError(e.message ?: "An unknown error occurred")
                return
            }
        }

        // Exhausted all attempts (extremely unlikely with UUIDs)
        Timber.e("Failed to register ID after $maxAttempts attempts")
        _startupState.value =
                StartupState.ServerError("Unable to register device. Please try again later.")
    }

    private suspend fun checkAuthorization(appId: String) {
        val currentAuthCode = appPreferences.authCode
        if (currentAuthCode == null) {
            Timber.d("No auth code found, authorization needed")
            _startupState.value = StartupState.AuthorizationNeeded
        } else {
            try {
                _startupState.value = StartupState.Loading
                Timber.d("Checking authorization status...")
                val response =
                        apiService.checkAuthStatus(CheckAuthStatusRequest(proposed_id = appId))
                if (response.authorized) {
                    Timber.i("Authorization successful")
                    _startupState.value = StartupState.StartupComplete
                } else {
                    Timber.w("Authorization revoked by server")
                    appPreferences.authCode = null
                    _startupState.value = StartupState.AuthorizationNeeded
                }
            } catch (e: IOException) {
                Timber.e(e, "Network error during authorization check")
                _startupState.value =
                        StartupState.NetworkError(
                                message =
                                        "Unable to verify authorization. Please check your connection.",
                                canRetry = true
                        )
            } catch (e: HttpException) {
                Timber.e(e, "Server error during authorization check: ${e.code()}")
                _startupState.value =
                        StartupState.AuthError(
                                "Server error (${e.code()}): Unable to verify authorization."
                        )
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error during authorization check")
                _startupState.value =
                        StartupState.AuthError(
                                e.message ?: "Server error during authorization check"
                        )
            }
        }
    }

    fun verifyAuthCode(authCode: String) {
        viewModelScope.launch {
            _startupState.value = StartupState.Loading
            val currentAppId = appPreferences.appId
            if (currentAppId == null) {
                Timber.e("App ID missing during authorization attempt")
                _startupState.value =
                        StartupState.ServerError("App ID missing during authorization attempt.")
                return@launch
            }

            try {
                Timber.d("Verifying authorization code...")
                val response =
                        apiService.verifyAuthCode(
                                VerifyAuthCodeRequest(app_id = currentAppId, auth_code = authCode)
                        )
                if (response.status == "allowed") {
                    appPreferences.authCode = authCode
                    Timber.i("Authorization code verified successfully")
                    _startupState.value = StartupState.StartupComplete
                } else {
                    Timber.w("Invalid authorization code provided")
                    _startupState.value = StartupState.AuthError("Invalid authorization code.")
                }
            } catch (e: IOException) {
                Timber.e(e, "Network error during code verification")
                _startupState.value =
                        StartupState.NetworkError(
                                message = "Unable to verify code. Please check your connection.",
                                canRetry = false
                        )
            } catch (e: HttpException) {
                Timber.e(e, "Server error during code verification: ${e.code()}")
                _startupState.value =
                        StartupState.AuthError("Server error (${e.code()}): Unable to verify code.")
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error during code verification")
                _startupState.value =
                        StartupState.AuthError(
                                e.message ?: "Server error during code verification."
                        )
            }
        }
    }

    fun retryStartup() {
        beginStartupChecks()
    }
}
