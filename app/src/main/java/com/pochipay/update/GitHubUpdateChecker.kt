package com.pochipay.update

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import timber.log.Timber
import java.util.concurrent.TimeUnit

data class GitHubReleaseInfo(
    val versionName: String,
    val versionCode: Long,
    val downloadUrl: String,
    val releaseNotes: String?
)

object GitHubUpdateChecker {

    private const val GITHUB_OWNER = "mr-ceo7"
    private const val GITHUB_REPO = "PochiPay"
    private const val RELEASES_API_URL =
        "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"
    private const val WORK_NAME = "pochipay_github_auto_update"

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Checks GitHub Releases for a newer version than current.
     * Returns GitHubReleaseInfo if an update is available, null otherwise.
     */
    suspend fun checkForUpdate(
        currentVersionCode: Int,
        currentVersionName: String
    ): GitHubReleaseInfo? = withContext(Dispatchers.IO) {
        try {
            Timber.d("Querying GitHub Releases: $RELEASES_API_URL")
            val request = Request.Builder()
                .url(RELEASES_API_URL)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "PochiPay-Android-App")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                Timber.w("GitHub Releases API returned HTTP ${response.code}")
                return@withContext null
            }

            val responseBody = response.body?.string() ?: return@withContext null
            val json = JSONObject(responseBody)

            val tagName = json.optString("tag_name", "").trim()
            if (tagName.isEmpty()) {
                Timber.w("No tag_name in GitHub release response")
                return@withContext null
            }

            val cleanVersion = tagName.removePrefix("v").removePrefix("V")
            val remoteVersionCode = parseVersionToCode(cleanVersion)
            val localVersionCode = currentVersionCode.toLong()

            Timber.i("GitHub version: $cleanVersion (code: $remoteVersionCode), Local code: $localVersionCode, Local name: $currentVersionName")

            val isNewer = if (remoteVersionCode > 0 && localVersionCode > 0) {
                remoteVersionCode > localVersionCode
            } else {
                isVersionStringNewer(cleanVersion, currentVersionName)
            }

            if (!isNewer) {
                Timber.d("App is up to date according to GitHub Releases")
                return@withContext null
            }

            // Find APK asset
            val assetsArray = json.optJSONArray("assets") ?: return@withContext null
            var apkDownloadUrl: String? = null

            for (i in 0 until assetsArray.length()) {
                val asset = assetsArray.getJSONObject(i)
                val assetName = asset.optString("name", "")
                if (assetName.endsWith(".apk", ignoreCase = true)) {
                    apkDownloadUrl = asset.optString("browser_download_url", "")
                    break
                }
            }

            if (apkDownloadUrl.isNullOrEmpty()) {
                Timber.w("GitHub release $tagName has no APK asset attached")
                return@withContext null
            }

            val body = json.optString("body", null)
            Timber.i("Found GitHub update: $cleanVersion -> $apkDownloadUrl")
            GitHubReleaseInfo(
                versionName = cleanVersion,
                versionCode = remoteVersionCode,
                downloadUrl = apkDownloadUrl,
                releaseNotes = body
            )
        } catch (e: Exception) {
            Timber.e(e, "Error checking GitHub releases")
            null
        }
    }

    /**
     * Parse semantic version string (e.g. "1.0.1") to integer code:
     * 1.0.1 -> 1 * 10000 + 0 * 100 + 1 = 10001
     */
    fun parseVersionToCode(version: String): Long {
        return try {
            val parts = version.split(".").map { it.filter { char -> char.isDigit() } }
            when (parts.size) {
                1 -> parts[0].toLong() * 10000
                2 -> parts[0].toLong() * 10000 + parts[1].toLong() * 100
                else -> parts[0].toLong() * 10000 + parts[1].toLong() * 100 + parts[2].toLong()
            }
        } catch (e: Exception) {
            0L
        }
    }

    private fun isVersionStringNewer(remote: String, local: String): Boolean {
        try {
            val remoteParts = remote.split(".").map { it.filter { c -> c.isDigit() }.toIntOrNull() ?: 0 }
            val localParts = local.split(".").map { it.filter { c -> c.isDigit() }.toIntOrNull() ?: 0 }
            val maxLen = maxOf(remoteParts.size, localParts.size)
            for (i in 0 until maxLen) {
                val r = remoteParts.getOrElse(i) { 0 }
                val l = localParts.getOrElse(i) { 0 }
                if (r > l) return true
                if (r < l) return false
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to compare version strings: $remote vs $local")
        }
        return false
    }

    /**
     * Schedules periodic background update checks using WorkManager (every 6 hours).
     */
    fun schedulePeriodicChecks(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<GitHubUpdateWorker>(
            repeatInterval = 6,
            repeatIntervalTimeUnit = TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
        Timber.i("Scheduled periodic GitHub update checks (every 6 hours)")
    }

    class GitHubUpdateWorker(
        appContext: Context,
        params: WorkerParameters
    ) : CoroutineWorker(appContext, params) {

        override suspend fun doWork(): Result {
            return try {
                val updateInfo = checkForUpdate(
                    currentVersionCode = com.pochipay.BuildConfig.VERSION_CODE,
                    currentVersionName = com.pochipay.BuildConfig.VERSION_NAME
                )
                if (updateInfo != null) {
                    Timber.i("Background check found new PochiPay update: ${updateInfo.versionName}")
                }
                Result.success()
            } catch (e: Exception) {
                Timber.e(e, "GitHubUpdateWorker failed")
                Result.retry()
            }
        }
    }
}
