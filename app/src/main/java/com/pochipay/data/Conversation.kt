package com.pochipay.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversations")
data class Conversation(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val topic: String,
    val lastMessage: String?,
    val lastMessageTimestamp: Long
)
