package com.example.aichat10.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Request
import com.example.aichat10.BuildConfig
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

class AuthViewModel(application: Application) : AndroidViewModel(application) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
    private val prefs = application.getSharedPreferences("auth", Context.MODE_PRIVATE)
    private val serverBase = BuildConfig.SERVER_BASE

    private val _username = MutableStateFlow("")
    val username: StateFlow<String> = _username.asStateFlow()
    private val _password = MutableStateFlow("")
    val password: StateFlow<String> = _password.asStateFlow()
    private val _confirm = MutableStateFlow("")
    val confirm: StateFlow<String> = _confirm.asStateFlow()
    private val _registerMode = MutableStateFlow(false)
    val registerMode: StateFlow<Boolean> = _registerMode.asStateFlow()
    private val _status = MutableStateFlow("")
    val status: StateFlow<String> = _status.asStateFlow()
    private val _loggedIn = MutableStateFlow(run {
        val t = prefs.getString("token", null)
        val exp = prefs.getLong("token_expires_at", 0L)
        val now = System.currentTimeMillis()
        t != null && (exp == 0L || exp > now)
    })
    val loggedIn: StateFlow<Boolean> = _loggedIn.asStateFlow()
    private val _devCode = MutableStateFlow("")
    val devCode: StateFlow<String> = _devCode.asStateFlow()
    private val _cooldown = MutableStateFlow(0)
    val cooldown: StateFlow<Int> = _cooldown.asStateFlow()

    private fun currentBase(): String = prefs.getString("server_base", serverBase) ?: serverBase
    private fun endpointCandidates(): List<String> {
        val b = currentBase()
        val list = mutableListOf(b)
        if (!list.contains(serverBase)) list.add(serverBase)
        if (b.contains("127.0.0.1") || serverBase.contains("127.0.0.1")) {
            list.add("http://10.0.2.2:8080")
        }
        return list.distinct()
    }
    private fun postJson(path: String, json: String): Triple<Boolean, String, String> {
        val body = json.toRequestBody("application/json".toMediaType())
        var last = ""
        var used = ""
        for (base in endpointCandidates()) {
            val req = Request.Builder().url("${base}${path}").post(body).build()
            try {
                client.newCall(req).execute().use { r ->
                    val txt = r.body?.string() ?: ""
                    if (r.isSuccessful) return Triple(true, txt, base)
                    last = txt
                    used = base
                }
            } catch (e: Exception) { last = e.message ?: "连接失败" }
        }
        return Triple(false, last, used)
    }

    fun setUsername(u: String) { _username.value = u }
    fun setPassword(p: String) { _password.value = p }
    fun setConfirm(c: String) { _confirm.value = c }
    fun toggleRegister() { _registerMode.value = !_registerMode.value }

    fun login(onSuccess: () -> Unit) {
        val u = _username.value.trim()
        val p = _password.value
        val localErr = validateInputs(u, p, null, false)
        if (localErr != null) { _status.value = localErr; return }
        viewModelScope.launch {
            val payload = "{\"username\":\"$u\",\"password\":\"$p\"}"
            val (ok, txt, base) = postJson("/auth/login", payload)
            if (ok) {
                val token = Regex("\"token\":\"(.*?)\"").find(txt)?.groupValues?.get(1)
                val ttlSec = Regex("\"ttlSec\":(\\d+)").find(txt)?.groupValues?.get(1)?.toLongOrNull()
                val expiresAt = Regex("\"expiresAt\":(\\d+)").find(txt)?.groupValues?.get(1)?.toLongOrNull()
                if (!token.isNullOrBlank()) {
                    saveSession(token, ttlSec, expiresAt)
                    _loggedIn.value = true
                    onSuccess()
                } else {
                    _status.value = "登录失败"
                }
            } else {
                val msg = Regex("\"message\":\"(.*?)\"").find(txt)?.groupValues?.get(1) ?: "登录失败"
                _status.value = if (base.isNotEmpty()) "地址 ${base} 失败：${msg}" else msg
            }
        }
    }

    fun register(onSuccess: () -> Unit) {
        val u = _username.value.trim()
        val p = _password.value
        val c = _confirm.value
        val localErr = validateInputs(u, p, c, true)
        if (localErr != null) { _status.value = localErr; return }
        viewModelScope.launch {
            val payload = "{\"username\":\"$u\",\"password\":\"$p\"}"
            val (ok, txt, base) = postJson("/auth/register", payload)
            if (ok) {
                val token = Regex("\"token\":\"(.*?)\"").find(txt)?.groupValues?.get(1)
                val ttlSec = Regex("\"ttlSec\":(\\d+)").find(txt)?.groupValues?.get(1)?.toLongOrNull()
                val expiresAt = Regex("\"expiresAt\":(\\d+)").find(txt)?.groupValues?.get(1)?.toLongOrNull()
                if (!token.isNullOrBlank()) {
                    saveSession(token, ttlSec, expiresAt)
                    _loggedIn.value = true
                    onSuccess()
                } else {
                    _status.value = "注册失败"
                }
            } else {
                val msg = Regex("\"message\":\"(.*?)\"").find(txt)?.groupValues?.get(1) ?: "注册失败"
                _status.value = if (base.isNotEmpty()) "地址 ${base} 失败：${msg}" else msg
            }
        }
    }

    fun testConnection() {
        viewModelScope.launch {
            val candidates = endpointCandidates()
            var lastErr = ""
            for (b in candidates) {
                // 1) /health
                val health = Request.Builder().url("${b}/health").get().build()
                try {
                    client.newCall(health).execute().use { r ->
                        if (r.isSuccessful) { _status.value = "连接正常：${b}"; return@launch } else { lastErr = "${r.code}" }
                    }
                } catch (e: Exception) { lastErr = e.message ?: "异常" }
                // 2) /actuator/health
                val act = Request.Builder().url("${b}/actuator/health").get().build()
                try {
                    client.newCall(act).execute().use { r ->
                        if (r.isSuccessful) { _status.value = "连接正常：${b}"; return@launch } else { lastErr = "${r.code}" }
                    }
                } catch (e: Exception) { lastErr = e.message ?: "异常" }
                // 3) 尝试登录接口，任何有效响应都视为可连
                val dummy = "{\"username\":\"__ping__\",\"password\":\"__ping__\"}".toRequestBody("application/json".toMediaType())
                val loginReq = Request.Builder().url("${b}/auth/login").post(dummy).build()
                try {
                    client.newCall(loginReq).execute().use { r ->
                        if (r.code in 200..499) { _status.value = "连接正常：${b}"; return@launch } else { lastErr = "${r.code}" }
                    }
                } catch (e: Exception) { lastErr = e.message ?: "异常" }
            }
            _status.value = if (lastErr.isNotEmpty()) "连接失败：${lastErr}，请检查端口与地址" else "连接失败：请检查端口与地址"
        }
    }

    fun logout() {
        prefs.edit().clear().apply()
        _loggedIn.value = false
        _devCode.value = ""
        _cooldown.value = 0
    }

    private fun startCooldown() {
        if (_cooldown.value > 0) return
        viewModelScope.launch {
            _cooldown.value = 60
            while (_cooldown.value > 0) {
                delay(1000)
                _cooldown.value = _cooldown.value - 1
            }
        }
    }

    fun setServerBase(base: String) {
        var s = base.trim().replace(" ", "")
        if (s.startsWith("///")) s = s.removePrefix("///")
        if (s.startsWith("//")) s = s.removePrefix("//")
        val m1 = Regex("(?i)^(?:https?://)?([a-z0-9.-]+):(\\d+)$").find(s)
        val m2 = Regex("([0-9]{1,3}(?:\\.[0-9]{1,3}){3}):(\\d+)").find(s)
        val url = when {
            m1 != null -> "http://${m1.groupValues[1]}:${m1.groupValues[2]}"
            m2 != null -> "http://${m2.groupValues[1]}:${m2.groupValues[2]}"
            s.startsWith("http://") || s.startsWith("https://") -> s
            else -> "http://${s}"
        }
        prefs.edit().putString("server_base", url).apply()
        _status.value = "已保存地址：${url}"
        testConnection()
    }

    fun getServerBase(): String = currentBase()

    private fun saveSession(token: String, ttlSec: Long?, expiresAt: Long?) {
        val now = System.currentTimeMillis()
        val exp = when {
            expiresAt != null && expiresAt > now -> expiresAt
            ttlSec != null && ttlSec > 0 -> now + ttlSec * 1000
            else -> now + 24L * 60L * 60L * 1000L
        }
        prefs.edit().putString("token", token).putLong("token_expires_at", exp).apply()
    }

    private fun validateInputs(u: String, p: String, c: String?, isRegister: Boolean): String? {
        val nameOk = u.isNotEmpty() && u.matches(Regex("^[A-Za-z0-9_.-]{3,32}$"))
        if (!nameOk) return "账号格式不正确（3-32位字母数字或_.-）"
        if (p.length < 8) return "密码至少8位"
        if (isRegister && c != null && p != c) return "两次输入的密码不一致"
        return null
    }
}
