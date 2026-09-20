package com.pochipay.utils

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import timber.log.Timber

/**
 * Utility for verifying APK signatures.
 *
 * This is critical for self-updating apps to ensure that downloaded APK files are signed with the
 * same certificate as the currently installed app, preventing installation of tampered or malicious
 * updates.
 */
object ApkSignatureVerifier {

    /**
     * Verify that an APK file has a valid signature matching the current app.
     *
     * @param context Application context
     * @param apkFile The APK file to verify
     * @return true if signatures match, false otherwise
     */
    fun verifyApkSignature(context: Context, apkFile: File): Boolean {
        return try {
            if (!apkFile.exists()) {
                Timber.e("APK file does not exist: ${apkFile.absolutePath}")
                return false
            }

            val packageManager = context.packageManager

            // Get package info for the APK file
            val apkPackageInfo = getPackageArchiveInfo(packageManager, apkFile.absolutePath)

            if (apkPackageInfo == null) {
                Timber.e("Failed to get package info for APK: ${apkFile.absolutePath}")
                return false
            }

            // Verify it's the same package
            if (apkPackageInfo.packageName != context.packageName) {
                Timber.e(
                        "Package name mismatch. APK: ${apkPackageInfo.packageName}, Current: ${context.packageName}"
                )
                return false
            }

            // Get current app signatures
            val currentSignatures = getCurrentAppSignatures(packageManager, context.packageName)

            // Get APK signatures
            val apkSignatures = getApkSignatures(apkPackageInfo)

            // Compare signatures
            val signaturesMatch = currentSignatures.contentEquals(apkSignatures)

            if (signaturesMatch) {
                Timber.i("APK signature verification PASSED for ${apkFile.name}")
            } else {
                Timber.e("APK signature verification FAILED - signatures don't match")
                Timber.e(
                        "Current app has ${currentSignatures.size} signatures, APK has ${apkSignatures.size}"
                )
            }

            signaturesMatch
        } catch (e: Exception) {
            Timber.e(e, "Error verifying APK signature")
            false
        }
    }

    @Suppress("DEPRECATION")
    private fun getPackageArchiveInfo(pm: PackageManager, apkPath: String): PackageInfo? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageArchiveInfo(
                    apkPath,
                    PackageManager.PackageInfoFlags.of(
                            PackageManager.GET_SIGNING_CERTIFICATES.toLong()
                    )
            )
        } else {
            pm.getPackageArchiveInfo(apkPath, PackageManager.GET_SIGNATURES)
        }
    }

    @Suppress("DEPRECATION")
    private fun getCurrentAppSignatures(
            pm: PackageManager,
            packageName: String
    ): Array<android.content.pm.Signature> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val packageInfo =
                    pm.getPackageInfo(
                            packageName,
                            PackageManager.PackageInfoFlags.of(
                                    PackageManager.GET_SIGNING_CERTIFICATES.toLong()
                            )
                    )
            packageInfo.signingInfo?.apkContentsSigners ?: emptyArray()
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val packageInfo =
                    pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            packageInfo.signingInfo?.apkContentsSigners ?: emptyArray()
        } else {
            val packageInfo = pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
            packageInfo.signatures ?: emptyArray()
        }
    }

    @Suppress("DEPRECATION")
    private fun getApkSignatures(packageInfo: PackageInfo): Array<android.content.pm.Signature> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.signingInfo?.apkContentsSigners ?: emptyArray()
        } else {
            packageInfo.signatures ?: emptyArray()
        }
    }
}
