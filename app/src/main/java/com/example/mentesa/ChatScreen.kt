package com.example.mentesa

import android.util.Log
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mentesa.ui.theme.*
import dev.jeziellago.compose.markdowntext.MarkdownText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onLogin: () -> Unit = {},
    onLogout: () -> Unit = {},
    chatViewModel: ChatViewModel = viewModel(),
    authViewModel: AuthViewModel = viewModel()
) {
    val messages by chatViewModel.messages.collectAsState()
    val conversationDisplayList by chatViewModel.conversationListForDrawer.collectAsState()
    val currentConversationId by chatViewModel.currentConversationId.collectAsState()
    val isLoading by chatViewModel.isLoading.collectAsState()
    val errorMessage by chatViewModel.errorMessage.collectAsState()
    val currentUser by authViewModel.currentUser.collectAsState()
    val logoutEvent by authViewModel.logoutEvent.collectAsState()

    var userMessage by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    var conversationIdToRename by remember { mutableStateOf<Long?>(null) }
    var currentTitleForDialog by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirmationDialog by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(logoutEvent) {
        if (logoutEvent) {
            userMessage = ""
            chatViewModel.handleLogout()
            Log.d("ChatScreen", "Tela e menu lateral limpos após logout")
        }
    }

    LaunchedEffect(conversationIdToRename) {
        val id = conversationIdToRename
        currentTitleForDialog = if (id != null && id != NEW_CONVERSATION_ID) "" else null
        if (id != null && id != NEW_CONVERSATION_ID) {
            Log.d("ChatScreen", "Fetching title for rename dialog (ID: $id)")
            try {
                currentTitleForDialog = chatViewModel.getDisplayTitle(id)
            } catch (e: Exception) {
                Log.e("ChatScreen", "Error fetching title for rename dialog", e)
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppDrawerContent(
                conversationDisplayItems = conversationDisplayList,
                currentConversationId = currentConversationId,
                onConversationClick = { conversationId ->
                    coroutineScope.launch { drawerState.close() }
                    if (conversationId != currentConversationId) {
                        chatViewModel.selectConversation(conversationId)
                    }
                },
                onNewChatClick = {
                    coroutineScope.launch { drawerState.close() }
                    if (currentConversationId != null && currentConversationId != NEW_CONVERSATION_ID) {
                        chatViewModel.startNewConversation()
                    }
                },
                onDeleteConversationRequest = { conversationId ->
                    showDeleteConfirmationDialog = conversationId
                },
                onRenameConversationRequest = { conversationId ->
                    Log.d("ChatScreen", "Rename requested for $conversationId. Setting state.")
                    conversationIdToRename = conversationId
                }
            )
        }
    ) {
        // Esta modificação altera apenas a parte da Surface e Scaffold no ChatScreen.kt
// Mantém toda a funcionalidade original, só adiciona um Box verde acima da TopBar

// Parte modificada do ChatScreen.kt:
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = BackgroundColor
        ) {
            // Em vez do Scaffold diretamente, envolva-o em uma Column
            Column(modifier = Modifier.fillMaxSize()) {
                // Box verde para criar o espaço com a cor desejada
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(0.dp) // Ajuste este valor conforme desejado
                        .background(PrimaryColor) // Mesma cor da TopBar
                )

                // O Scaffold fica exatamente como estava antes, sem alterações
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    contentWindowInsets = WindowInsets(0),
                    topBar = {
                        // TopBar inalterada
                        CenterAlignedTopAppBar(
                            windowInsets = WindowInsets(0),
                            title = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.baseline_account_circle_24),
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(id = R.string.app_name),
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            },
                            navigationIcon = {
                                IconButton(onClick = { coroutineScope.launch { drawerState.open() } }) {
                                    Icon(
                                        Icons.Filled.Menu,
                                        stringResource(R.string.open_drawer_description),
                                        tint = Color.White
                                    )
                                }
                            },
                            actions = {
                                IconButton(
                                    onClick = {
                                        if (currentUser != null) {
                                            onLogout()
                                        } else {
                                            onLogin()
                                        }
                                    },
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f))
                                ) {
                                    Icon(
                                        imageVector = if (currentUser != null) Icons.Default.Logout else Icons.Default.Login,
                                        contentDescription = if (currentUser != null) "Sair" else "Entrar",
                                        tint = Color.White
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                                containerColor = PrimaryColor,
                                titleContentColor = Color.White,
                                navigationIconContentColor = Color.White,
                                actionIconContentColor = Color.White
                            )
                        )
                    },
                    bottomBar = {
                        MessageInput(
                            message = userMessage,
                            onMessageChange = { userMessage = it },
                            onSendClick = {
                                if (userMessage.isNotBlank()) {
                                    chatViewModel.sendMessage(userMessage)
                                    userMessage = ""
                                }
                            },
                            isSendEnabled = !isLoading
                        )
                    },
                    containerColor = BackgroundColor,
                    contentColor = TextColorDark
                ) { paddingValues ->
                    // Conteúdo do chat inalterado
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                            .background(BackgroundColor)
                    ) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(messages, key = { "${it.sender}-${it.text.hashCode()}" }) { message ->
                                MessageBubble(message = message)
                            }
                            if (isLoading) {
                                item { TypingBubbleAnimation(modifier = Modifier.padding(vertical = 4.dp)) }
                            }
                        }

                        errorMessage?.let { errorMsg ->
                            Text(
                                text = "Erro: $errorMsg",
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                                    .background(
                                        color = Color(0xFFE53935),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .padding(vertical = 8.dp, horizontal = 12.dp)
                            )
                        }
                    }
                }
            }
        }

        showDeleteConfirmationDialog?.let { conversationIdToDelete ->
            AlertDialog(
                onDismissRequest = { showDeleteConfirmationDialog = null },
                title = {
                    Text(
                        stringResource(R.string.delete_confirmation_title),
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        stringResource(R.string.delete_confirmation_text),
                        fontWeight = FontWeight.Medium
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            chatViewModel.deleteConversation(conversationIdToDelete)
                            showDeleteConfirmationDialog = null
                        }
                    ) {
                        Text(
                            stringResource(R.string.delete_confirm_button),
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirmationDialog = null }) {
                        Text(
                            stringResource(R.string.cancel_button),
                            fontWeight = FontWeight.Medium
                        )
                    }
                },
                shape = RoundedCornerShape(16.dp)
            )
        }

        conversationIdToRename?.let { id ->
            if (currentTitleForDialog != null) {
                RenameConversationDialog(
                    conversationId = id,
                    currentTitle = currentTitleForDialog,
                    onConfirm = { confirmedId, newTitle ->
                        chatViewModel.renameConversation(confirmedId, newTitle)
                        conversationIdToRename = null
                    },
                    onDismiss = {
                        conversationIdToRename = null
                    }
                )
            }
        }
    }

    LaunchedEffect(messages.size, isLoading) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }
}


