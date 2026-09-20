package com.pochipay

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.pochipay.automation.BusinessStkAutomation
import com.pochipay.databinding.FragmentAutomationBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class AutomationFragment : Fragment() {

    private var _binding: FragmentAutomationBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAutomationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupLogs()
        setupListeners()
    }

    private fun setupLogs() {
        val initialLogs = StatusLogEngine.getLogs()
        if (initialLogs.isNotEmpty()) {
            binding.textLogs.text = initialLogs
            scrollToBottom()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            StatusLogEngine.logFlow.collectLatest { newLog ->
                binding.textLogs.append(newLog)
                scrollToBottom()
            }
        }
    }

    private fun scrollToBottom() {
        binding.scrollLogs.post {
            _binding?.scrollLogs?.fullScroll(View.FOCUS_DOWN)
        }
    }

    private fun setupListeners() {
        binding.buttonClearLogs.setOnClickListener {
            StatusLogEngine.clear()
            binding.textLogs.text = "Logs cleared.\n"
        }

        binding.buttonTriggerStk.setOnClickListener {
            val phone = binding.editPhoneNumber.text.toString().trim()
            val amountStr = binding.editAmount.text.toString().trim()

            if (phone.isEmpty()) {
                binding.editPhoneNumber.error = "Enter phone number"
                return@setOnClickListener
            }

            val amount = amountStr.toDoubleOrNull()
            if (amount == null || amount <= 0.0) {
                binding.editAmount.error = "Enter valid amount"
                return@setOnClickListener
            }

            // Check if accessibility service is running
            if (!MyAccessibilityService.isConnected) {
                Toast.makeText(
                    requireContext(),
                    "Please enable PochiPay in Accessibility Settings first",
                    Toast.LENGTH_LONG
                ).show()
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                startActivity(intent)
                return@setOnClickListener
            }

            // Execute Business STK Push
            binding.buttonTriggerStk.isEnabled = false
            binding.buttonTriggerStk.text = "DISPATCHING STK..."

            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val result = BusinessStkAutomation.execute(
                        context = requireContext().applicationContext,
                        phone = phone,
                        amount = amount
                    )

                    if (result.success) {
                        val nameStr = result.customerName?.let { " ($it)" } ?: ""
                        Toast.makeText(
                            requireContext(),
                            "STK Prompt dispatched successfully$nameStr",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        Toast.makeText(
                            requireContext(),
                            "Failed: ${result.errorMessage ?: "Unknown error"}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_LONG).show()
                } finally {
                    _binding?.buttonTriggerStk?.isEnabled = true
                    _binding?.buttonTriggerStk?.text = "DISPATCH STK PUSH"
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
