package com.example.aichat10.data

data class Message(
    val id: String = System.currentTimeMillis().toString(),
    val content: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val conversationId: Long = System.currentTimeMillis(),
    val imageUri: String? = null
)
