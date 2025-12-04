package com.example.aichat10.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.graphics.BitmapFactory
import androidx.compose.runtime.LaunchedEffect
import com.example.aichat10.data.Message
import com.example.aichat10.viewmodel.ChatViewModel
import kotlinx.coroutines.launch
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.MoreVert

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(viewModel: ChatViewModel) {
    val messages by viewModel.messages.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val model by viewModel.model.collectAsState()
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    var showModelMenu by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            viewModel.pickImage(uri.toString())
        }
    }
    
    // 当有新消息时自动滚动到底部
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            coroutineScope.launch {
                listState.animateScrollToItem(messages.size - 1)
            }
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 顶部标题栏
    TopAppBar(
            title = { 
                Text(
                    text = "AI对话助手",
                    fontWeight = FontWeight.Bold
                ) 
            },
            actions = {
                TextButton(onClick = { showHistory = true }) { Text("历史") }
                Box {
                    IconButton(onClick = { showModelMenu = true }) { Icon(Icons.Default.MoreVert, null) }
                    DropdownMenu(expanded = showModelMenu, onDismissRequest = { showModelMenu = false }) {
                        LaunchedEffect(Unit) { viewModel.fetchModels() }
                        val models by viewModel.models.collectAsState()
                        val list = if (models.isNotEmpty()) models else listOf("doubao", "deepseek", "kimi")
                        list.forEach { m ->
                            DropdownMenuItem(text = { Text(m) }, onClick = {
                                viewModel.setModel(m)
                                showModelMenu = false
                            })
                        }
                    }
                }
                TextButton(onClick = { viewModel.clearMessages() }) { Text("新建聊天") }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        )
        if (showHistory) {
            AlertDialog(
                onDismissRequest = { showHistory = false },
                confirmButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { viewModel.loadMoreConversations() }) { Text("加载更多") }
                        TextButton(onClick = { showHistory = false }) { Text("关闭") }
                    }
                },
                title = { Text("历史会话") },
                text = {
                    LaunchedEffect(Unit) { viewModel.refreshConversations() }
                    val conversations by viewModel.conversations.collectAsState()
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        conversations.forEach { item ->
                            TextButton(onClick = {
                                viewModel.openConversation(item.conversationId)
                                showHistory = false
                            }) { Text("会话 " + item.conversationId) }
                        }
                        if (conversations.isEmpty()) { Text("无历史记录") }
                    }
                }
            )
        }
        
        // 消息列表
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    TextButton(onClick = { viewModel.loadMoreMessages() }) { Text("加载更多消息") }
                }
            }
            items(messages) { message ->
                MessageBubble(message = message)
            }
            
            // 加载指示器
            if (isLoading) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        TypingIndicator()
                    }
                }
            }
        }
        
        // 输入框
    InputField(
        text = inputText,
        onTextChange = { inputText = it },
        onSend = {
            if (inputText.isNotBlank()) {
                viewModel.sendMessage(inputText)
                inputText = ""
            }
        },
        enabled = !isLoading,
        onPickImage = { imagePicker.launch("image/*") }
    )
    }
}

@Composable
fun MessageBubble(message: Message) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!message.isUser) {
            // AI头像
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "AI",
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }
        
        var showPreview by remember { mutableStateOf(false) }
        val bubbleBg = if (message.imageUri != null) Color.Transparent else if (message.isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(bubbleBg)
                .padding(12.dp)
        ) {
            if (message.imageUri != null) {
                val context = LocalContext.current
                var bmp by remember(message.imageUri) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
                LaunchedEffect(message.imageUri) {
                    runCatching {
                        val u = android.net.Uri.parse(message.imageUri)
                        context.contentResolver.openInputStream(u)?.use { ins ->
                            val opt = BitmapFactory.Options().apply { inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888; inSampleSize = 1 }
                            val b = BitmapFactory.decodeStream(ins, null, opt)
                            bmp = b?.asImageBitmap()
                        }
                    }
                }
                if (bmp != null) {
                    Image(
                        bitmap = bmp!!,
                        contentDescription = null,
                        modifier = Modifier.size(160.dp).clip(RoundedCornerShape(12.dp)).clickable { showPreview = true },
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(modifier = Modifier.size(160.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant))
                }
                if (showPreview) {
                    AlertDialog(
                        onDismissRequest = { showPreview = false },
                        confirmButton = {
                            TextButton(onClick = { showPreview = false }) { Text("关闭") }
                        },
                        text = {
                            if (bmp != null) {
                                Image(
                                    bitmap = bmp!!,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxWidth().height(320.dp).clip(RoundedCornerShape(12.dp)),
                                    contentScale = ContentScale.Fit
                                )
                            }
                        }
                    )
                }
            } else {
                Text(
                    text = message.content,
                    color = if (message.isUser) 
                        MaterialTheme.colorScheme.onPrimary 
                    else 
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 15.sp,
                    lineHeight = 20.sp
                )
            }
        }
        
        if (message.isUser) {
            Spacer(modifier = Modifier.width(8.dp))
            // 用户头像
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.secondary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "我",
                    color = MaterialTheme.colorScheme.onSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun TypingIndicator() {
    Box(
        modifier = Modifier
            .widthIn(max = 80.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(3) { index ->
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                )
            }
        }
    }
}

@Composable
fun InputField(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    enabled: Boolean = true,
    onPickImage: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.weight(1f),
                enabled = enabled,
                placeholder = { Text("请输入中文消息...") },
                shape = RoundedCornerShape(24.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )
            TextButton(onClick = { if (enabled) onPickImage() }) { Text("图片") }
            
            FloatingActionButton(
                onClick = {
                    if (enabled && text.isNotBlank()) {
                        onSend()
                    }
                },
                modifier = Modifier.size(48.dp),
                containerColor = if (enabled && text.isNotBlank()) 
                    MaterialTheme.colorScheme.primary 
                else 
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "发送"
                )
            }
        }
    }
}
