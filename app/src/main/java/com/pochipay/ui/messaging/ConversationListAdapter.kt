package com.pochipay.ui.messaging

import android.view.LayoutInflater
import android.view.ViewGroup
import android.graphics.Typeface
import android.text.format.DateUtils
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.pochipay.data.Conversation
import com.pochipay.databinding.ListItemConversationBinding
import com.pochipay.utils.AvatarGenerator
import androidx.navigation.findNavController
import androidx.preference.PreferenceManager

class ConversationListAdapter(
    private val onClick: (Conversation) -> Unit,
    private val onLongClick: ((Conversation, android.view.View) -> Unit)? = null
) : ListAdapter<Conversation, ConversationListAdapter.ConversationViewHolder>(ConversationDiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ConversationViewHolder {
        val binding = ListItemConversationBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ConversationViewHolder(binding, onClick, onLongClick)
    }

    override fun onBindViewHolder(holder: ConversationViewHolder, position: Int) {
        val conversation = getItem(position)
        holder.bind(conversation)
    }

    class ConversationViewHolder(
        private val binding: ListItemConversationBinding,
        private val onClick: (Conversation) -> Unit,
        private val onLongClick: ((Conversation, android.view.View) -> Unit)?
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(conversation: Conversation) {
            binding.textViewTopic.text = conversation.topic
            binding.textViewLastMessage.text = previewText(conversation.lastMessage)

            // Show relative timestamp (e.g., "2h ago")
            val ts = conversation.lastMessageTimestamp
            if (ts > 0L) {
                binding.textViewTimestamp.text = DateUtils.getRelativeTimeSpanString(ts)
            } else {
                binding.textViewTimestamp.text = ""
            }

            // Generate and display avatar
            val avatar = AvatarGenerator.generateAvatar(conversation.topic, 112)
            binding.imageViewAvatar.setImageBitmap(avatar)

            // Determine if conversation is unread by comparing lastMessageTimestamp with last opened timestamp stored in prefs
            val prefs = PreferenceManager.getDefaultSharedPreferences(binding.root.context)
            val key = "conversation_opened_${conversation.id}"
            val lastOpened = prefs.getLong(key, 0L)
            val isUnread = conversation.lastMessageTimestamp > lastOpened
            if (isUnread) {
                binding.textViewLastMessage.setTypeface(null, Typeface.BOLD)
            } else {
                binding.textViewLastMessage.setTypeface(null, Typeface.NORMAL)
            }
            var counter = 0
            binding.textViewTopic.setOnClickListener {
                counter++
                if (counter == 2) {
                    counter=0
                it.findNavController().navigate(com.pochipay.R.id.action_conversationListFragment_to_automationFragment)
            }}
            itemView.setOnClickListener {counter=0
                onClick(conversation)
            }

            itemView.setOnLongClickListener {counter=0
                onLongClick?.invoke(conversation, it)
                true
            }
        }

        private fun previewText(text: String?, maxLen: Int = 80): String? {
            if (text == null) return null
            // collapse whitespace/newlines to single space
            val collapsed = text.replace(Regex("\\s+"), " ").trim()
            return if (collapsed.length <= maxLen) collapsed else collapsed.substring(0, maxLen).trimEnd() + "…"
        }
    }
}

object ConversationDiffCallback : DiffUtil.ItemCallback<Conversation>() {
    override fun areItemsTheSame(oldItem: Conversation, newItem: Conversation): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: Conversation, newItem: Conversation): Boolean {
        return oldItem == newItem
    }
}
