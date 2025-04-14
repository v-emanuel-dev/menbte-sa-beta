package com.example.mentesa

import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateInterpolator
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
import androidx.core.animation.doOnEnd
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mentesa.ui.theme.MenteSaTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalAnimationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()

        splashScreen.setOnExitAnimationListener { splashScreenViewProvider ->
            val splashScreenView = splashScreenViewProvider.view
            val fadeOut = ObjectAnimator.ofFloat(splashScreenView, View.ALPHA, 1f, 0f)
            fadeOut.interpolator = AccelerateInterpolator()
            fadeOut.duration = 500L

            fadeOut.doOnEnd {
                splashScreenViewProvider.remove()
            }

            fadeOut.start()
        }

        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            MenteSaTheme {
                val authViewModel: AuthViewModel = viewModel()
                val chatViewModel: ChatViewModel = viewModel()
                val currentUser by authViewModel.currentUser.collectAsState()
                val isLoggedIn = currentUser != null

                var showAuthScreen by remember { mutableStateOf(!isLoggedIn) }

                val snackbarHostState = remember { SnackbarHostState() }
                val coroutineScope = rememberCoroutineScope()

                Scaffold(
                    snackbarHost = {
                        SnackbarHost(hostState = snackbarHostState)
                    }
                ) { paddingValues ->
                    Box(modifier = Modifier.padding(paddingValues)) {
                        AnimatedContent(
                            targetState = showAuthScreen,
                            transitionSpec = {
                                if (targetState) {
                                    slideInHorizontally(
                                        initialOffsetX = { it },
                                        animationSpec = tween(durationMillis = 300)
                                    ) + fadeIn(animationSpec = tween(durationMillis = 300)) with
                                            slideOutHorizontally(
                                                targetOffsetX = { -it },
                                                animationSpec = tween(durationMillis = 300)
                                            ) + fadeOut(animationSpec = tween(durationMillis = 300))
                                } else {
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