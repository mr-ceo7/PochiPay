package com.pochipay.utils

import android.content.Context
import java.io.File
import timber.log.Timber

/**
 * Utility for managing update file caching.
 *
 * Caches downloaded update APKs to avoid re-downloading the same version. All cached files are
 * verified for signature before use.
 */
object UpdateCacheManager {

    private const val CACHE_DIR_NAME = "updates"
    private const val CACHE_FILE_PREFIX = "ntfy5-v"
    private const val CACHE_FILE_EXTENSION = ".apk"

    /** Get the cache directory for updates. Creates it if it doesn't exist. */
    private fun getCacheDirectory(context: Context): File {
        // Use externalCacheDir because DownloadManager requires it (or public dir)
        // and FileProvider is configured for external-cache-path.
        val baseDir = context.externalCacheDir ?: context.cacheDir
        val cacheDir = File(baseDir, CACHE_DIR_NAME)
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
            Timber.d("Created update cache directory: ${cacheDir.absolutePath}")
        }
        return cacheDir
    }

    /** Get the expected cache file for a specific version. */
    private fun getCacheFile(context: Context, versionName: String): File {
        return File(
                getCacheDirectory(context),
                "$CACHE_FILE_PREFIX$versionName$CACHE_FILE_EXTENSION"
        )
    }

    /**
     * Check if an update for the given version is already cached and valid.
     *
     * @param context Application context
     * @param versionName The version to check for
     * @return The cached file if it exists and has a valid signature, null otherwise
     */
    fun getCachedUpdate(context: Context, versionName: String): File? {
        val cacheFile = getCacheFile(context, versionName)

        if (!cacheFile.exists()) {
            Timber.d("No cached update found for version $versionName")
            return null
        }

        Timber.d("Found cached update file: ${cacheFile.name}")

        // Verify the cached file has a valid signature
        if (!ApkSignatureVerifier.verifyApkSignature(context, cacheFile)) {
            Timber.w("Cached update for version $versionName has invalid signature, deleting")
            cacheFile.delete()
            return null
        }

        val sizeInMB = cacheFile.length() / (1024 * 1024)
        Timber.i("Using valid cached update for version $versionName (${sizeInMB}MB)")
        return cacheFile
    }

    /**
     * Save a downloaded update to the cache.
     *
     * @param context Application context
     * @param sourceFile The downloaded file
     * @param versionName The version name for this update
     * @return The cached file, or null if caching failed
     */
    fun cacheUpdate(context: Context, sourceFile: File, versionName: String): File? {
        return try {
            val cacheFile = getCacheFile(context, versionName)

            // If source is already in the right location, just return it
            if (sourceFile.absolutePath == cacheFile.absolutePath) {
                Timber.d("File is already in cache location")
                return cacheFile
            }

            // Copy or move to cache location
            if (sourceFile.exists()) {
                sourceFile.copyTo(cacheFile, overwrite = true)
                val sizeInMB = cacheFile.length() / (1024 * 1024)
                Timber.i("Cached update for version $versionName (${sizeInMB}MB)")
                cacheFile
            } else {
                Timber.e("Source file doesn't exist: ${sourceFile.absolutePath}")
                null
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to cache update for version $versionName")
            null
        }
    }

    /**
     * Clean up old cached updates to free space. Keeps only the most recent cached update.
     *
     * @param context Application context
     * @param keepVersion The version to keep (usually the currently downloading one)
     */
    fun cleanOldUpdates(context: Context, keepVersion: String? = null) {
        try {
            val cacheDir = getCacheDirectory(context)
            val files =
                    cacheDir.listFiles { file ->
                        file.name.startsWith(CACHE_FILE_PREFIX) &&
                                file.name.endsWith(CACHE_FILE_EXTENSION)
                    }
                            ?: return

            var deletedCount = 0
            var freedSpace = 0L

            for (file in files) {
                // Don't delete the version we want to keep
                if (keepVersion != null && file.name.contains(keepVersion)) {
                    continue
                }

                val size = file.length()
                if (file.delete()) {
                    deletedCount++
                    freedSpace += size
                    Timber.d("Deleted old cached update: ${file.name}")
                }
            }

            if (deletedCount > 0) {
                val freedMB = freedSpace / (1024 * 1024)
                Timber.i("Cleaned up $deletedCount old update(s), freed ${freedMB}MB")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error cleaning old updates")
        }
    }

    /**
     * Get the total size of all cached updates.
     *
     * @param context Application context
     * @return Total size in bytes
     */
    fun getCacheSize(context: Context): Long {
        return try {
            val cacheDir = getCacheDirectory(context)
            val files =
                    cacheDir.listFiles { file ->
                        file.name.startsWith(CACHE_FILE_PREFIX) &&
                                file.name.endsWith(CACHE_FILE_EXTENSION)
                    }
                            ?: return 0L

            files.sumOf { it.length() }
        } catch (e: Exception) {
            Timber.e(e, "Error calculating cache size")
            0L
        }
    }

    /**
     * Clear all cached updates.
     *
     * @param context Application context
     */
    fun clearAllCache(context: Context) {
        try {
            val cacheDir = getCacheDirectory(context)
            val files =
                    cacheDir.listFiles { file ->
                        file.name.startsWith(CACHE_FILE_PREFIX) &&
                                file.name.endsWith(CACHE_FILE_EXTENSION)
                    }
                            ?: return

            var deletedCount = 0
            for (file in files) {
                if (file.delete()) {
                    deletedCount++
                }
            }

            if (deletedCount > 0) {
                Timber.i("Cleared all cached updates ($deletedCount files)")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error clearing cache")
        }
    }
}
