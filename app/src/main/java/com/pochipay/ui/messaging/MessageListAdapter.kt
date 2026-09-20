package com.pochipay.ui.messaging

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.graphics.Color
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.pochipay.data.Message
import com.pochipay.databinding.ListItemMessageOtherBinding
import com.pochipay.databinding.ListItemMessageUserBinding

private const val VIEW_TYPE_USER = 1
private const val VIEW_TYPE_OTHER = 2

class MessageListAdapter :
    ListAdapter<Message, RecyclerView.ViewHolder>(MessageDiffCallback) {

    // Id of the currently selected message (for long-click highlighting)
    var selectedMessageId: Long? = null
        private set

    // Callback invoked when a message is long-clicked. The view parameter is the itemView anchor for popups.
    var onMessageLongClick: ((Message, View) -> Unit)? = null

    fun setSelectedMessageId(id: Long?) {
        selectedMessageId = id
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return if (getItem(position).isFromUser) {
            VIEW_TYPE_USER
        } else {
            VIEW_TYPE_OTHER
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_USER) {
            val binding = ListItemMessageUserBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            UserMessageViewHolder(binding)
        } else {
            val binding = ListItemMessageOtherBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            OtherMessageViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = getItem(position)
        if (holder is UserMessageViewHolder) {
            holder.bind(message)
        } else if (holder is OtherMessageViewHolder) {
            holder.bind(message)
        }
    }

    inner class UserMessageViewHolder(private val binding: ListItemMessageUserBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(message: Message) {
            binding.textViewMessage.text = message.message

            // Highlight if selected
            if (message.id == selectedMessageId) {
                binding.root.setBackgroundColor(ContextCompat.getColor(binding.root.context, android.R.color.holo_blue_light))
            } else {
                binding.root.setBackgroundColor(Color.TRANSPARENT)
            }

            binding.root.setOnLongClickListener {
                onMessageLongClick?.invoke(message, it)
                true
            }
        }
    }

    inner class OtherMessageViewHolder(private val binding: ListItemMessageOtherBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(message: Message) {
            binding.textViewMessage.text = message.message

            // Highlight if selected
            if (message.id == selectedMessageId) {
                binding.root.setBackgroundColor(ContextCompat.getColor(binding.root.context, android.R.color.holo_blue_light))
            } else {
                binding.root.setBackgroundColor(Color.TRANSPARENT)
            }

            binding.root.setOnLongClickListener {
                onMessageLongClick?.invoke(message, it)
                true
            }
        }
    }
}

object MessageDiffCallback : DiffUtil.ItemCallback<Message>() {
    override fun areItemsTheSame(oldItem: Message, newItem: Message): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: Message, newItem: Message): Boolean {
        return oldItem == newItem
    }
}
