package com.example.aichat_10.repository

import android.content.Context
import com.example.aichat_10.data.Message
import com.example.aichat_10.data.local.AppDatabase
import com.example.aichat_10.data.local.MessageEntity

class LocalChatRepository(context: Context) {
    private val dao = AppDatabase.getInstance(context).messageDao()

    suspend fun loadAll(): List<Message> =
        dao.getAll().map { it.toMessage() }

    suspend fun insert(message: Message) {
        dao.insert(message.toEntity())
    }

    suspend fun updateContent(id: String, content: String) {
        dao.updateContent(id, content)
    }

    suspend fun clear() {
        dao.clearAll()
    }

    private fun MessageEntity.toMessage() = Message(
        id = id,
        content = content,
        isUser = isUser,
        timestamp = timestamp
    )

    private fun Message.toEntity() = MessageEntity(
        id = id,
        content = content,
        isUser = isUser,
        timestamp = timestamp
    )
}

