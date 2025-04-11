package com.example.mentesa

// Representa uma mensagem no chat
data class ChatMessage(val text: String, val sender: Sender)

// Enumera os possíveis remetentes de uma mensagem
enum class Sender {
    USER, BOT
}

// Representa um item na lista de conversas
data class ConversationDisplayItem(
    val id: Long,
    val displayTitle: String,
    val lastUpdated: Long,
    val conversationType: ConversationType = ConversationType.GENERAL
)