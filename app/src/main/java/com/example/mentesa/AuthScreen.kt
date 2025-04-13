// AuthScreen.kt
package com.example.mentesa

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.auth.api.identity.BeginSignInRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import androidx.compose.ui.text.input.VisualTransformation

@Composable
fun AuthScreen(
    onNavigateToChat: () -> Unit,
    onBackToChat: () -> Unit = {},
    authViewModel: AuthViewModel = viewModel()
) {
    val authState by authViewModel.authState.collectAsState()
    val currentUser by authViewModel.currentUser.collectAsState()
    val context = LocalContext.current
    val oneTapClient = remember { Identity.getSignInClient(context) }

    var googleSignInError by remember { mutableStateOf<String?>(null) }
    var isLogin by remember { mutableStateOf(true) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var isPasswordReset by remember { mutableStateOf(false) }

    val isLoading = authState is AuthState.Loading

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        googleSignInError = null
        Log.d("AuthScreen", "[GoogleSignIn] Activity Result: Code = ${result.resultCode}")

        if (result.resultCode == Activity.RESULT_OK) {
            try {
                val credential = oneTapClient.getSignInCredentialFromIntent(result.data)
                val idToken = credential.googleIdToken
                if (idToken != null) {
                    authViewModel.signInWithGoogle(idToken)
                } else {
                    googleSignInError = "Não foi possível obter o token do Google"
                }
            } catch (e: ApiException) {
                googleSignInError = "Erro na autenticação Google: ${e.localizedMessage}"
            } catch (e: Exception) {
                googleSignInError = "Erro inesperado: ${e.message}"
            }
        } else if (result.resultCode == Activity.RESULT_CANCELED) {
            googleSignInError = "Login com Google cancelado"
        } else {
            googleSignInError = "Falha no login com Google"
        }
    }

    fun startGoogleSignIn() {
        if (!isLoading) {
            try {
                googleSignInError = null
                val signInRequest = BeginSignInRequest.builder()
                    .setGoogleIdTokenRequestOptions(
                        BeginSignInRequest.GoogleIdTokenRequestOptions.builder()
                            .setSupported(true)
                            .setServerClientId(context.getString(R.string.default_web_client_id))
                            .setFilterByAuthorizedAccounts(false)
                            .build()
                    )
                    .setAutoSelectEnabled(true)
                    .build()

                oneTapClient.beginSignIn(signInRequest)
                    .addOnSuccessListener { result ->
                        val intentSenderRequest = IntentSenderRequest.Builder(result.pendingIntent.intentSender).build()
                        googleSignInLauncher.launch(intentSenderRequest)
                    }
                    .addOnFailureListener {
                        googleSignInError = "Não foi possível iniciar o login com Google"
                    }
            } catch (e: Exception) {
                googleSignInError = "Erro: ${e.message}"
            }
        }
    }

    LaunchedEffect(isLogin, isPasswordReset) {
        email = ""
        password = ""
        googleSignInError = null
    }

    LaunchedEffect(authState, currentUser) {
        if (authState is AuthState.Success && currentUser != null) {
            onNavigateToChat()
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.padding(bottom = 32.dp)
            )

            if (isPasswordReset) {
                Text("Recuperação de Senha", style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    placeholder = { Text("Email") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !isLoading
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { authViewModel.resetPassword(email) },
                    enabled = email.isNotEmpty() && !isLoading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Enviar Email de Recuperação")
                }
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = { isPasswordReset = false },
                    enabled = !isLoading
                ) { Text("Voltar ao Login") }
            } else {
                Text(if (isLogin) "Login" else "Criar Conta", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(24.dp)) // Espaço entre o título e os inputs
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    placeholder = { Text("Email") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !isLoading
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    placeholder = { Text("Senha") },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    enabled = !isLoading
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { showPassword = !showPassword }) {
                        Text(if (showPassword) "Esconder" else "Mostrar")
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
                Button(
                    onClick = {
                        if (isLogin) authViewModel.loginWithEmail(email, password)
                        else authViewModel.registerWithEmail(email, password)
                    },
                    enabled = email.isNotEmpty() && password.isNotEmpty() && !isLoading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isLogin) "Entrar" else "Registrar")
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text("OU")
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(
                    onClick = { startGoogleSignIn() },
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_google_logo),
                        contentDescription = "Google Logo",
                        modifier = Modifier.size(20.dp),
                        tint = Color.Unspecified
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.sign_in_with_google))
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { isLogin = !isLogin }) {
                        Text(if (isLogin) "Criar Conta" else "Já Tenho Conta")
                    }
                    if (isLogin) {
                        TextButton(onClick = { isPasswordReset = true }) {
                            Text("Esqueci Senha")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            TextButton(onClick = { onBackToChat() }) {
                Text("Voltar ao Chat")
            }

            val errorMessage = googleSignInError ?: (authState as? AuthState.Error)?.message
            if (errorMessage != null && !isLoading) {
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}
