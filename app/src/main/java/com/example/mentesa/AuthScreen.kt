package com.example.mentesa

import android.app.Activity
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons // Import Icons
import androidx.compose.material.icons.filled.Visibility // Import Ícone Visibilidade
import androidx.compose.material.icons.filled.VisibilityOff // Import Ícone Não Visibilidade
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType // Import KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation // Import VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mentesa.ui.theme.*
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable

@Composable
fun AuthScreen(
    onNavigateToChat: () -> Unit,
    onBackToChat: () -> Unit,
    authViewModel: AuthViewModel = viewModel()
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLogin by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var passwordVisible by remember { mutableStateOf(false) } // <-- NOVO ESTADO

    val authState by authViewModel.authState.collectAsState()
    val context = LocalContext.current

    val googleSignInClient = remember {
        GoogleSignIn.getClient(
            context,
            GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(context.getString(R.string.default_web_client_id))
                .requestEmail()
                .build()
        )
    }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                account?.idToken?.let { idToken ->
                    authViewModel.signInWithGoogle(idToken)
                }
            } catch (e: ApiException) {
                Log.e("GoogleSignIn", "Google sign in failed", e)
                errorMessage = "Falha na autenticação com Google: ${e.localizedMessage}"
            }
        }
    }

    LaunchedEffect(authState) {
        when (authState) {
            is AuthState.Success -> onNavigateToChat()
            is AuthState.Error -> errorMessage = (authState as AuthState.Error).message
            else -> { }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColor),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.99f)
                .padding(16.dp)
                .shadow(
                    elevation = 4.dp,
                    spotColor = Color.Black.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(32.dp)
                ),
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.White
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(PrimaryColor.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.baseline_account_circle_24),
                        contentDescription = "Mente Sã Logo",
                        modifier = Modifier.size(80.dp),
                        colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(PrimaryColor)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(animationSpec = tween(durationMillis = 500))
                ) {
                    Text(
                        text = if (isLogin) "Bem-vindo(a) de volta" else "Criar uma conta",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = TextColorDark,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                Text(
                    text = if (isLogin) "Entre na sua conta para continuar" else "Preencha os dados para se cadastrar",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    color = TextColorDark.copy(alpha = 0.9f),
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = {
                        Text(
                            "Email",
                            fontWeight = FontWeight.Medium,
                            color = Color.DarkGray
                        )
                    },
                    textStyle = TextStyle(
                        fontWeight = FontWeight.Medium,
                        color = Color.Black
                    ),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedBorderColor = PrimaryColor,
                        unfocusedBorderColor = Color.Gray.copy(alpha = 0.5f),
                        focusedLabelColor = PrimaryColor,
                        cursorColor = PrimaryColor
                    ),
                    keyboardOptions = KeyboardOptions.Default.copy(
                        imeAction = ImeAction.Next
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                )

                // --- CAMPO DE SENHA MODIFICADO ---
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = {
                        Text(
                            "Senha",
                            fontWeight = FontWeight.Medium,
                            color = Color.DarkGray
                        )
                    },
                    textStyle = TextStyle(
                        fontWeight = FontWeight.Medium,
                        color = Color.Black
                    ),
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(), // <-- MUDANÇA AQUI
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedBorderColor = PrimaryColor,
                        unfocusedBorderColor = Color.Gray.copy(alpha = 0.5f),
                        focusedLabelColor = PrimaryColor,
                        cursorColor = PrimaryColor
                    ),
                    keyboardOptions = KeyboardOptions( // <-- MUDANÇA AQUI
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    trailingIcon = { // <-- ÍCONE ADICIONADO AQUI
                        val image = if (passwordVisible)
                            Icons.Filled.Visibility
                        else Icons.Filled.VisibilityOff
                        val description = if (passwordVisible) "Esconder senha" else "Mostrar senha"
                        IconButton(onClick = {passwordVisible = !passwordVisible}){
                            Icon(imageVector  = image, description)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                )
                // --- FIM DO CAMPO DE SENHA MODIFICADO ---


                AnimatedVisibility(
                    visible = errorMessage != null,
                    enter = fadeIn(animationSpec = tween(durationMillis = 300)),
                    exit = fadeOut(animationSpec = tween(durationMillis = 300))
                ) {
                    errorMessage?.let {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                                .background(
                                    color = Color(0xFFE53935),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(12.dp)
                        ) {
                            Text(
                                text = it,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                Button(
                    onClick = {
                        errorMessage = null
                        if (email.isBlank() || password.isBlank()) {
                            errorMessage = "Por favor, preencha todos os campos"
                            return@Button
                        }

                        if (isLogin) {
                            authViewModel.loginWithEmail(email, password)
                        } else {
                            authViewModel.registerWithEmail(email, password)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryColor,
                        contentColor = Color.White
                    ),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 2.dp,
                        pressedElevation = 0.dp
                    )
                ) {
                    Text(
                        if (isLogin) "Entrar" else "Cadastrar",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Divider(
                        modifier = Modifier
                            .weight(1f)
                            .background(Color.Gray.copy(alpha = 0.3f))
                    )
                    Text(
                        text = "  ou  ",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = TextColorDark.copy(alpha = 0.7f)
                    )
                    Divider(
                        modifier = Modifier
                            .weight(1f)
                            .background(Color.Gray.copy(alpha = 0.3f))
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .border(
                            width = 1.dp,
                            color = Color.Gray.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .background(Color.White)
                        .clickable {
                            googleSignInLauncher.launch(googleSignInClient.signInIntent)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        Icon(
                            imageVector = googleIcon(),
                            contentDescription = "Logo do Google",
                            modifier = Modifier.size(24.dp),
                            tint = Color.Unspecified
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        Text(
                            text = "Login com Google",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextColorDark,
                            maxLines = 1,
                            overflow = TextOverflow.Visible
                        )
                    }
                }

                TextButton(
                    onClick = { isLogin = !isLogin },
                    modifier = Modifier.padding(top = 16.dp)
                ) {
                    Text(
                        if (isLogin) "Não tem uma conta? Cadastre-se" else "Já tem uma conta? Faça login",
                        color = PrimaryColor,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    )
                }

                if (authState is AuthState.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(top = 16.dp),
                        color = PrimaryColor,
                        strokeWidth = 3.dp
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(SecondaryColor.copy(alpha = 0.1f))
                .align(Alignment.TopEnd)
                .offset(x = 40.dp, y = (-20).dp)
        )

        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(PrimaryColor.copy(alpha = 0.1f))
                .align(Alignment.BottomStart)
                .offset(x = (-20).dp, y = 30.dp)
        )
    }
}

@Composable
fun googleIcon() = ImageVector.vectorResource(id = R.drawable.ic_google_logo)