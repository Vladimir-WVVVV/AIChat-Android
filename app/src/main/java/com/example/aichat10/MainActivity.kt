package com.example.aichat10

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import com.example.aichat10.ui.theme.AIChat10Theme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AIChat10Theme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val owner = LocalViewModelStoreOwner.current!!
                    val authViewModel = remember(owner) { ViewModelProvider(owner).get(com.example.aichat10.viewmodel.AuthViewModel::class.java) }
                    val loggedInState = authViewModel.loggedIn.collectAsState()
                    val loggedIn = loggedInState.value
                    if (loggedIn) {
                        val chatVm = remember(owner) { ViewModelProvider(owner).get(com.example.aichat10.viewmodel.ChatViewModel::class.java) }
                        com.example.aichat10.ui.components.ChatScreen(viewModel = chatVm)
                    } else {
                        com.example.aichat10.ui.components.LoginScreen(viewModel = authViewModel, onLoggedIn = {})
                    }
                }
            }
        }
    }
}
