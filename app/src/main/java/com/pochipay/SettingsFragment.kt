package com.pochipay

import android.app.Activity
import android.app.role.RoleManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Telephony
import androidx.activity.result.contract.ActivityResultContracts
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import timber.log.Timber

class SettingsFragment : PreferenceFragmentCompat() {

    private val appPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val selectedAppPackage = result.data?.getStringExtra("selected_app_package")
            if (selectedAppPackage != null) {
                val sharedPreferences = preferenceManager.sharedPreferences
                sharedPreferences?.edit()?.putString("target_package", selectedAppPackage)?.apply()
                val targetPackagePreference = findPreference<Preference>("target_package")
                targetPackagePreference?.summary = selectedAppPackage
            }
        }
    }

    private val roleRequestLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            Timber.d("Successfully set as default SMS app")
        } else {
            Timber.d("User declined default SMS app request")
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)

        val targetPackagePreference = findPreference<Preference>("target_package")
        targetPackagePreference?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            val intent = Intent(requireContext(), AppPickerActivity::class.java)
            appPickerLauncher.launch(intent)
            true
        }

        val setDefaultSmsPreference = findPreference<Preference>("set_default_sms")
        setDefaultSmsPreference?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            setAsDefaultSmsApp()
            true
        }

        val logsPreference = findPreference<Preference>("view_logs")
        logsPreference?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            startActivity(Intent(requireContext(), LogsActivity::class.java))
            true
        }

        val diagnosticsPreference = findPreference<Preference>("diagnostics")
        diagnosticsPreference?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            val intent = Intent(requireContext(), DiagnosticsActivity::class.java)
            startActivity(intent)
            true
        }
    }

    private fun setAsDefaultSmsApp() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // For Android 10+, use RoleManager
            try {
                val roleManager = requireContext().getSystemService(RoleManager::class.java)
                if (roleManager != null) {
                    Timber.d("RoleManager available, checking SMS role...")
                    if (roleManager.isRoleAvailable(RoleManager.ROLE_SMS)) {
                        Timber.d("SMS role available, requesting...")
                        val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)
                        roleRequestLauncher.launch(intent)
                    } else {
                        Timber.w("SMS role not available on this device")
                    }
                } else {
                    Timber.w("RoleManager not available")
                }
            } catch (e: Exception) {
                Timber.e(e, "Error requesting SMS role")
            }
        } else {
            // For older versions, use the deprecated but still functional method
            val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
            intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, requireContext().packageName)
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        val sharedPreferences = preferenceManager.sharedPreferences
        val targetPackage = sharedPreferences?.getString("target_package", "")
        val targetPackagePreference = findPreference<Preference>("target_package")
        targetPackagePreference?.summary = targetPackage
    }
}
