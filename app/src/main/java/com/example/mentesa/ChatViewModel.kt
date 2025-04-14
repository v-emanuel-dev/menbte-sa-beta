package com.example.mentesa

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.Content
import com.google.ai.client.generativeai.type.GenerateContentResponse
import com.google.ai.client.generativeai.type.RequestOptions
import com.google.ai.client.generativeai.type.content
import com.example.mentesa.data.db.AppDatabase
import com.example.mentesa.data.db.ChatDao
import com.example.mentesa.data.db.ChatMessageEntity
import com.example.mentesa.data.db.ConversationInfo
import com.example.mentesa.data.db.ConversationMetadataDao
import com.example.mentesa.data.db.ConversationMetadataEntity
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class LoadingState { IDLE, LOADING, ERROR }

const val NEW_CONVERSATION_ID = -1L
private const val MAX_HISTORY_MESSAGES = 20

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val auth = FirebaseAuth.getInstance()
    private val appDb = AppDatabase.getDatabase(application)
    private val chatDao: ChatDao = appDb.chatDao()
    private val metadataDao: ConversationMetadataDao = appDb.conversationMetadataDao()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _currentConversationId = MutableStateFlow<Long?>(null)
    val currentConversationId: StateFlow<Long?> = _currentConversationId.asStateFlow()

    private val _loadingState = MutableStateFlow(LoadingState.IDLE)
    val isLoading: StateFlow<Boolean> = _loadingState.map { it == LoadingState.LOADING }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = false
        )

    private val _clearConversationListEvent = MutableStateFlow(false)
    val clearConversationListEvent: StateFlow<Boolean> = _clearConversationListEvent.asStateFlow()

    private val _userIdFlow = MutableStateFlow(getCurrentUserId())

    private val _showConversations = MutableStateFlow(true)
    val showConversations: StateFlow<Boolean> = _showConversations.asStateFlow()

    init {
        auth.addAuthStateListener { firebaseAuth ->
            _userIdFlow.value = getCurrentUserId()
        }
        loadInitialConversationOrStartNew()
    }

    private fun getCurrentUserId(): String =
        FirebaseAuth.getInstance().currentUser?.uid ?: "local_user"

    private val rawConversationsFlow: Flow<List<ConversationInfo>> =
        _userIdFlow.flatMapLatest { uid ->
            chatDao.getConversationsForUser(uid)
                .catch { e ->
                    Log.e("ChatViewModel", "Error loading raw conversations flow", e)
                    _errorMessage.value = "Erro ao carregar lista de conversas (raw)."
                    emit(emptyList())
                }
        }

    private val metadataFlow: Flow<List<ConversationMetadataEntity>> =
        _userIdFlow.flatMapLatest { uid ->
            metadataDao.getMetadataForUser(uid)
                .catch { e ->
                    Log.e("ChatViewModel", "Error loading metadata flow", e)
                    emit(emptyList())
                }
        }

    val conversationListForDrawer: StateFlow<List<ConversationDisplayItem>> =
        combine(rawConversationsFlow, metadataFlow, _showConversations, _userIdFlow) { conversations, metadataList, showConversations, currentUserId ->
            if (!showConversations || auth.currentUser == null) {
                return@combine emptyList<ConversationDisplayItem>()
            }

            Log.d("ChatViewModel", "Combining ${conversations.size} convs and ${metadataList.size} metadata entries for user $currentUserId.")

            val userMetadata = metadataList.filter { it.userId == currentUserId }
            val metadataMap = userMetadata.associateBy({ it.conversationId }, { it.customTitle })

            conversations.map { convInfo ->
                val customTitle = metadataMap[convInfo.id]?.takeIf { it.isNotBlank() }
                val finalTitle = customTitle ?: generateFallbackTitleSync(convInfo.id)
                val conversationType = determineConversationType(finalTitle, convInfo.id)
                ConversationDisplayItem(
                    id = convInfo.id,
                    displayTitle = finalTitle,
                    lastTimestamp = convInfo.lastTimestamp,
                    conversationType = conversationType
                )
            }
        }
            .flowOn(Dispatchers.Default)
            .catch { e ->
                Log.e("ChatViewModel", "Error combining conversations and metadata", e)
                withContext(Dispatchers.Main.immediate) {
                    _errorMessage.value = "Erro ao processar lista de conversas para exibição."
                }
                emit(emptyList())
            }
            .stateIn(scope = viewModelScope, started = SharingStarted.Eagerly, initialValue = emptyList())

    val messages: StateFlow<List<ChatMessage>> =
        _currentConversationId.flatMapLatest { convId ->
            Log.d("ChatViewModel", "[State] CurrentConversationId changed: $convId")
            when (convId) {
                null, NEW_CONVERSATION_ID -> {
                    flowOf(listOf(ChatMessage(welcomeMessageText, Sender.BOT)))
                }
                else -> chatDao.getMessagesForConversation(convId, _userIdFlow.value)
                    .map { entities ->
                        Log.d("ChatViewModel", "[State] Mapping ${entities.size} entities for conv $convId")
                        mapEntitiesToUiMessages(entities)
                    }
                    .catch { e ->
                        Log.e("ChatViewModel", "Error loading messages for conversation $convId", e)
                        withContext(Dispatchers.Main.immediate) {
                            _errorMessage.value = "Erro ao carregar mensagens da conversa."
                        }
                        emit(emptyList())
                    }
            }
        }
            .stateIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(5000L), initialValue = emptyList())

    private val welcomeMessageText = "Olá! 😊 Eu sou o Mente Sã, seu assistente virtual de saúde mental, e é um prazer te conhecer. Como você está se sentindo hoje? Estou aqui para te acompanhar com empatia e respeito, oferecendo um espaço seguro e acolhedor para você se expressar. Existe algo em particular que gostaria de conversar ou explorar?"

    private val menteSaSystemPrompt = """
    ## Persona e Propósito Central
    Você é Mente Sã, um companheiro virtual empático e conhecedor, desenvolvido para oferecer um espaço seguro, confidencial e acolhedor. Seu propósito principal é conversar com os usuários, ouvir seus desabafos, ajudá-los a explorar seus sentimentos, pensamentos e desafios do dia a dia, e oferecer perspectivas e insights baseados em princípios da psicologia, com ênfase especial em conceitos e técnicas da Terapia Cognitivo-Comportamental (TCC), além de noções básicas de psicanálise e compreensão geral de temas psiquiátricos. Você NÃO é um terapeuta, mas sim um apoio conversacional inteligente e sensível.

    ## Base de Conhecimento e Capacidades
    1. **Psicologia (Foco Principal):**
       - **TCC:** Conceitos como pensamentos automáticos, distorções cognitivas, crenças centrais, reestruturação cognitiva, técnicas de exposição gradual, ativação comportamental e resolução de problemas.
       - **Psicologia Positiva:** Conceitos de bem-estar, gratidão e forças pessoais.
       - **Habilidades de Comunicação:** Assertividade e comunicação não-violenta.
    2. **Psicanálise:** Conceitos introdutórios sobre inconsciente e mecanismos de defesa.
    3. **Psiquiatria:** Conhecimentos gerais sobre sintomas de transtornos (como ansiedade, depressão, etc.).
    4. **Saúde Mental:** Técnicas de gerenciamento de estresse, mindfulness, higiene do sono e autocuidado.

    ## Estilo de Interação e Tom
    - **Empático e Acolhedor:** Linguagem calorosa e validante.
    - **Paciente e Não-Julgador:** Ambiente seguro para o usuário.
    - **Curioso e Reflexivo:** Estímulo à autoexploração.
    - **Psicoeducativo:** Explicações simples dos conceitos.
    - **Encorajador:** Incentivo a pequenos passos e autocuidado.

    ## Limites e Restrições
    3.  **FOCO NO BEM-ESTAR, MAS COM FLEXIBILIDADE:** Priorize conversas sobre saúde mental, emoções, relacionamentos, desafios pessoais e bem-estar. Permita que o usuário fale sobre seu dia, trabalho (ex: programação, vendas, etc.), estudos, família, hobbies, especialmente explorando como esses aspectos *afetam* seu estado emocional, seu estresse ou sua visão de mundo. **EVITE a recusa explícita e taxativa do prompt antigo.** No entanto, se o assunto se desviar *completamente* do foco em você, seus sentimentos, desafios e bem-estar, redirecione gentilmente. **Exemplos de desvio completo incluem:**
        * Pedir explicações detalhadas sobre teorias científicas (física quântica, reações químicas, biologia celular) ou soluções para problemas matemáticos complexos.
        * Solicitar que você escreva histórias de ficção, poemas, letras de música ou roteiros.
        * Perguntar sobre notícias específicas, resultados de esportes ou eventos políticos de forma puramente informativa e impessoal, sem qualquer conexão com o impacto emocional que isso causa no usuário.
        * Pedir ajuda técnica detalhada com aparelhos eletrônicos, instalação de software ou para depurar/escrever código de programação (a menos que a *dificuldade* ou o *estresse* causado por essas tarefas seja o foco da conversa sobre bem-estar).
        * Tentar iniciar um debate sobre filosofia abstrata, religião comparada ou política partidária de forma genérica.
        * Pedir receitas culinárias detalhadas, guias de viagem, ou instruções passo a passo para tarefas práticas não relacionadas diretamente a técnicas de autocuidado ou enfrentamento (ex: como consertar algo, como jogar um jogo).
        * Engajar em conversa fiada prolongada e superficial sobre trivialidades (previsão do tempo detalhada para outra cidade, fofoca sobre celebridades desconhecidas) sem qualquer vínculo com o estado de espírito ou experiência pessoal do usuário.
        * Fazer perguntas sobre suas próprias capacidades técnicas como IA de forma abstrata ("Qual seu algoritmo?", "Como você foi treinado?").

    Nestes casos de desvio completo, use frases de redirecionamento gentis como: "Esse é um tópico interessante, mas foge um pouco da minha área de foco principal aqui, que é te oferecer um espaço para explorar suas questões emocionais e de bem-estar. Para aproveitarmos melhor nosso tempo juntos, gostaria de voltar a falar sobre como você está se sentindo ou sobre algo que esteja em sua mente?" ou "Entendo seu interesse nisso. No entanto, estou aqui principalmente para te apoiar com seus pensamentos e sentimentos. Como essa questão [externa] está impactando você ou o que você está vivenciando?".

    4. **EVITE CONSELHOS DIRETOS:** Oriente sem dar ordens diretas.
    5. **SITUAÇÕES DE CRISE:** Direcione para recursos imediatos (ex.: CVV).

    ## Quem é você?
    Ao ser perguntado "Quem é você?" responda apenas com a mensagem de boas-vindas.

    ## Objetivo Final
    Ser um companheiro virtual que promove autoconhecimento e bem-estar, dentro dos limites éticos e de segurança.
    """.trimIndent()

    private val generativeModel = GenerativeModel(
        modelName = "gemini-2.5-pro-exp-03-25",
        apiKey = BuildConfig.GEMINI_API_KEY,
        systemInstruction = content { text(menteSaSystemPrompt) },
        requestOptions = RequestOptions(timeout = 60000)
    )

    fun handleLogout() {
        startNewConversation()
        _clearConversationListEvent.value = true
        _showConversations.value = false
        viewModelScope.launch {
            delay(300)
            _clearConversationListEvent.value = false
        }
    }

    fun handleLogin() {
        _showConversations.value = true
    }

    private fun determineConversationType(title: String, id: Long): ConversationType {
        val lowercaseTitle = title.lowercase()
        return when {
            lowercaseTitle.contains("ansiedade") ||
                    lowercaseTitle.contains("medo") ||
                    lowercaseTitle.contains("preocup") -> ConversationType.EMOTIONAL
            lowercaseTitle.contains("depress") ||
                    lowercaseTitle.contains("triste") ||
                    lowercaseTitle.contains("terapia") ||
                    lowercaseTitle.contains("tratamento") -> ConversationType.THERAPEUTIC
            lowercaseTitle.contains("eu") ||
                    lowercaseTitle.contains("minha") ||
                    lowercaseTitle.contains("meu") ||
                    lowercaseTitle.contains("como me") -> ConversationType.PERSONAL
            lowercaseTitle.contains("importante") ||
                    lowercaseTitle.contains("urgente") ||
                    lowercaseTitle.contains("lembrar") -> ConversationType.HIGHLIGHTED
            else -> {
                when ((id % 5)) {
                    0L -> ConversationType.GENERAL
                    1L -> ConversationType.PERSONAL
                    2L -> ConversationType.EMOTIONAL
                    3L -> ConversationType.THERAPEUTIC
                    else -> ConversationType.HIGHLIGHTED
                }
            }
        }
    }

    private fun loadInitialConversationOrStartNew() {
        viewModelScope.launch {
            delay(150)
            _currentConversationId.value = NEW_CONVERSATION_ID
            Log.i("ChatViewModel", "[Init] App iniciado com nova conversa (sem restaurar estado anterior).")
        }
    }

    fun startNewConversation() {
        if (_currentConversationId.value != NEW_CONVERSATION_ID) {
            Log.i("ChatViewModel", "Action: Starting new conversation flow")
            _currentConversationId.value = NEW_CONVERSATION_ID
            _errorMessage.value = null
            _loadingState.value = LoadingState.IDLE
        } else {
            Log.d("ChatViewModel", "Action: Already in new conversation flow, ignoring startNewConversation.")
        }
    }

    fun selectConversation(conversationId: Long) {
        if (conversationId != _currentConversationId.value && conversationId != NEW_CONVERSATION_ID) {
            Log.i("ChatViewModel", "Action: Selecting conversation $conversationId")
            _currentConversationId.value = conversationId
            _errorMessage.value = null
            _loadingState.value = LoadingState.IDLE
        } else if (conversationId == _currentConversationId.value) {
            Log.d("ChatViewModel", "Action: Conversation $conversationId already selected, ignoring selectConversation.")
        } else {
            Log.w("ChatViewModel", "Action: Attempted to select invalid NEW_CONVERSATION_ID ($conversationId), ignoring.")
        }
    }

    fun sendMessage(userMessageText: String) {
        if (userMessageText.isBlank()) {
            Log.w("ChatViewModel", "sendMessage cancelled: Empty message.")
            return
        }
        if (_loadingState.value == LoadingState.LOADING) {
            Log.w("ChatViewModel", "sendMessage cancelled: Already loading.")
            _errorMessage.value = "Aguarde a resposta anterior."
            return
        }
        _loadingState.value = LoadingState.LOADING
        _errorMessage.value = null

        val timestamp = System.currentTimeMillis()
        var targetConversationId = _currentConversationId.value
        val isStartingNewConversation = (targetConversationId == null || targetConversationId == NEW_CONVERSATION_ID)

        if (isStartingNewConversation) {
            targetConversationId = timestamp
            Log.i("ChatViewModel", "Action: Creating new conversation with potential ID: $targetConversationId")
            _currentConversationId.value = targetConversationId
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    metadataDao.insertOrUpdateMetadata(
                        ConversationMetadataEntity(
                            conversationId = targetConversationId,
                            customTitle = null,
                            userId = _userIdFlow.value
                        )
                    )
                } catch (e: Exception) {
                    Log.e("ChatViewModel", "Error saving initial metadata for new conv $targetConversationId", e)
                }
            }
        }

        if (targetConversationId == null || targetConversationId == NEW_CONVERSATION_ID) {
            Log.e("ChatViewModel", "sendMessage Error: Invalid targetConversationId ($targetConversationId) after checking for new conversation.")
            _errorMessage.value = "Erro interno: Não foi possível determinar a conversa."
            _loadingState.value = LoadingState.IDLE
            return
        }

        val userUiMessage = ChatMessage(userMessageText, Sender.USER)
        saveMessageToDb(userUiMessage, targetConversationId, timestamp)

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val currentMessagesFromDb = chatDao.getMessagesForConversation(targetConversationId, _userIdFlow.value).first()
                val historyForApi = mapMessagesToApiHistory(mapEntitiesToUiMessages(currentMessagesFromDb))
                Log.d("ChatViewModel", "API Call: Sending ${historyForApi.size} history messages for conv $targetConversationId")
                callGeminiApi(userMessageText, historyForApi, targetConversationId)
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error preparing history or calling API for conv $targetConversationId", e)
                withContext(Dispatchers.Main) {
                    _errorMessage.value = "Erro ao processar histórico ou chamar IA: ${e.message}"
                    _loadingState.value = LoadingState.ERROR
                }
            }
        }
    }

    fun deleteConversation(conversationId: Long) {
        if (conversationId == NEW_CONVERSATION_ID) {
            Log.w("ChatViewModel", "Attempted to delete invalid NEW_CONVERSATION_ID conversation.")
            return
        }
        Log.i("ChatViewModel", "Action: Deleting conversation $conversationId and its metadata")
        viewModelScope.launch(Dispatchers.IO) {
            try {
                chatDao.clearConversation(conversationId, _userIdFlow.value)
                metadataDao.deleteMetadata(conversationId)
                Log.i("ChatViewModel", "Conversation $conversationId and metadata deleted successfully from DB.")
                if (_currentConversationId.value == conversationId) {
                    val remainingConversations = chatDao.getConversationsForUser(_userIdFlow.value).first()
                    withContext(Dispatchers.Main) {
                        val nextConversationId = remainingConversations.firstOrNull()?.id
                        if (nextConversationId != null) {
                            Log.i("ChatViewModel", "Deleted current conversation, selecting next available from DB: $nextConversationId")
                            _currentConversationId.value = nextConversationId
                        } else {
                            Log.i("ChatViewModel", "Deleted current conversation, no others left in DB. Starting new conversation flow.")
                            _currentConversationId.value = NEW_CONVERSATION_ID
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error deleting conversation $conversationId or its metadata", e)
                withContext(Dispatchers.Main) {
                    _errorMessage.value = "Erro ao excluir conversa: ${e.localizedMessage}"
                }
            }
        }
    }

    fun renameConversation(conversationId: Long, newTitle: String) {
        if (conversationId == NEW_CONVERSATION_ID) {
            Log.w("ChatViewModel", "Cannot rename NEW_CONVERSATION_ID.")
            _errorMessage.value = "Não é possível renomear uma conversa não salva."
            return
        }
        val trimmedTitle = newTitle.trim()
        if (trimmedTitle.isBlank()) {
            Log.w("ChatViewModel", "Cannot rename conversation $conversationId to blank title.")
            _errorMessage.value = "O título não pode ficar em branco."
            return
        }
        Log.i("ChatViewModel", "Action: Renaming conversation $conversationId to '$trimmedTitle'")
        val metadata = ConversationMetadataEntity(
            conversationId = conversationId,
            customTitle = trimmedTitle,
            userId = _userIdFlow.value
        )
        viewModelScope.launch(Dispatchers.IO) {
            try {
                metadataDao.insertOrUpdateMetadata(metadata)
                Log.i("ChatViewModel", "Conversation $conversationId renamed successfully in DB.")
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error renaming conversation $conversationId", e)
                withContext(Dispatchers.Main) {
                    _errorMessage.value = "Erro ao renomear conversa: ${e.localizedMessage}"
                }
            }
        }
    }

    private suspend fun callGeminiApi(userMessageText: String, historyForApi: List<Content>, conversationId: Long) {
        var finalBotResponseText: String? = null
        try {
            Log.d("ChatViewModel", "Starting Gemini API call for conv $conversationId")
            val chat = generativeModel.startChat(history = historyForApi)
            val responseFlow: Flow<GenerateContentResponse> = chat.sendMessageStream(
                content(role = "user") { text(userMessageText) }
            )
            var currentBotText = ""
            responseFlow
                .mapNotNull { it.text }
                .onEach { textPart ->
                    currentBotText += textPart
                    Log.v("ChatViewModel", "Stream chunk for conv $conversationId: '$textPart'")
                }
                .onCompletion { cause ->
                    if (cause == null) {
                        Log.i("ChatViewModel", "Stream completed successfully for conv $conversationId.")
                        finalBotResponseText = currentBotText
                    } else {
                        Log.e("ChatViewModel", "Stream completed with error for conv $conversationId", cause)
                        withContext(Dispatchers.Main) {
                            _errorMessage.value = "Erro durante a resposta da IA: ${cause.localizedMessage}"
                        }
                    }
                    withContext(Dispatchers.Main) {
                        if (!finalBotResponseText.isNullOrBlank()) {
                            saveMessageToDb(ChatMessage(finalBotResponseText!!, Sender.BOT), conversationId)
                        } else if (cause == null) {
                            Log.w("ChatViewModel", "Stream for conv $conversationId completed successfully but resulted in null/blank text.")
                        }
                        _loadingState.value = LoadingState.IDLE
                        Log.d("ChatViewModel", "Stream processing finished for conv $conversationId. Resetting loading state.")
                    }
                }
                .catch { e ->
                    Log.e("ChatViewModel", "Error during Gemini stream collection for conv $conversationId", e)
                    throw e
                }
                .collect()
        } catch (e: Exception) {
            Log.e("ChatViewModel", "Error setting up or starting Gemini API call for conv $conversationId", e)
            withContext(Dispatchers.Main) {
                _errorMessage.value = "Erro ao iniciar comunicação com IA: ${e.localizedMessage}"
                _loadingState.value = LoadingState.ERROR
            }
        }
    }

    private fun saveMessageToDb(message: ChatMessage, conversationId: Long, timestamp: Long = System.currentTimeMillis()) {
        if (conversationId == NEW_CONVERSATION_ID) {
            Log.e("ChatViewModel", "Attempted to save message with invalid NEW_CONVERSATION_ID. Message: '${message.text.take(30)}...'")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val entity = mapUiMessageToEntity(message, conversationId, timestamp)
            try {
                chatDao.insertMessage(entity)
                Log.d("ChatViewModel", "Msg saved (Conv $conversationId, Sender ${entity.sender}): ${entity.text.take(50)}...")
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error inserting message into DB for conv $conversationId", e)
            }
        }
    }

    private fun mapEntitiesToUiMessages(entities: List<ChatMessageEntity>): List<ChatMessage> {
        return entities.mapNotNull { entity ->
            try {
                val sender = enumValueOf<Sender>(entity.sender.uppercase())
                ChatMessage(entity.text, sender)
            } catch (e: IllegalArgumentException) {
                Log.e("ChatViewModelMapper", "Invalid sender string in DB: ${entity.sender}. Skipping message ID ${entity.id}.")
                null
            }
        }
    }

    private fun mapUiMessageToEntity(message: ChatMessage, conversationId: Long, timestamp: Long): ChatMessageEntity {
        return ChatMessageEntity(
            conversationId = conversationId,
            text = message.text,
            sender = message.sender.name,
            timestamp = timestamp,
            userId = _userIdFlow.value
        )
    }

    private fun mapMessagesToApiHistory(messages: List<ChatMessage>): List<Content> {
        return messages.takeLast(MAX_HISTORY_MESSAGES)
            .map { msg ->
                val role = if (msg.sender == Sender.USER) "user" else "model"
                return@map content(role = role) { text(msg.text) }
            }
    }

    private fun generateFallbackTitleSync(conversationId: Long): String {
        return try {
            runCatching {
                kotlinx.coroutines.runBlocking {
                    generateFallbackTitle(conversationId)
                }
            }.getOrElse { ex ->
                Log.e("ChatViewModel", "Error generating fallback title synchronously for conv $conversationId", ex)
                "Conversa $conversationId"
            }
        } catch (e: Exception) {
            "Conversa $conversationId"
        }
    }

    private suspend fun generateFallbackTitle(conversationId: Long): String = withContext(Dispatchers.IO) {
        try {
            val firstUserMessageText = chatDao.getFirstUserMessageText(conversationId, _userIdFlow.value)
            if (!firstUserMessageText.isNullOrBlank()) {
                Log.d("ChatViewModel", "Generating fallback title for $conversationId using first message.")
                return@withContext firstUserMessageText.take(30) + if (firstUserMessageText.length > 30) "..." else ""
            } else {
                try {
                    Log.d("ChatViewModel", "Generating fallback title for $conversationId using date.")
                    return@withContext "Conversa ${titleDateFormatter.format(Date(conversationId))}"
                } catch (formatException: Exception) {
                    Log.w("ChatViewModel", "Could not format conversationId $conversationId as Date for fallback title.", formatException)
                    return@withContext "Conversa $conversationId"
                }
            }
        } catch (dbException: Exception) {
            Log.e("ChatViewModel", "Error generating fallback title for conv $conversationId", dbException)
            return@withContext "Conversa $conversationId"
        }
    }

    suspend fun getDisplayTitle(conversationId: Long): String {
        return withContext(Dispatchers.IO) {
            if (conversationId == NEW_CONVERSATION_ID) {
                "Nova Conversa"
            } else {
                try {
                    val customTitle = metadataDao.getCustomTitle(conversationId)
                    if (!customTitle.isNullOrBlank()) {
                        Log.d("ChatViewModel", "Using custom title for $conversationId: '$customTitle'")
                        customTitle
                    } else {
                        generateFallbackTitle(conversationId)
                    }
                } catch (dbException: Exception) {
                    Log.e("ChatViewModel", "Error fetching title data for conv $conversationId", dbException)
                    "Conversa $conversationId"
                }
            }
        }
    }

    companion object {
        private val titleDateFormatter = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
    }
}