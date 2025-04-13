package com.example.mentesa

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mentesa.ui.theme.MenteSaTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalAnimationApi::class)
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

                // Estado do Snackbar com estilo personalizado
                val snackbarHostState = remember { SnackbarHostState() }
                val coroutineScope = rememberCoroutineScope()

                // Scaffold para poder mostrar Snackbar
                Scaffold(
                    snackbarHost = {
                        SnackbarHost(hostState = snackbarHostState)
                    }
                ) { paddingValues ->
                    Box(modifier = Modifier.padding(paddingValues)) {
                        // Usar @OptIn para marcar o uso de API experimental
                        // Já anotamos a classe inteira no início
                        AnimatedContent(
                            targetState = showAuthScreen,
                            transitionSpec = {
                                if (targetState) {
                                    // Entrando na tela de auth
                                    slideInHorizontally(
                                        initialOffsetX = { it },
                                        animationSpec = tween(durationMillis = 300)
                                    ) + fadeIn(animationSpec = tween(durationMillis = 300)) with
                                            slideOutHorizontally(
                                                targetOffsetX = { -it },
                                                animationSpec = tween(durationMillis = 300)
                                            ) + fadeOut(animationSpec = tween(durationMillis = 300))
                                } else {
                                    // Voltando para o chat
                                    slideInHorizontally(
                                        initialOffsetX = { -it },
                                        animationSpec = tween(durationMillis = 300)
                                    ) + fadeIn(animationSpec = tween(durationMillis = 300)) with
                                            slideOutHorizontally(
                                                targetOffsetX = { it },
                                                animationSpec = tween(durationMillis = 300)
                                            ) + fadeOut(animationSpec = tween(durationMillis = 300))
                                }
                            },
                            label = "screenTransition"
                        ) { isAuthScreen ->
                            if (isAuthScreen) {
                                AuthScreen(
                                    onNavigateToChat = {
                                        chatViewModel.handleLogin()
                                        chatViewModel.startNewConversation()
                                        showAuthScreen = false

                                        // Mostra o Snackbar para mensagem de login bem-sucedido
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar(
                                                message = "Login realizado com sucesso!",
                                                duration = SnackbarDuration.Short
                                            )
                                        }
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
                                        chatViewModel.handleLogout()

                                        // Mostra o Snackbar para a mensagem de logout
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar(
                                                message = "Logout realizado com sucesso!",
                                                duration = SnackbarDuration.Short
                                            )
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}