package com.pochipay

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

class DiagnosticsActivity : AppCompatActivity() {

    private lateinit var resultView: TextView
    private lateinit var btnRequestPermissions: Button
    private lateinit var btnRecheck: Button

    private val permissionsToRequest by lazy {
        val list = mutableListOf<String>()
        list.add(Manifest.permission.RECEIVE_SMS)
        list.add(Manifest.permission.READ_SMS)
        list.add(Manifest.permission.READ_CONTACTS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        list.toTypedArray()
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        Timber.d("Permissions request result: $results")
        updateChecks()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_diagnostics)

        resultView = findViewById(R.id.diagnostics_text)
        btnRequestPermissions = findViewById(R.id.btn_request_permissions)
        btnRecheck = findViewById(R.id.btn_recheck)

        resultView.movementMethod = ScrollingMovementMethod()

        btnRequestPermissions.setOnClickListener {
            permissionLauncher.launch(permissionsToRequest)
        }

        btnRecheck.setOnClickListener {
            updateChecks()
        }

        updateChecks()
    }

    private fun updateChecks() {
        CoroutineScope(Dispatchers.Default).launch {
            val sb = StringBuilder()
            val pkg = packageName
            val pm = packageManager

            sb.append("Package: $pkg\n\n")

            // Required intent filters / receivers
            sb.append("-- Intent filters / Receivers --\n")

            // SMS_RECEIVED
            val smsReceived = checkBroadcastReceiver(pm, "android.provider.Telephony.SMS_RECEIVED")
            sb.append("SMS_RECEIVED receiver: ${if (smsReceived) "PRESENT" else "MISSING"}\n")

            // SMS_DELIVER
            val smsDeliver = checkBroadcastReceiver(pm, "android.provider.Telephony.SMS_DELIVER")
            sb.append("SMS_DELIVER receiver: ${if (smsDeliver) "PRESENT" else "MISSING"}\n")

            // WAP_PUSH_RECEIVED
            val wapPushReceived = checkBroadcastReceiver(pm, "android.provider.Telephony.WAP_PUSH_RECEIVED", "application/vnd.wap.mms-message")
            sb.append("WAP_PUSH_RECEIVED receiver: ${if (wapPushReceived) "PRESENT" else "MISSING"}\n")

            // WAP_PUSH_DELIVER
            val wapPushDeliver = checkBroadcastReceiver(pm, "android.provider.Telephony.WAP_PUSH_DELIVER", "application/vnd.wap.mms-message")
            sb.append("WAP_PUSH_DELIVER receiver: ${if (wapPushDeliver) "PRESENT" else "MISSING"}\n")

            // Activity intent filters for SENDTO/SEND
            sb.append("\n-- Activity Intent Filters --\n")
            val sendTo = checkActivityForScheme(pm, "sms:")
            sb.append("Handles sms: scheme (SENDTO): ${if (sendTo) "YES" else "NO"}\n")

            val sendAction = checkActivityForAction(pm, Intent.ACTION_SEND)
            sb.append("Handles ACTION_SEND text/plain: ${if (sendAction) "YES" else "NO"}\n")

            // Permissions declared and granted
            sb.append("\n-- Permissions (declared / granted) --\n")
            val requiredPerms = listOf(
                "android.permission.RECEIVE_SMS",
                "android.permission.BROADCAST_SMS",
                "android.permission.RECEIVE_WAP_PUSH",
                "android.permission.BROADCAST_WAP_PUSH",
                "android.permission.READ_SMS",
                "android.permission.WRITE_SMS",
                "android.permission.READ_CONTACTS",
                "android.permission.POST_NOTIFICATIONS"
            )

            val pkgInfo = pm.getPackageInfo(pkg, PackageManager.GET_PERMISSIONS)
            val declared = pkgInfo.requestedPermissions?.toSet() ?: emptySet()

            for (p in requiredPerms) {
                val declaredStr = if (declared.contains(p)) "Declared" else "Not declared"
                val grantedStr = try {
                    val g = ContextCompat.checkSelfPermission(this@DiagnosticsActivity, p) == PackageManager.PERMISSION_GRANTED
                    if (g) "Granted" else "Not granted"
                } catch (e: Exception) {
                    "N/A"
                }
                sb.append("$p : $declaredStr / $grantedStr\n")
            }

            withContext(Dispatchers.Main) {
                resultView.text = sb.toString()
            }
        }
    }

    private fun checkBroadcastReceiver(pm: PackageManager, action: String, mimeType: String? = null): Boolean {
        return try {
            val intent = Intent(action)
            if (mimeType != null) {
                intent.setDataAndType(Uri.parse("content://mms"), mimeType)
            }
            val list = pm.queryBroadcastReceivers(intent, PackageManager.GET_RESOLVED_FILTER)
            list.any { it.activityInfo.packageName == packageName }
        } catch (e: Exception) {
            Timber.e(e, "Error checking broadcast receiver for $action")
            false
        }
    }

    private fun checkActivityForScheme(pm: PackageManager, scheme: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse(scheme))
            val list = pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            list.any { it.activityInfo.packageName == packageName }
        } catch (e: Exception) {
            false
        }
    }

    private fun checkActivityForAction(pm: PackageManager, action: String): Boolean {
        return try {
            val intent = Intent(action).setType("text/plain")
            val list = pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            list.any { it.activityInfo.packageName == packageName }
        } catch (e: Exception) {
            false
        }
    }
}
