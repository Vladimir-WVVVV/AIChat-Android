package com.example.aichat_10.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.aichat_10.data.Message
import com.example.aichat_10.service.ChatRuleEngine
 
import com.example.aichat_10.repository.LocalChatRepository
import okhttp3.Call
import okhttp3.Callback
 
import okhttp3.OkHttpClient
import okhttp3.Request
 
import okhttp3.Response
 
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.URLEncoder

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    private val client = OkHttpClient()
    private val repository = LocalChatRepository(getApplication())
    private val serverBase = "http://127.0.0.1:8080"
    private val conversationId = 1L
    
    init {
        viewModelScope.launch {
            val loaded = runCatching { repository.loadAll() }.getOrElse { emptyList() }
            if (loaded.isNotEmpty()) {
                _messages.value = loaded
            } else {
                val welcomeMessage = Message(
                    content = ChatRuleEngine.getWelcomeMessage(),
                    isUser = false
                )
                _messages.value = listOf(welcomeMessage)
                repository.insert(welcomeMessage)
            }
        }
    }
    
    fun sendMessage(content: String) {
        if (content.isBlank() || _isLoading.value) return
        
        // 添加用户消息
        val userMessage = Message(
            content = content,
            isUser = true
        )
        _messages.value = _messages.value + userMessage
        viewModelScope.launch { repository.insert(userMessage) }
        
        _isLoading.value = true

        val url = "$serverBase/stream/$conversationId?prompt=" +
                URLEncoder.encode(content, "UTF-8")
        val request = Request.Builder()
            .url(url)
            .addHeader("Accept", "text/event-stream")
            .build()

        val aiPlaceholder = Message(content = "", isUser = false)
        _messages.value = _messages.value + aiPlaceholder
        viewModelScope.launch { repository.insert(aiPlaceholder) }

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: java.io.IOException) {
                viewModelScope.launch {
                    val fallback = Message(
                        content = ChatRuleEngine.generateResponse(content),
                        isUser = false
                    )
                    val current = _messages.value.toMutableList()
                    current[current.lastIndex] = fallback
                    _messages.value = current
                    repository.updateContent(fallback.id, fallback.content)
                    _isLoading.value = false
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    onFailure(call, java.io.IOException("HTTP ${'$'}{response.code}"))
                    return
                }
                response.body?.let { body ->
                    val source = body.source()
                    while (!source.exhausted()) {
                        val raw = source.readUtf8Line() ?: break
                        val line = raw.trim()
                        if (line.isEmpty()) continue
                        val payload = if (line.startsWith("data:")) line.removePrefix("data:").trim() else line
                        if (payload == "[DONE]") break
                        val piece = payload
                        if (piece.isNotEmpty()) {
                            viewModelScope.launch {
                                val list = _messages.value.toMutableList()
                                val last = list.last()
                                val updated = last.copy(content = last.content + piece)
                                list[list.lastIndex] = updated
                                _messages.value = list
                                repository.updateContent(updated.id, updated.content)
                            }
                        }
                    }
                }
                viewModelScope.launch { _isLoading.value = false }
            }
        })
    }
    
    fun clearMessages() {
        _messages.value = emptyList()
        viewModelScope.launch { repository.clear() }
        val welcomeMessage = Message(
            content = ChatRuleEngine.getWelcomeMessage(),
            isUser = false
        )
        _messages.value = listOf(welcomeMessage)
        viewModelScope.launch { repository.insert(welcomeMessage) }
    }
}
