package com.example.aichat10.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.aichat10.viewmodel.AuthViewModel
import com.example.aichat10.BuildConfig

@Composable
fun LoginScreen(viewModel: AuthViewModel, onLoggedIn: () -> Unit) {
    val username by viewModel.username.collectAsState()
    val password by viewModel.password.collectAsState()
    val confirm by viewModel.confirm.collectAsState()
    val registerMode by viewModel.registerMode.collectAsState()
    val status by viewModel.status.collectAsState()
    var serverBaseText by remember { mutableStateOf(viewModel.getServerBase()) }
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(text = if (registerMode) "账号注册" else "账号登录")
        OutlinedTextField(value = username, onValueChange = { viewModel.setUsername(it) }, modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("账号") })
        OutlinedTextField(value = password, onValueChange = { viewModel.setPassword(it) }, modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("密码") })
        if (registerMode) {
            OutlinedTextField(value = confirm, onValueChange = { viewModel.setConfirm(it) }, modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("确认密码") })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = serverBaseText, onValueChange = { serverBaseText = it }, modifier = Modifier.weight(1f), singleLine = true, placeholder = { Text("后端地址，如 http://127.0.0.1:8080") })
            Button(onClick = { viewModel.setServerBase(serverBaseText) }) { Text("保存地址") }
            TextButton(onClick = { viewModel.testConnection() }) { Text("测试连接") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { if (registerMode) viewModel.register(onLoggedIn) else viewModel.login(onLoggedIn) }, modifier = Modifier.weight(1f)) { Text(if (registerMode) "注册" else "登录") }
            TextButton(onClick = { viewModel.toggleRegister() }) { Text(if (registerMode) "切换到登录" else "切换到注册") }
        }
        if (status.isNotEmpty()) { Text(text = status) }
    }
}
