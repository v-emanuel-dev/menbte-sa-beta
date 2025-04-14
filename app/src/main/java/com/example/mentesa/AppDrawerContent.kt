// Primeiro código (AppDrawerContent) sem comentários:
package com.example.mentesa

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.mentesa.ui.theme.*


@Composable
fun AppDrawerContent(
    conversationDisplayItems: List<ConversationDisplayItem>,
    currentConversationId: Long?,
    onConversationClick: (Long) -> Unit,
    onNewChatClick: () -> Unit,
    onDeleteConversationRequest: (Long) -> Unit,
    onRenameConversationRequest: (Long) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxHeight()
            .width(280.dp),
        color = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 16.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(PrimaryColor.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.baseline_account_circle_24),
                            contentDescription = null,
                            tint = PrimaryColor,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Text(
                        text = stringResource(id = R.string.app_name),
                        style = MaterialTheme.typography.titleLarge,
                        color = PrimaryColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Divider(
                modifier = Modifier.fillMaxWidth(),
                thickness = 1.dp,
                color = Color.LightGray.copy(alpha = 0.5f)
            )

            Spacer(modifier = Modifier.height(8.dp))

            NewChatButton(
                onClick = onNewChatClick,
                isSelected = currentConversationId == null || currentConversationId == NEW_CONVERSATION_ID
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (conversationDisplayItems.isNotEmpty()) {
                Text(
                    text = "Suas conversas",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = Color.DarkGray,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(conversationDisplayItems) { item ->
                        ConversationItem(
                            item = item,
                            isSelected = currentConversationId == item.id,
                            onClick = { onConversationClick(item.id) },
                            onRenameClick = { onRenameConversationRequest(item.id) },
                            onDeleteClick = { onDeleteConversationRequest(item.id) }
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Nenhuma conversa salva",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.Medium
                            ),
                            color = Color.DarkGray,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Suas conversas aparecerão aqui",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.DarkGray.copy(alpha = 0.8f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Versão 1.0",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.Medium
                    ),
                    color = Color.DarkGray
                )
            }
        }
    }
}

@Composable
fun NewChatButton(
    onClick: () -> Unit,
    isSelected: Boolean
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) PrimaryColor.copy(alpha = 0.15f) else Color.Transparent,
        animationSpec = tween(durationMillis = 200),
        label = "backgroundColor"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 0.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(PrimaryColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Nova conversa",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = "Nova conversa",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (isSelected) PrimaryColor else Color.DarkGray
            )
        }
    }
}

@Composable
fun ConversationItem(
    item: ConversationDisplayItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    onRenameClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) PrimaryColor.copy(alpha = 0.15f) else Color.Transparent,
        animationSpec = tween(durationMillis = 200),
        label = "backgroundColor"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 0.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(SecondaryColor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = item.displayTitle.firstOrNull()?.uppercase() ?: "C",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = item.displayTitle,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isSelected) PrimaryColor else Color.Black,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            Row {
                IconButton(
                    onClick = { onRenameClick() },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Renomear",
                        tint = Color.DarkGray,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = { onDeleteClick() },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Excluir",
                        tint = Color.DarkGray,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun RenameConversationDialog(
    conversationId: Long,
    currentTitle: String?,
    onConfirm: (Long, String) -> Unit,
    onDismiss: () -> Unit
) {
    var newTitle by remember { mutableStateOf(currentTitle ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Renomear conversa",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = newTitle,
                    onValueChange = { newTitle = it },
                    label = {
                        Text(
                            "Novo nome",
                            fontWeight = FontWeight.Medium,
                            color = Color.DarkGray
                        )
                    },
                    textStyle = TextStyle(
                        fontWeight = FontWeight.Medium,
                        color = Color.Black
                    ),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedBorderColor = PrimaryColor,
                        unfocusedBorderColor = Color.Gray.copy(alpha = 0.5f),
                        focusedLabelColor = PrimaryColor,
                        cursorColor = PrimaryColor
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (newTitle.isNotBlank()) {
                        onConfirm(conversationId, newTitle)
                    }
                },
                enabled = newTitle.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryColor
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    "Salvar",
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss
            ) {
                Text(
                    "Cancelar",
                    color = Color.DarkGray,
                    fontWeight = FontWeight.Medium
                )
            }
        },
        shape = RoundedCornerShape(16.dp),
        containerColor = Color.White,
        tonalElevation = 8.dp
    )
}