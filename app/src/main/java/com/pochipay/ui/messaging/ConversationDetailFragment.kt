package com.pochipay.ui.messaging

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.pochipay.PochiPayApplication
import com.pochipay.databinding.FragmentConversationDetailBinding
import com.pochipay.viewmodels.MessagingViewModel
import com.pochipay.viewmodels.MessagingViewModelFactory
import kotlinx.coroutines.launch
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.PopupMenu
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.appcompat.app.AppCompatActivity
import com.pochipay.R

import android.content.Intent
import com.pochipay.AutomationEngine
import com.pochipay.data.AutomationCommand
import com.pochipay.utils.ToastHelper
import com.pochipay.viewmodels.AutomationEvent
import kotlinx.coroutines.flow.collectLatest

import androidx.fragment.app.activityViewModels
import com.pochipay.OverlayService
import com.pochipay.viewmodels.SharedViewModel

class ConversationDetailFragment : Fragment() {

    private var _binding: FragmentConversationDetailBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MessagingViewModel by viewModels {
        MessagingViewModelFactory(
            (requireActivity().application as PochiPayApplication).repository
        )
    }
    private val sharedViewModel: SharedViewModel by activityViewModels()

    private val args: ConversationDetailFragmentArgs by navArgs()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentConversationDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = MessageListAdapter()
        binding.recyclerViewMessages.adapter = adapter
        binding.recyclerViewMessages.layoutManager = LinearLayoutManager(requireContext())

        // Handle long-clicks from the adapter: show copy/delete options and highlight selection
        adapter.onMessageLongClick = { message, anchorView ->
            // toggle selection highlight
            if (adapter.selectedMessageId == message.id) {
                adapter.setSelectedMessageId(null)
            } else {
                adapter.setSelectedMessageId(message.id)
            }

            val popup = PopupMenu(requireContext(), anchorView)
            popup.menu.add("Copy")
            popup.menu.add("QR")
            popup.menu.add("Delete")
            popup.menu.add("Push") // Add "Push" option
            popup.setOnMenuItemClickListener { menuItem ->
                when (menuItem.title) {
                    "Copy" -> {
                        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("message", message.message)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(requireContext(), "Copied to clipboard", Toast.LENGTH_SHORT).show()
                        true
                    }
                    "Delete" -> {
                        viewModel.deleteMessage(message.id)
                        adapter.setSelectedMessageId(null)
                        Toast.makeText(requireContext(), "Message deleted", Toast.LENGTH_SHORT).show()
                        true
                    }
                    "QR" -> {


                        Toast.makeText(requireContext(), "Coming soon...", Toast.LENGTH_SHORT).show()

                        true
                    }
                    "Push" -> {

                        sharedViewModel.rerunLastAutomation()
                        Toast.makeText(requireContext(), "Pushing transaction...", Toast.LENGTH_SHORT).show()
                        viewModel.deleteMessage(message.id)
                        adapter.setSelectedMessageId(null)
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.getMessagesForConversation(args.conversationId).collect { messages ->
                // submit list and scroll to last item (bottom)
                adapter.submitList(messages) {
                    binding.recyclerViewMessages.post {
                        if (adapter.itemCount > 0) {
                            binding.recyclerViewMessages.scrollToPosition(adapter.itemCount - 1)
                        }
                    }
                }
            }
        }

        // Fetch conversation to check topic
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.getConversation(args.conversationId).collect { conversation ->
                // Set the action bar title to the conversation topic
                (activity as? AppCompatActivity)?.supportActionBar?.title = conversation.topic

                if (conversation.topic.equals("MPESA", ignoreCase = true)||conversation.topic.equals("safaricom", ignoreCase = true)||conversation.topic.equals("SAF_Balance", ignoreCase = true)||conversation.topic.equals("SAFARICOM25", ignoreCase = true)||conversation.topic.equals("SAF_555", ignoreCase = true)) {
                    binding.editTextMessage.isEnabled = false
                    binding.editTextMessage.visibility = View.GONE
                    binding.buttonSend.isEnabled = false
                    binding.buttonSend.visibility = View.GONE
                    binding.textMpesaIndicator.visibility = View.VISIBLE
                } else {
                    binding.editTextMessage.isEnabled = true
                    binding.editTextMessage.visibility = View.VISIBLE
                    binding.buttonSend.isEnabled = true
                    binding.buttonSend.visibility = View.VISIBLE
                    binding.textMpesaIndicator.visibility = View.GONE
                }
            }
        }


        binding.buttonSend.setOnClickListener {
            val message = binding.editTextMessage.text.toString()
            if (message.isNotBlank()) {
                viewModel.sendMessage(args.conversationId, message, true)
                binding.editTextMessage.text.clear()
            }
        }

        observeSharedViewModel()
    }

    private fun observeSharedViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            sharedViewModel.events.collectLatest { event ->
                when (event) {
                    is AutomationEvent.ShowToast -> ToastHelper.showInfo(requireContext(), event.message)
                    is AutomationEvent.ShowError -> ToastHelper.showError(requireContext(), event.message)
                    is AutomationEvent.StartOverlayService -> requireContext().startService(Intent(requireContext(), OverlayService::class.java))
                    is AutomationEvent.LaunchApp -> {
                        val intent = requireContext().packageManager.getLaunchIntentForPackage(event.packageName)
                        if (intent != null) {
                            startActivity(intent)
                        } else {
                            // This fragment doesn't have a statusLog binding, so use ToastHelper
                            ToastHelper.showError(requireContext(), "App not found: ${event.packageName} (no launch intent)")
                        }
                    }
                    is AutomationEvent.CreateConversation -> AutomationEngine.sendCommand(AutomationCommand.createConversation(event.message))
                    else -> {
                        // Handle other automation events if necessary, or ignore them
                        // For ReturnToApp, this fragment does not need to do anything specific.
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Restore default title
        (activity as? AppCompatActivity)?.supportActionBar?.title = "Conversations"
        _binding = null
    }
}
