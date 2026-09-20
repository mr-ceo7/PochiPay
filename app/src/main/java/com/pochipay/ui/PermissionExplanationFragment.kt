package com.pochipay.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.pochipay.databinding.FragmentPermissionExplanationBinding

class PermissionExplanationFragment : Fragment() {

    private var _binding: FragmentPermissionExplanationBinding? = null
    private val binding
        get() = _binding!!

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPermissionExplanationBinding.inflate(inflater, container, false)
        return binding.root
    }

    private val standardPermissions =
            mutableListOf(
                            android.Manifest.permission.RECEIVE_SMS,
                            android.Manifest.permission.READ_SMS,
                            android.Manifest.permission.SEND_SMS,
                            android.Manifest.permission.READ_CONTACTS
                    )
                    .apply {
                        if (android.os.Build.VERSION.SDK_INT >=
                                        android.os.Build.VERSION_CODES.TIRAMISU
                        ) {
                            add(android.Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                    .toTypedArray()

    private val requestStandardPermissionsLauncher =
            registerForActivityResult(
                    androidx.activity.result.contract.ActivityResultContracts
                            .RequestMultiplePermissions()
            ) { permissions -> checkPermissions() }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupClickListeners()
        checkPermissions()
    }

    override fun onResume() {
        super.onResume()
        // Re-check permissions when returning from settings
        checkPermissions()
    }

    private fun setupClickListeners() {
        binding.btnGrantAccessibility.setOnClickListener { requestAccessibilityPermission() }

        binding.btnGrantOverlay.setOnClickListener { requestOverlayPermission() }

        binding.btnGrantStandard.setOnClickListener {
            requestStandardPermissionsLauncher.launch(standardPermissions)
        }

        binding.btnContinue.setOnClickListener {
            // Navigate to next screen or finish
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun checkPermissions() {
        val context = requireContext()

        // Check Standard Permissions
        val areStandardGranted = hasStandardPermissions(context)
        updateStandardStatus(areStandardGranted)

        // Check Overlay Permission
        val isOverlayGranted = Settings.canDrawOverlays(context)
        updateOverlayStatus(isOverlayGranted)

        // Check Accessibility Permission
        val isAccessibilityGranted =
                isAccessibilityServiceEnabled(
                        context,
                        com.pochipay.MyAccessibilityService::class.java
                )
        updateAccessibilityStatus(isAccessibilityGranted)

        // Enable Continue button only if ALL are granted
        binding.btnContinue.isEnabled =
                isOverlayGranted && isAccessibilityGranted && areStandardGranted
        binding.btnContinue.alpha = if (binding.btnContinue.isEnabled) 1.0f else 0.5f
    }

    private fun hasStandardPermissions(context: Context): Boolean {
        return standardPermissions.all {
            androidx.core.content.ContextCompat.checkSelfPermission(context, it) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }

    private fun updateStandardStatus(isGranted: Boolean) {
        if (isGranted) {
            binding.standardStatus.text = "Granted"
            binding.standardStatus.setTextColor(
                    requireContext().getColor(com.pochipay.R.color.accent_green)
            )
            binding.btnGrantStandard.isEnabled = false
            binding.btnGrantStandard.text = "Enabled"
        } else {
            binding.standardStatus.text = "Not Granted"
            binding.standardStatus.setTextColor(
                    requireContext().getColor(com.pochipay.R.color.pink_500)
            )
            binding.btnGrantStandard.isEnabled = true
            binding.btnGrantStandard.text = "Grant Permissions"
        }
    }

    private fun updateOverlayStatus(isGranted: Boolean) {
        if (isGranted) {
            binding.overlayStatus.text = "Granted"
            binding.overlayStatus.setTextColor(
                    requireContext().getColor(com.pochipay.R.color.accent_green)
            )
            binding.btnGrantOverlay.isEnabled = false
            binding.btnGrantOverlay.text = "Enabled"
        } else {
            binding.overlayStatus.text = "Not Granted"
            binding.overlayStatus.setTextColor(
                    requireContext().getColor(com.pochipay.R.color.pink_500)
            )
            binding.btnGrantOverlay.isEnabled = true
            binding.btnGrantOverlay.text = "Allow Overlay"
        }
    }

    private fun updateAccessibilityStatus(isGranted: Boolean) {
        if (isGranted) {
            binding.accessibilityStatus.text = "Granted"
            binding.accessibilityStatus.setTextColor(
                    requireContext().getColor(com.pochipay.R.color.accent_green)
            )
            binding.btnGrantAccessibility.isEnabled = false
            binding.btnGrantAccessibility.text = "Enabled"
        } else {
            binding.accessibilityStatus.text = "Not Granted"
            binding.accessibilityStatus.setTextColor(
                    requireContext().getColor(com.pochipay.R.color.pink_500)
            )
            binding.btnGrantAccessibility.isEnabled = true
            binding.btnGrantAccessibility.text = "Enable Service"
        }
    }

    private fun requestAccessibilityPermission() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
    }

    private fun requestOverlayPermission() {
        val intent =
                Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${requireContext().packageName}")
                )
        startActivity(intent)
    }

    private fun isAccessibilityServiceEnabled(
            context: Context,
            service: Class<out android.accessibilityservice.AccessibilityService>
    ): Boolean {
        val am =
                context.getSystemService(Context.ACCESSIBILITY_SERVICE) as
                        android.view.accessibility.AccessibilityManager
        val enabledServices =
                am.getEnabledAccessibilityServiceList(
                        android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK
                )

        for (enabledService in enabledServices) {
            val serviceInfo = enabledService.resolveInfo.serviceInfo
            if (serviceInfo.packageName == context.packageName && serviceInfo.name == service.name
            ) {
                return true
            }
        }
        return false
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
