package com.pochipay.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Insert
    suspend fun insert(message: Message): Long

    // Return messages oldest-first so the UI can display the conversation in chronological order
    // and allow scrolling to the bottom to show the latest message.
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    fun getMessagesForConversation(conversationId: Long): Flow<List<Message>>

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM messages WHERE id = :id LIMIT 1")
    suspend fun getMessageById(id: Long): Message?

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestMessageForConversation(conversationId: Long): Message?

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    suspend fun getMessagesForConversationOnce(conversationId: Long): List<Message>

    // Find the latest message containing the exact MPESA balance phrase
    @Query("SELECT * FROM messages WHERE message LIKE '%' || :phrase || '%' ORDER BY timestamp DESC LIMIT 1")
    suspend fun findLatestMessageLike(phrase: String): Message?
}