@Composable
fun MessageInput(
    message: String,
    onMessageChange: (String) -> Unit,
    onSendClick: () -> Unit,
    isSendEnabled: Boolean
) {
    var isExpanded by remember { mutableStateOf(false) }

    val lineCount = if (message.isBlank()) 1 else message.count { it == '\n' } + 1
    val showExpandButton = lineCount >= 2 || message.length > 80

    val minHeight = 56.dp
    val maxHeight = 150.dp
    val currentHeight = if (isExpanded) maxHeight else minHeight

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(BackgroundColor)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = SurfaceColor,
                    shape = RoundedCornerShape(28.dp)
                )
                .padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = message,
                onValueChange = onMessageChange,
                placeholder = {
                    Text(
                        text = stringResource(R.string.message_input_placeholder),
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = Color.Gray,
                            fontWeight = FontWeight.Medium,
                            fontSize = 16.sp
                        )
                    )
                },
                textStyle = TextStyle(
                    fontWeight = FontWeight.Medium,
                    color = Color.Black,
                    fontSize = 16.sp
                ),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = minHeight),
                shape = RoundedCornerShape(24.dp),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    errorIndicatorColor = Color.Transparent,
                    focusedContainerColor = SurfaceColor,
                    unfocusedContainerColor = SurfaceColor,
                    cursorColor = PrimaryColor
                ),
                enabled = isSendEnabled,
                maxLines = if (isExpanded) 8 else 3
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (showExpandButton) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.LightGray.copy(alpha = 0.2f))
                            .clickable { isExpanded = !isExpanded },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isExpanded) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Contrair campo de texto",
                                tint = PrimaryColor,
                                modifier = Modifier.size(20.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowUp,
                                contentDescription = "Expandir campo de texto",
                                tint = PrimaryColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .padding(bottom = 2.dp)
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            if (message.isNotBlank() && isSendEnabled)
                                PrimaryColor
                            else
                                PrimaryColor.copy(alpha = 0.5f)
                        )
                        .clickable(enabled = message.isNotBlank() && isSendEnabled) {
                            onSendClick()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = stringResource(R.string.action_send),
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun MessageBubble(message: ChatMessage) {
    val isUserMessage = message.sender == Sender.USER

    val userShape = RoundedCornerShape(
        topStart = 20.dp,
        topEnd = 20.dp,
        bottomStart = 20.dp,
        bottomEnd = 6.dp
    )

    val botShape = RoundedCornerShape(
        topStart = 6.dp,
        topEnd = 20.dp,
        bottomStart = 20.dp,
        bottomEnd = 20.dp
    )

    val visibleState = remember { MutableTransitionState(initialState = isUserMessage) }

    LaunchedEffect(message) {
        if (!isUserMessage) {
            visibleState.targetState = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        contentAlignment = if (isUserMessage) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        if (isUserMessage) {
            Card(
                modifier = Modifier
                    .widthIn(max = LocalConfiguration.current.screenWidthDp.dp * 0.75f),
                shape = userShape,
                colors = CardDefaults.cardColors(
                    containerColor = UserBubbleColor,
                ),
                elevation = CardDefaults.cardElevation(
                    defaultElevation = 2.dp
                )
            ) {
                SelectionContainer {
                    Text(
                        text = message.text,
                        color = Color.Black,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.Medium
                        ),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
            }
        } else {
            AnimatedVisibility(
                visibleState = visibleState,
                enter = fadeIn(animationSpec = tween(durationMillis = 300)) +
                        slideInHorizontally(
                            initialOffsetX = { -40 },
                            animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing)
                        )
            ) {
                Card(
                    modifier = Modifier
                        .widthIn(max = LocalConfiguration.current.screenWidthDp.dp * 0.75f),
                    shape = botShape,
                    colors = CardDefaults.cardColors(
                        containerColor = BotBubbleColor
                    ),
                    elevation = CardDefaults.cardElevation(
                        defaultElevation = 2.dp
                    )
                ) {
                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        MarkdownText(
                            markdown = message.text,
                            color = Color.White,
                            fontSize = MaterialTheme.typography.bodyLarge.fontSize,
                            linkColor = Color(0xFFB8E2FF),
                            onClick = { }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TypingIndicatorAnimation(
    modifier: Modifier = Modifier,
    dotColor: Color = Color.White,
    dotSize: Dp = 8.dp,
    spaceBetweenDots: Dp = 4.dp,
    bounceHeight: Dp = 6.dp
) {
    val dots = listOf(remember { Animatable(0f) }, remember { Animatable(0f) }, remember { Animatable(0f) })
    val bounceHeightPx = with(LocalDensity.current) { bounceHeight.toPx() }

    dots.forEachIndexed { index, animatable ->
        LaunchedEffect(animatable) {
            delay(index * 140L)
            animatable.animateTo(
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = 1000
                        0f at 0 using LinearOutSlowInEasing
                        bounceHeightPx at 250 using LinearOutSlowInEasing
                        0f at 500 using LinearOutSlowInEasing
                        0f at 1000
                    },
                    repeatMode = RepeatMode.Restart
                )
            )
        }
    }

    Card(
        modifier = modifier
            .wrapContentWidth()
            .padding(8.dp),
        shape = RoundedCornerShape(
            topStart = 6.dp,
            topEnd = 20.dp,
            bottomStart = 20.dp,
            bottomEnd = 20.dp
        ),
        colors = CardDefaults.cardColors(
            containerColor = BotBubbleColor
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            dots.forEachIndexed { index, animatable ->
                if (index != 0) {
                    Spacer(modifier = Modifier.width(spaceBetweenDots))
                }
                Box(
                    modifier = Modifier
                        .size(dotSize)
                        .graphicsLayer { translationY = -animatable.value * 4 }
                        .background(color = dotColor, shape = CircleShape)
                )
            }
        }
    }
}

@Composable
fun TypingBubbleAnimation(modifier: Modifier = Modifier) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        TypingIndicatorAnimation(modifier = modifier)
    }
}