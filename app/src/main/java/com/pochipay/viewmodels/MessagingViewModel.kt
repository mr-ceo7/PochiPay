package com.pochipay.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pochipay.data.Conversation
import com.pochipay.data.Message
import com.pochipay.data.Repository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class MessagingViewModel(private val repository: Repository) : ViewModel() {

    fun getAllConversations(): Flow<List<Conversation>> {
        return repository.getAllConversations()
    }

    fun getConversation(conversationId: Long): Flow<Conversation> {
        return repository.getConversation(conversationId)
    }

    fun getMessagesForConversation(conversationId: Long): Flow<List<Message>> {
        return repository.getMessagesForConversation(conversationId)
    }

    fun sendMessage(conversationId: Long, message: String, isFromUser: Boolean) {
        viewModelScope.launch {
            repository.sendMessage(conversationId, message, isFromUser)
        }
    }

    fun deleteMessage(messageId: Long) {
        viewModelScope.launch {
            repository.deleteMessage(messageId)
        }
    }

    /**
     * Delete a conversation and provide its backup to the caller via the callback so it can be restored.
     */
    fun deleteConversation(conversationId: Long, onDeleted: (Repository.ConversationBackup?) -> Unit) {
        viewModelScope.launch {
            val backup = repository.deleteConversation(conversationId)
            onDeleted(backup)
        }
    }

    fun restoreConversation(backup: Repository.ConversationBackup) {
        viewModelScope.launch {
            repository.restoreConversation(backup)
        }
    }

    fun createConversation(topic: String, message: String, isFromUser: Boolean) {
        viewModelScope.launch {
            repository.createConversation(topic, message, isFromUser)
        }
    }
}

class MessagingViewModelFactory(private val repository: Repository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MessagingViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MessagingViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
