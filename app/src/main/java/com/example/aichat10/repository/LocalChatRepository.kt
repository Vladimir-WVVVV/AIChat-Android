package com.example.aichat10.repository

import android.content.Context
import com.example.aichat10.data.Message
import com.example.aichat10.data.local.AppDatabase
import com.example.aichat10.data.local.MessageEntity

class LocalChatRepository(context: Context) {
    private val dao = AppDatabase.getInstance(context).messageDao()

    suspend fun loadByConversation(conversationId: Long): List<Message> =
        dao.getByConversation(conversationId).map { it.toMessage() }

    suspend fun insert(message: Message) {
        dao.insert(message.toEntity())
    }

    suspend fun updateContent(id: String, content: String) {
        dao.updateContent(id, content)
    }

    suspend fun clearConversation(conversationId: Long) {
        dao.clearConversation(conversationId)
    }

    suspend fun getConversationSummaries(): List<ConversationSummary> =
        dao.getConversationSummaries()

    private fun MessageEntity.toMessage() = Message(
        id = id,
        content = content,
        isUser = isUser,
        timestamp = timestamp,
        conversationId = conversationId,
        imageUri = imageUri
    )

    private fun Message.toEntity() = MessageEntity(
        id = id,
        content = content,
        isUser = isUser,
        timestamp = timestamp,
        conversationId = conversationId,
        imageUri = imageUri
    )
}

data class ConversationSummary(
    val conversationId: Long,
    val latest: Long
)
