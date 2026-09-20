package com.pochipay.ui.startup

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.pochipay.databinding.FragmentAuthorizationBinding
import com.pochipay.viewmodels.StartupState
import com.pochipay.viewmodels.StartupViewModel
import kotlinx.coroutines.launch

class AuthorizationFragment : Fragment() {

    private var _binding: FragmentAuthorizationBinding? = null
    private val binding get() = _binding!!

    private val startupViewModel: StartupViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAuthorizationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.authSubmitButton.setOnClickListener {
            val authCode = binding.authCodeInput.text.toString()
            if (authCode.isNotBlank()) {
                binding.authErrorText.visibility = View.GONE
                startupViewModel.verifyAuthCode(authCode)
            } else {
                binding.authErrorText.text = "Auth code cannot be empty"
                binding.authErrorText.visibility = View.VISIBLE
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            startupViewModel.startupState.collect { state ->
                handleState(state)
            }
        }
    }

    private fun handleState(state: StartupState) {
        when (state) {
            is StartupState.Loading -> {
                binding.authProgressBar.visibility = View.VISIBLE
                binding.authSubmitButton.isEnabled = false
                binding.authCodeInput.isEnabled = false
                binding.authErrorText.visibility = View.GONE
            }
            is StartupState.AuthorizationNeeded -> {
                binding.authProgressBar.visibility = View.GONE
                binding.authSubmitButton.isEnabled = true
                binding.authCodeInput.isEnabled = true
                binding.authErrorText.visibility = View.GONE
            }
            is StartupState.NetworkError -> {
                binding.authProgressBar.visibility = View.GONE
                binding.authSubmitButton.isEnabled = true
                binding.authCodeInput.isEnabled = true
                binding.authErrorText.visibility = View.VISIBLE
                binding.authErrorText.text = state.message
            }
            is StartupState.AuthError -> {
                binding.authProgressBar.visibility = View.GONE
                binding.authSubmitButton.isEnabled = true
                binding.authCodeInput.isEnabled = true
                binding.authErrorText.visibility = View.VISIBLE
                binding.authErrorText.text = state.message
            }
            else -> {
                // Other states are handled by MainActivity or other fragments
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
