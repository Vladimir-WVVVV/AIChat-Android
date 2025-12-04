package com.example.aichat10.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.aichat10.data.Message
import com.example.aichat10.service.ChatRuleEngine
 
import com.example.aichat10.repository.LocalChatRepository
import okhttp3.Call
import okhttp3.Callback
 
import okhttp3.OkHttpClient
import okhttp3.Request
 
import okhttp3.Response
import com.example.aichat10.BuildConfig
import com.example.aichat10.repository.ConversationSummary
 
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.URLEncoder
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    private val client = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val req = chain.request()
            val path = req.url.encodedPath
            if (path.startsWith("/auth")) return@addInterceptor chain.proceed(req)
            val token = getApplication<Application>().getSharedPreferences("auth", android.content.Context.MODE_PRIVATE).getString("token", null)
            val newReq = if (!token.isNullOrBlank()) req.newBuilder().addHeader("Authorization", "Bearer $token").build() else req
            chain.proceed(newReq)
        }
        .build()
    private val repository = LocalChatRepository(getApplication())
    private val serverBase = getApplication<Application>().getSharedPreferences("auth", android.content.Context.MODE_PRIVATE).getString("server_base", BuildConfig.SERVER_BASE) ?: BuildConfig.SERVER_BASE
    private var currentTextCall: Call? = null
    private var currentImageCall: Call? = null
    private var activeStreams = 0
    private var conversationId = System.currentTimeMillis()
    private val _model = MutableStateFlow("doubao")
    val model: StateFlow<String> = _model.asStateFlow()
    private val _conversations = MutableStateFlow<List<ConversationSummary>>(emptyList())
    val conversations: StateFlow<List<ConversationSummary>> = _conversations.asStateFlow()
    private val _models = MutableStateFlow<List<String>>(emptyList())
    val models: StateFlow<List<String>> = _models.asStateFlow()
    
    init {
        viewModelScope.launch {
            val loaded = runCatching { repository.loadByConversation(conversationId) }.getOrElse { emptyList() }
            if (loaded.isNotEmpty()) {
                _messages.value = loaded
            } else {
                val welcomeMessage = Message(
                    content = ChatRuleEngine.getWelcomeMessage(),
                    isUser = false,
                    conversationId = conversationId
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
            isUser = true,
            conversationId = conversationId
        )
        _messages.value = _messages.value + userMessage
        viewModelScope.launch { repository.insert(userMessage) }
        
        _isLoading.value = true

        val url = "$serverBase/stream/$conversationId?prompt=" +
                URLEncoder.encode(content, "UTF-8") + "&model=" + _model.value
        val token = getApplication<Application>().getSharedPreferences("auth", android.content.Context.MODE_PRIVATE).getString("token", null)
        val builder = Request.Builder().url(url).addHeader("Accept", "text/event-stream")
        if (!token.isNullOrBlank()) builder.addHeader("Authorization", "Bearer $token")
        val request = builder.build()

        val aiPlaceholder = Message(content = "", isUser = false, conversationId = conversationId)
        _messages.value = _messages.value + aiPlaceholder
        viewModelScope.launch { repository.insert(aiPlaceholder) }

        if (activeStreams >= 2 && currentTextCall != null) { currentTextCall?.cancel(); activeStreams = activeStreams - 1 }
        currentTextCall?.cancel()
        currentTextCall = client.newCall(request)
        currentTextCall!!.enqueue(object : Callback {
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
                    if (response.code == 401) {
                        viewModelScope.launch {
                            val current = _messages.value.toMutableList()
                            val last = current.last().copy(content = "认证失败，请重新登录")
                            current[current.lastIndex] = last
                            _messages.value = current
                            repository.updateContent(last.id, last.content)
                            _isLoading.value = false
                        }
                    } else if (response.code == 429) {
                        viewModelScope.launch {
                            val current = _messages.value.toMutableList()
                            val last = current.last().copy(content = "并发过多，请稍后")
                            current[current.lastIndex] = last
                            _messages.value = current
                            repository.updateContent(last.id, last.content)
                            _isLoading.value = false
                        }
                    } else {
                        onFailure(call, java.io.IOException("HTTP ${'$'}{response.code}"))
                    }
                    return
                }
                response.body?.let { body ->
                    val source = body.source()
                    activeStreams = activeStreams + 1
                    while (!source.exhausted()) {
                        val raw = source.readUtf8Line() ?: break
                        val line = raw.trim()
                        if (line.isEmpty()) continue
                        val payload = if (line.startsWith("data:")) line.removePrefix("data:").trim() else line
                        if (payload == "[DONE]") break
                        if (payload == "forbidden") {
                            viewModelScope.launch {
                                val current = _messages.value.toMutableList()
                                val last = current.last().copy(content = "无权限访问该会话")
                                current[current.lastIndex] = last
                                _messages.value = current
                                repository.updateContent(last.id, last.content)
                                _isLoading.value = false
                            }
                            break
                        }
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
                viewModelScope.launch { _isLoading.value = false; activeStreams = maxOf(0, activeStreams - 1) }
            }
        })
    }
    
    fun clearMessages() {
        currentTextCall?.cancel()
        conversationId = System.currentTimeMillis()
        _messages.value = emptyList()
        viewModelScope.launch { repository.clearConversation(conversationId) }
        val welcomeMessage = Message(
            content = ChatRuleEngine.getWelcomeMessage(),
            isUser = false,
            conversationId = conversationId
        )
        _messages.value = listOf(welcomeMessage)
        viewModelScope.launch { repository.insert(welcomeMessage) }
    }

    fun setModel(value: String) {
        _model.value = value
    }

    fun openConversation(id: Long) {
        conversationId = id
        val token = getApplication<Application>().getSharedPreferences("auth", android.content.Context.MODE_PRIVATE).getString("token", null)
        viewModelScope.launch {
            msgPage = 0
            msgHasMore = true
            val remote = try {
                val req = Request.Builder().url("$serverBase/messages/$conversationId?page=${msgPage}&size=100").apply {
                    addHeader("Accept", "application/json")
                    if (!token.isNullOrBlank()) addHeader("Authorization", "Bearer $token")
                }.build()
                client.newCall(req).execute().body?.string()
            } catch (_: Exception) { null }
            val loaded = if (!remote.isNullOrBlank()) parseMessages(remote) else runCatching { repository.loadByConversation(conversationId) }.getOrElse { emptyList() }
            _messages.value = loaded
            msgHasMore = loaded.isNotEmpty()
        }
    }

    fun loadMoreMessages() {
        if (!msgHasMore) return
        viewModelScope.launch {
            val token = getApplication<Application>().getSharedPreferences("auth", android.content.Context.MODE_PRIVATE).getString("token", null)
            val next = msgPage + 1
            val remote = try {
                val req = Request.Builder().url("$serverBase/messages/$conversationId?page=${next}&size=100").apply {
                    addHeader("Accept", "application/json")
                    if (!token.isNullOrBlank()) addHeader("Authorization", "Bearer $token")
                }.build()
                client.newCall(req).execute().body?.string()
            } catch (_: Exception) { null }
            val more = if (!remote.isNullOrBlank()) parseMessages(remote) else emptyList()
            if (more.isNotEmpty()) {
                _messages.value = more + _messages.value
                msgPage = next
            } else {
                msgHasMore = false
            }
        }
    }

    fun pickImage(uri: String) {
        val imgMessage = Message(content = "[图片]", isUser = true, conversationId = conversationId, imageUri = uri)
        _messages.value = _messages.value + imgMessage
        viewModelScope.launch { repository.insert(imgMessage) }
        val aiPlaceholder = Message(content = "", isUser = false, conversationId = conversationId)
        _messages.value = _messages.value + aiPlaceholder
        viewModelScope.launch { repository.insert(aiPlaceholder) }
        val token = getApplication<Application>().getSharedPreferences("auth", android.content.Context.MODE_PRIVATE).getString("token", null)
        val resolver = getApplication<Application>().contentResolver
        val u = android.net.Uri.parse(uri)
        val name = "image.jpg"
        val mime = resolver.getType(u) ?: "application/octet-stream"
        val allowed = listOf("image/jpeg", "image/png", "image/webp")
        val size = resolver.query(u, arrayOf(android.provider.OpenableColumns.SIZE), null, null, null)?.use { c -> if (c.moveToFirst()) c.getLong(0) else 0L } ?: 0L
        if (!allowed.contains(mime) || size <= 0L || size > 10L * 1024L * 1024L) {
            viewModelScope.launch {
                val current = _messages.value.toMutableList()
                val last = current.last().copy(content = "图片不符合要求（类型或大小）")
                current[current.lastIndex] = last
                _messages.value = current
                repository.updateContent(last.id, last.content)
            }
            return
        }
        val bytes = resolver.openInputStream(u)?.use { it.readBytes() } ?: run {
            viewModelScope.launch {
                val current = _messages.value.toMutableList()
                val last = current.last().copy(content = "无法读取图片")
                current[current.lastIndex] = last
                _messages.value = current
                repository.updateContent(last.id, last.content)
            }
            return
        }
        val reqBody = okhttp3.MultipartBody.Builder().setType(okhttp3.MultipartBody.FORM)
            .addFormDataPart("prompt", "")
            .addFormDataPart("model", _model.value)
            .addFormDataPart("image", name, bytes.toRequestBody("image/*".toMediaType()))
            .build()
        val url = "$serverBase/multimodal/$conversationId"
        val builder = Request.Builder().url(url).post(reqBody).addHeader("Accept", "text/event-stream")
        if (!token.isNullOrBlank()) builder.addHeader("Authorization", "Bearer $token")
        val request = builder.build()
        if (activeStreams >= 2 && currentImageCall != null) { currentImageCall?.cancel(); activeStreams = activeStreams - 1 }
        currentImageCall = client.newCall(request)
        currentImageCall!!.enqueue(object : Callback {
            override fun onFailure(call: Call, e: java.io.IOException) {
                viewModelScope.launch {
                    val current = _messages.value.toMutableList()
                    val last = current.last().copy(content = "图片发送失败")
                    current[current.lastIndex] = last
                    _messages.value = current
                    repository.updateContent(last.id, last.content)
                }
            }
            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    if (response.code == 429) {
                        viewModelScope.launch {
                            val current = _messages.value.toMutableList()
                            val last = current.last().copy(content = "并发过多，请稍后")
                            current[current.lastIndex] = last
                            _messages.value = current
                            repository.updateContent(last.id, last.content)
                        }
                    }
                    return
                }
                response.body?.let { body ->
                    val source = body.source()
                    activeStreams = activeStreams + 1
                    while (!source.exhausted()) {
                        val raw = source.readUtf8Line() ?: break
                        val line = raw.trim()
                        if (line.isEmpty()) continue
                        val payload = if (line.startsWith("data:")) line.removePrefix("data:").trim() else line
                        if (payload == "[DONE]") break
                        if (payload == "forbidden") {
                            viewModelScope.launch {
                                val current = _messages.value.toMutableList()
                                val last = current.last().copy(content = "无权限访问该会话")
                                current[current.lastIndex] = last
                                _messages.value = current
                                repository.updateContent(last.id, last.content)
                            }
                            break
                        }
                        if (payload.isNotEmpty()) {
                            viewModelScope.launch {
                                val list = _messages.value.toMutableList()
                                val last = list.last()
                                val updated = last.copy(content = last.content + payload)
                                list[list.lastIndex] = updated
                                _messages.value = list
                                repository.updateContent(updated.id, updated.content)
                            }
                        }
                    }
                }
            }
        })
    }

    private var convPage = 0
    private var convHasMore = true
    private var msgPage = 0
    private var msgHasMore = true

    fun refreshConversations() {
        val token = getApplication<Application>().getSharedPreferences("auth", android.content.Context.MODE_PRIVATE).getString("token", null)
        viewModelScope.launch {
            convPage = 0
            convHasMore = true
            val remote = try {
                val req = Request.Builder().url("$serverBase/conversations?page=${convPage}&size=50").apply {
                    addHeader("Accept", "application/json")
                    if (!token.isNullOrBlank()) addHeader("Authorization", "Bearer $token")
                }.build()
                client.newCall(req).execute().body?.string()
            } catch (_: Exception) { null }
            val list = if (!remote.isNullOrBlank()) parseSummaries(remote) else runCatching { repository.getConversationSummaries() }.getOrElse { emptyList<ConversationSummary>() }
            _conversations.value = list
            convHasMore = list.isNotEmpty()
        }
    }

    fun loadMoreConversations() {
        if (!convHasMore) return
        viewModelScope.launch {
            val token = getApplication<Application>().getSharedPreferences("auth", android.content.Context.MODE_PRIVATE).getString("token", null)
            val next = convPage + 1
            val remote = try {
                val req = Request.Builder().url("$serverBase/conversations?page=${next}&size=50").apply {
                    addHeader("Accept", "application/json")
                    if (!token.isNullOrBlank()) addHeader("Authorization", "Bearer $token")
                }.build()
                client.newCall(req).execute().body?.string()
            } catch (_: Exception) { null }
            val list = if (!remote.isNullOrBlank()) parseSummaries(remote) else emptyList()
            if (list.isNotEmpty()) {
                _conversations.value = _conversations.value + list
                convPage = next
            } else {
                convHasMore = false
            }
        }
    }

    fun fetchModels() {
        val token = getApplication<Application>().getSharedPreferences("auth", android.content.Context.MODE_PRIVATE).getString("token", null)
        viewModelScope.launch {
            val resp = try {
                val req = Request.Builder().url("$serverBase/models").apply {
                    addHeader("Accept", "application/json")
                    if (!token.isNullOrBlank()) addHeader("Authorization", "Bearer $token")
                }.build()
                client.newCall(req).execute().body?.string()
            } catch (_: Exception) { null }
            val parsed = if (!resp.isNullOrBlank()) parseModels(resp) else emptyList()
            _models.value = if (parsed.isNotEmpty()) parsed else listOf("doubao", "deepseek", "kimi")
        }
    }

    private fun parseModels(json: String): List<String> {
        val names = Regex("\"name\":\"(.*?)\"").findAll(json).map { it.groupValues[1] }.toList()
        return names
    }

    private fun parseMessages(json: String): List<Message> {
        val regex = Regex("\\{\\\"id\\\":\\\"(.*?)\\\",\\\"content\\\":\\\"(.*?)\\\",\\\"isUser\\\":(true|false),\\\"timestamp\\\":(\\d+),\\\"conversationId\\\":(\\d+)")
        return regex.findAll(json).map {
            val g = it.groupValues
            Message(id = g[1], content = g[2], isUser = g[3] == "true", timestamp = g[4].toLong(), conversationId = g[5].toLong())
        }.toList()
    }

    private fun parseSummaries(json: String): List<ConversationSummary> {
        val regex = Regex("\\{\\\"conversationId\\\":(\\d+),\\\"latest\\\":(\\d+)")
        return regex.findAll(json).map { val g = it.groupValues; ConversationSummary(g[1].toLong(), g[2].toLong()) }.toList()
    }
}
