package com.example.mentesa

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mentesa.data.db.AppDatabase
import com.example.mentesa.data.db.ChatMessageEntity
import com.example.mentesa.data.db.ConversationMetadataEntity
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

const val NEW_CONVERSATION_ID = -1L

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val chatDao = db.chatDao()
    private val metadataDao = db.conversationMetadataDao()
    private val auth = FirebaseAuth.getInstance()

    // Current user ID from Firebase
    private val currentUserId: String
        get() = auth.currentUser?.uid ?: "local_user"

    private val _currentConversationId = MutableStateFlow<Long?>(null)
    val currentConversationId: StateFlow<Long?> = _currentConversationId

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages

    private val _conversationListForDrawer = MutableStateFlow<List<ConversationDisplayItem>>(emptyList())
    val conversationListForDrawer: StateFlow<List<ConversationDisplayItem>> = _conversationListForDrawer

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    init {
        viewModelScope.launch {
            // Observe conversations for the current user
            chatDao.getConversationsForUser(currentUserId)
                .onEach { conversations ->
                    if (conversations.isEmpty() && _currentConversationId.value == null) {
                        // No conversations yet, create a new one
                        startNewConversation()
                    } else if (_currentConversationId.value == null && conversations.isNotEmpty()) {
                        // Load the most recent conversation
                        selectConversation(conversations.first().id)
                    }
                }
                .collect()
        }

        viewModelScope.launch {
            combine(
                chatDao.getConversationsForUser(currentUserId),
                metadataDao.getMetadataForUser(currentUserId)
            ) { conversationInfos, metadataEntities ->
                val metadataMap = metadataEntities.associateBy { it.conversationId }

                conversationInfos.map { info ->
                    val title = metadataMap[info.id]?.customTitle
                        ?: getFirstUserMessageAsync(info.id) ?: "Nova conversa"

                    ConversationDisplayItem(
                        id = info.id,
                        displayTitle = title,
                        lastUpdated = info.lastTimestamp
                    )
                }
            }.collect { items ->
                _conversationListForDrawer.value = items
            }
        }

        // Observe messages for the current conversation
        viewModelScope.launch {
            _currentConversationId
                .filterNotNull()
                .flatMapLatest { id ->
                    if (id == NEW_CONVERSATION_ID) {
                        flowOf(emptyList())
                    } else {
                        chatDao.getMessagesForConversation(id)
                    }
                }
                .collect { messageEntities ->
                    _messages.value = messageEntities.map { entity ->
                        ChatMessage(
                            text = entity.text,
                            sender = if (entity.sender == "USER") Sender.USER else Sender.BOT
                        )
                    }
                }
        }
    }

    fun startNewConversation() {
        _currentConversationId.value = NEW_CONVERSATION_ID
        _messages.value = emptyList()
    }

    fun selectConversation(conversationId: Long) {
        viewModelScope.launch {
            _currentConversationId.value = conversationId
        }
    }

    fun sendMessage(text: String) {
        viewModelScope.launch {
            val currentId = getCurrentConversationId()

            // Add user message to database
            val userMessage = ChatMessageEntity(
                conversationId = currentId,
                text = text,
                sender = "USER",
                timestamp = System.currentTimeMillis(),
                userId = currentUserId
            )
            chatDao.insertMessage(userMessage)

            _isLoading.value = true

            try {
                // Simulate bot response
                delay(1000)
                val botResponse = "Isso é uma resposta simulada do bot."

                // Add bot message to database
                val botMessage = ChatMessageEntity(
                    conversationId = currentId,
                    text = botResponse,
                    sender = "BOT",
                    timestamp = System.currentTimeMillis(),
                    userId = currentUserId
                )
                chatDao.insertMessage(botMessage)
                _errorMessage.value = null
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error sending message", e)
                _errorMessage.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun renameConversation(conversationId: Long, newTitle: String) {
        if (conversationId == NEW_CONVERSATION_ID) return

        viewModelScope.launch {
            metadataDao.insertOrUpdateMetadata(
                ConversationMetadataEntity(
                    conversationId = conversationId,
                    customTitle = newTitle,
                    userId = currentUserId
                )
            )
        }
    }

    fun deleteConversation(conversationId: Long) {
        viewModelScope.launch {
            chatDao.clearConversation(conversationId)
            metadataDao.deleteMetadata(conversationId)

            // If we deleted the current conversation, start a new one
            if (_currentConversationId.value == conversationId) {
                startNewConversation()
            }
        }
    }

    suspend fun getDisplayTitle(conversationId: Long): String {
        if (conversationId == NEW_CONVERSATION_ID) return "Nova conversa"

        return withContext(Dispatchers.IO) {
            metadataDao.getCustomTitle(conversationId)
                ?: getFirstUserMessageAsync(conversationId)
                ?: "Conversa $conversationId"
        }
    }

    private suspend fun getFirstUserMessageAsync(conversationId: Long): String? {
        return withContext(Dispatchers.IO) {
            val message = chatDao.getFirstUserMessageText(conversationId)
            if (message?.length ?: 0 > 30) message?.substring(0, 27) + "..." else message
        }
    }

    private suspend fun getCurrentConversationId(): Long {
        val currentId = _currentConversationId.value
        return if (currentId == null || currentId == NEW_CONVERSATION_ID) {
            // Create a new conversation ID
            val newId = System.currentTimeMillis()
            _currentConversationId.value = newId
            newId
        } else {
            currentId
        }
    }
}