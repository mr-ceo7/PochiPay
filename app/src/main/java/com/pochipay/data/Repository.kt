package com.pochipay.data

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class Repository(private val appDatabase: AppDatabase) {

    data class ConversationBackup(
        val conversation: Conversation,
        val messages: List<Message>
    )

    fun getAllConversations(): Flow<List<Conversation>> {
        return appDatabase.conversationDao().getAllConversations()
    }

    fun getConversation(conversationId: Long): Flow<Conversation> {
        return appDatabase.conversationDao().getConversation(conversationId)
    }

    fun getMessagesForConversation(conversationId: Long): Flow<List<Message>> {
        return appDatabase.messageDao().getMessagesForConversation(conversationId)
    }

    suspend fun sendMessage(conversationId: Long, message: String, isFromUser: Boolean) {
        val timestamp = System.currentTimeMillis()
        val messageEntity = Message(
            conversationId = conversationId,
            timestamp = timestamp,
            message = message,
            isFromUser = isFromUser
        )
        appDatabase.messageDao().insert(messageEntity)

        val conversation = appDatabase.conversationDao().getConversation(conversationId).first()
        appDatabase.conversationDao().update(
            conversation.copy(
                lastMessage = message,
                lastMessageTimestamp = timestamp
            )
        )
    }

    suspend fun createConversation(topic: String, message: String, isFromUser: Boolean): Long {
        // If a conversation with the same topic already exists, append the message to it
        val existing = appDatabase.conversationDao().getConversationByTopic(topic)
        if (existing != null) {
            // Append message to existing conversation
            sendMessage(existing.id, message, isFromUser)
            return existing.id
        }

        val timestamp = System.currentTimeMillis()
        val conversation = Conversation(
            topic = topic,
            lastMessage = message,
            lastMessageTimestamp = timestamp
        )
        val conversationId = appDatabase.conversationDao().insert(conversation)
        sendMessage(conversationId, message, isFromUser)
        return conversationId
    }

    suspend fun deleteMessage(messageId: Long) {
        val message = appDatabase.messageDao().getMessageById(messageId) ?: return
        val conversationId = message.conversationId

        appDatabase.messageDao().deleteById(messageId)

        // Update conversation last message info to the newest remaining message, or clear if none left
        val latest = appDatabase.messageDao().getLatestMessageForConversation(conversationId)
        val conversation = appDatabase.conversationDao().getConversation(conversationId).first()
        if (latest != null) {
            appDatabase.conversationDao().update(
                conversation.copy(
                    lastMessage = latest.message,
                    lastMessageTimestamp = latest.timestamp
                )
            )
        } else {
            appDatabase.conversationDao().update(
                conversation.copy(
                    lastMessage = "",
                    lastMessageTimestamp = 0L
                )
            )
        }
    }

    /**
     * Attempt to extract the latest M-PESA balance from recent messages.
     * Returns the parsed balance as Double, or null if none found.
     */
    suspend fun getLatestMpesaBalance(): Double? {
        // Try an exact phrase first
        val exactPhrase = "New M-PESA balance"
        val msg = appDatabase.messageDao().findLatestMessageLike(exactPhrase)
        val candidate = msg ?: appDatabase.messageDao().findLatestMessageLike("Ksh")
        if (candidate == null) return null

        // Parse Ksh amounts from the message text
        val text = candidate.message
        if (text == null) return null

        // Regex to extract amounts like Ksh1,234.56 or Ksh 1,234.56
        val regex = Regex("Ksh\\s*([0-9,]+(?:\\.[0-9]+)?)", RegexOption.IGNORE_CASE)
        val matches = regex.findAll(text).toList()
        if (matches.isEmpty()) return null

        // If message contains the exact phrase, prefer a match that occurs after it
        val idx = text.indexOf(exactPhrase, ignoreCase = true)
        val match = if (idx >= 0) {
            matches.firstOrNull { it.range.first >= idx } ?: matches.last()
        } else {
            // Otherwise, take the last matched Ksh amount (likely the balance)
            matches.last()
        }

        val raw = match.groups[1]?.value ?: return null
        val numeric = raw.replace(",", "")
        return try {
            numeric.toDouble()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Delete a conversation and return a backup (conversation + messages) so callers can restore.
     */
    suspend fun deleteConversation(conversationId: Long): ConversationBackup? {
        val conversation = appDatabase.conversationDao().getConversationOnce(conversationId) ?: return null
        val messages = appDatabase.messageDao().getMessagesForConversationOnce(conversationId)

        // Delete conversation (cascade will remove messages)
        appDatabase.conversationDao().deleteById(conversationId)

        return ConversationBackup(conversation, messages)
    }

    /**
     * Checks if a valid MPESA message exists for the given code and amount.
     */
    suspend fun checkTransaction(code: String, amount: String): Boolean {
        // 1. Search for code string
        val message = appDatabase.messageDao().findLatestMessageLike(code) ?: return false
        
        // 2. Optional: Verify amount if found
        val content = message.message ?: return false
        
        val amountStr = amount.toString()
        val hasAmount = content.contains(amountStr) || content.contains("Ksh$amountStr") || content.contains("Ksh $amountStr")
        
        return hasAmount
    }

    /**
     * Restore a previously backed-up conversation and its messages. Returns new conversation id.
     */
    suspend fun restoreConversation(backup: ConversationBackup): Long {
        // Insert conversation (will get a new id)
        val newConversation = Conversation(
            topic = backup.conversation.topic,
            lastMessage = backup.conversation.lastMessage,
            lastMessageTimestamp = backup.conversation.lastMessageTimestamp
        )
        val newConversationId = appDatabase.conversationDao().insert(newConversation)

        // Re-insert messages (preserve timestamps and isFromUser flag)
        for (m in backup.messages) {
            val inserted = Message(
                conversationId = newConversationId,
                timestamp = m.timestamp,
                message = m.message,
                isFromUser = m.isFromUser
            )
            appDatabase.messageDao().insert(inserted)
        }

        // Ensure conversation metadata is up-to-date
        val latest = appDatabase.messageDao().getLatestMessageForConversation(newConversationId)
        if (latest != null) {
            val conv = appDatabase.conversationDao().getConversationOnce(newConversationId)
            if (conv != null) {
                appDatabase.conversationDao().update(
                    conv.copy(
                        lastMessage = latest.message,
                        lastMessageTimestamp = latest.timestamp
                    )
                )
            }
        }

        return newConversationId
    }

    companion object {
        @Volatile
        private var INSTANCE: Repository? = null

        /**
         * Obtain a singleton Repository backed by the application's Room database.
         */
        fun getInstance(context: Context): Repository {
            return INSTANCE ?: synchronized(this) {
                val database = AppDatabase.getDatabase(context.applicationContext)
                val instance = Repository(database)
                INSTANCE = instance
                instance
            }
        }
    }
}
