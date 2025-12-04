package com.example.aichat10.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,
    val content: String,
    val isUser: Boolean,
    val timestamp: Long,
    val conversationId: Long,
    val imageUri: String?
)
