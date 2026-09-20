package com.pochipay.ui.messaging

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.pochipay.PochiPayApplication
import com.pochipay.R
import com.pochipay.databinding.FragmentConversationListBinding
import com.pochipay.viewmodels.MessagingViewModel
import com.pochipay.viewmodels.MessagingViewModelFactory
import kotlinx.coroutines.launch
import android.view.View
import android.widget.PopupMenu
import com.google.android.material.snackbar.Snackbar
import android.content.Intent
import android.text.TextUtils
import android.widget.Toast

class ConversationListFragment : Fragment() {

    private var _binding: FragmentConversationListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MessagingViewModel by viewModels {
        MessagingViewModelFactory(
            (requireActivity().application as PochiPayApplication).repository
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentConversationListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = ConversationListAdapter({ conversation ->
            // Mark conversation as opened now so the preview is no longer bold
            val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(requireContext())
            val key = "conversation_opened_${conversation.id}"
            prefs.edit().putLong(key, System.currentTimeMillis()).apply()

            val action =
                ConversationListFragmentDirections.actionConversationListFragmentToConversationDetailFragment(
                    conversation.id
                )
            findNavController().navigate(action)
        }, onLongClick = { conversation, anchorView ->
            val popup = PopupMenu(requireContext(), anchorView)
            popup.menu.add("Delete")
            popup.menu.add("Share")
            val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(requireContext())
            val key = "conversation_opened_${conversation.id}"
            val lastOpened = prefs.getLong(key, 0L)
            if (conversation.lastMessageTimestamp > lastOpened) {
                popup.menu.add("Mark as read")
            } else {
                popup.menu.add("Mark as unread")
            }

            popup.setOnMenuItemClickListener { menuItem ->
                when (menuItem.title) {
                    "Delete" -> {
                        // Delete and offer undo via Snackbar
                        viewLifecycleOwner.lifecycleScope.launch {
                            viewModel.deleteConversation(conversation.id) { backup ->
                                // show snackbar with undo
                                val root = binding.root
                                Snackbar.make(root, "Conversation deleted", Snackbar.LENGTH_LONG)
                                    .setAction("Undo") {
                                        if (backup != null) {
                                            viewModel.restoreConversation(backup)
                                            Toast.makeText(requireContext(), "Conversation restored", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                    .show()
                            }
                        }
                        true
                    }
                    "Share" -> {
                        viewLifecycleOwner.lifecycleScope.launch {
                            // Fallback: share the topic and last message (quick share)
                            val shareText = if (!TextUtils.isEmpty(conversation.lastMessage)) {
                                "${conversation.topic}: ${conversation.lastMessage}"
                            } else {
                                "${conversation.topic}"
                            }
                            val send = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, shareText)
                                type = "text/plain"
                            }
                            startActivity(Intent.createChooser(send, "Share conversation"))
                        }
                        true
                    }
                    "Mark as read" -> {
                        val prefs2 = androidx.preference.PreferenceManager.getDefaultSharedPreferences(requireContext())
                        val key2 = "conversation_opened_${conversation.id}"
                        prefs2.edit().putLong(key2, System.currentTimeMillis()).apply()
                        Toast.makeText(requireContext(), "Marked as read", Toast.LENGTH_SHORT).show()
                        true
                    }
                    "Mark as unread" -> {
                        val prefs2 = androidx.preference.PreferenceManager.getDefaultSharedPreferences(requireContext())
                        val key2 = "conversation_opened_${conversation.id}"
                        prefs2.edit().putLong(key2, 0L).apply()
                        Toast.makeText(requireContext(), "Marked as unread", Toast.LENGTH_SHORT).show()
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        })
        binding.recyclerViewConversations.adapter = adapter
        binding.recyclerViewConversations.layoutManager = LinearLayoutManager(requireContext())

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.getAllConversations().collect { conversations ->
                adapter.submitList(conversations)
            }
        }

        binding.fabNewConversation.setOnClickListener {
            // For now, let's just create a dummy conversation
            viewModel.createConversation("New Conversation", "Hello!", false)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
