package com.pochipay.ui.startup

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.pochipay.databinding.FragmentRegisteringBinding
import com.pochipay.viewmodels.StartupState
import com.pochipay.viewmodels.StartupViewModel
import kotlinx.coroutines.launch

class RegisteringFragment : Fragment() {

    private var _binding: FragmentRegisteringBinding? = null
    private val binding
        get() = _binding!!

    private val startupViewModel: StartupViewModel by activityViewModels()

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRegisteringBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.retryButton.setOnClickListener { startupViewModel.retryStartup() }

        viewLifecycleOwner.lifecycleScope.launch {
            startupViewModel.startupState.collect { state -> handleState(state) }
        }
    }

    private fun handleState(state: StartupState) {
        when (state) {
            is StartupState.RegistrationNeeded, is StartupState.Loading -> {
                binding.progressBar.visibility = View.VISIBLE
                binding.statusText.visibility = View.VISIBLE
                binding.statusText.text = "Registering device..."
                binding.errorText.visibility = View.GONE
                binding.retryButton.visibility = View.GONE
            }
            is StartupState.NetworkError -> {
                binding.progressBar.visibility = View.GONE
                binding.statusText.visibility = View.GONE
                binding.errorText.visibility = View.VISIBLE
                binding.retryButton.visibility = if (state.canRetry) View.VISIBLE else View.GONE
                binding.errorText.text = state.message
            }
            is StartupState.ServerError -> {
                binding.progressBar.visibility = View.GONE
                binding.statusText.visibility = View.GONE
                binding.errorText.visibility = View.VISIBLE
                binding.retryButton.visibility = View.VISIBLE
                binding.errorText.text = state.message
            }
            is StartupState.StartupComplete -> {
                // MainActivity will handle navigation
            }
            else -> {
                // Ignore other states as they are handled by MainActivity or other fragments
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
