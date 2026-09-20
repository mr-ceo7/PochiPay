package com.pochipay.ui.startup

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.pochipay.databinding.FragmentUpdatingBinding
import com.pochipay.viewmodels.StartupState
import com.pochipay.viewmodels.StartupViewModel
import kotlinx.coroutines.launch

class UpdatingFragment : Fragment() {

    private var _binding: FragmentUpdatingBinding? = null
    private val binding get() = _binding!!

    private val startupViewModel: StartupViewModel by activityViewModels()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // After returning from settings, re-trigger the check.
        // The onResume will trigger beginStartupChecks again, which is what we want.
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentUpdatingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        binding.permissionButton.setOnClickListener {
            requestInstallPermission()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            startupViewModel.startupState.collect { state ->
                handleState(state)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            startupViewModel.downloadProgress.collect { progress ->
                binding.updateProgressBar.progress = progress
                if (progress > 0 && progress < 100) {
                    binding.updateStatusText.text = "Downloading update... ($progress%)"
                }
            }
        }

        binding.installButton.setOnClickListener {
            // This button's actual logic is set in handleState when UpdateDownloadComplete is received.
            // This listener is a fallback or for clarity of intent.
        }
    }
    
    private fun handleState(state: StartupState) {
        when (state) {
            is StartupState.UpdateAvailable -> {
                binding.updateProgressBar.visibility = View.VISIBLE
                binding.updateStatusText.visibility = View.VISIBLE
                binding.updateStatusText.text = "Downloading update v${state.versionName}..."
                binding.updateProgressBar.progress = 0
                binding.installButton.visibility = View.GONE
                binding.permissionButton.visibility = View.GONE
                binding.permissionErrorText.visibility = View.GONE
                startupViewModel.downloadAndInstallUpdate(state.downloadUrl, state.versionName)
            }
            is StartupState.UpdateDownloadComplete -> {
                binding.updateProgressBar.progress = 100
                binding.updateProgressBar.visibility = View.VISIBLE
                binding.updateStatusText.visibility = View.VISIBLE
                binding.updateStatusText.text = "Download complete. Tap 'Install Update' to proceed."
                binding.installButton.visibility = View.VISIBLE
                binding.installButton.setOnClickListener {
                    startupViewModel.installUpdate(state.downloadId)
                }
                binding.permissionButton.visibility = View.GONE
                binding.permissionErrorText.visibility = View.GONE
            }
            is StartupState.UpdateDownloadFailed -> {
                binding.updateProgressBar.visibility = View.GONE
                binding.updateStatusText.visibility = View.VISIBLE
                binding.updateStatusText.text = "Update failed: ${state.reason}"
                binding.installButton.visibility = View.GONE
                binding.permissionButton.visibility = View.GONE
                binding.permissionErrorText.visibility = View.GONE
            }
            is StartupState.InstallPermissionNeeded -> {
                binding.updateProgressBar.visibility = View.GONE
                binding.updateStatusText.visibility = View.GONE
                binding.permissionErrorText.visibility = View.VISIBLE
                binding.permissionButton.visibility = View.VISIBLE
                binding.installButton.visibility = View.GONE
            }
            else -> {
                // Default state for this screen
                binding.updateProgressBar.visibility = View.VISIBLE
                binding.updateStatusText.visibility = View.VISIBLE
                binding.updateStatusText.text = "Checking for updates..."
                binding.installButton.visibility = View.GONE
                binding.permissionButton.visibility = View.GONE
                binding.permissionErrorText.visibility = View.GONE
            }
        }
    }

    private fun requestInstallPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${requireContext().packageName}")
            }
            requestPermissionLauncher.launch(intent)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}