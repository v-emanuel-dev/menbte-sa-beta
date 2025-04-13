package com.example.mentesa

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mentesa.ui.theme.MenteSaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Instala a splash screen e habilita edge-to-edge
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            MenteSaTheme {
                val authViewModel: AuthViewModel = viewModel()
                val chatViewModel: ChatViewModel = viewModel()
                val currentUser by authViewModel.currentUser.collectAsState()
                val isLoggedIn = currentUser != null

                // Define a tela de autenticação se o usuário ainda não estiver logado
                var showAuthScreen by remember { mutableStateOf(!isLoggedIn) }

                if (showAuthScreen) {
                    AuthScreen(
                        onNavigateToChat = {
                            chatViewModel.handleLogin()           // Exibe conversas do usuário logado
                            chatViewModel.startNewConversation()    // Limpa a tela e evita restaurar conversa antiga
                            showAuthScreen = false
                        },
                        onBackToChat = {
                            showAuthScreen = false
                        }
                    )
                } else {
                    ChatScreen(
                        onLogin = {
                            showAuthScreen = true
                        },
                        onLogout = {
                            authViewModel.logout()
                        }
                    )
                }
            }
        }
    }
}
