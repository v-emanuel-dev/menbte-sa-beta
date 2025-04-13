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

    // Firebase e banco de dados
    private val auth = FirebaseAuth.getInstance()
    private val appDb = AppDatabase.getDatabase(application)
    private val chatDao: ChatDao = appDb.chatDao()
    private val metadataDao: ConversationMetadataDao = appDb.conversationMetadataDao()

    // Fluxos de estado internos
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

    // Novo: Fluxo para controle do evento de limpar a lista de conversas (por exemplo, após logout)
    private val _clearConversationListEvent = MutableStateFlow(false)
    val clearConversationListEvent: StateFlow<Boolean> = _clearConversationListEvent.asStateFlow()

    // Fluxo reativo do UID do usuário
    private val _userIdFlow = MutableStateFlow(getCurrentUserId())

    // Fluxo para controle de exibição das conversas
    private val _showConversations = MutableStateFlow(true)
    val showConversations: StateFlow<Boolean> = _showConversations.asStateFlow()

    init {
        auth.addAuthStateListener { firebaseAuth ->
            _userIdFlow.value = getCurrentUserId()
        }
        loadInitialConversationOrStartNew()
    }

    // Função para obter o ID do usuário atual (ou "local_user" se não logado)
    private fun getCurrentUserId(): String =
        FirebaseAuth.getInstance().currentUser?.uid ?: "local_user"

    // Fluxo de conversas filtrado pelo UID
    private val rawConversationsFlow: Flow<List<ConversationInfo>> =
        _userIdFlow.flatMapLatest { uid ->
            chatDao.getConversationsForUser(uid)
                .catch { e ->
                    Log.e("ChatViewModel", "Error loading raw conversations flow", e)
                    _errorMessage.value = "Erro ao carregar lista de conversas (raw)."
                    emit(emptyList())
                }
        }

    // Fluxo de metadata reativo
    private val metadataFlow: Flow<List<ConversationMetadataEntity>> =
        _userIdFlow.flatMapLatest { uid ->
            metadataDao.getMetadataForUser(uid)
                .catch { e ->
                    Log.e("ChatViewModel", "Error loading metadata flow", e)
                    emit(emptyList())
                }
        }

    // Combinação dos fluxos de conversas e metadata para exibição na UI
    val conversationListForDrawer: StateFlow<List<ConversationDisplayItem>> =
        combine(rawConversationsFlow, metadataFlow, _showConversations, _userIdFlow) { conversations, metadataList, showConversations, currentUserId ->
            if (!showConversations || auth.currentUser == null) {
                return@combine emptyList<ConversationDisplayItem>()
            }

            Log.d("ChatViewModel", "Combining ${conversations.size} convs and ${metadataList.size} metadata entries for user $currentUserId.")

            // Filtramos explicitamente metadados pelo ID do usuário (embora isso provavelmente já ocorra com getMetadataForUser)
            val userMetadata = metadataList.filter { it.userId == currentUserId }
            val metadataMap = userMetadata.associateBy({ it.conversationId }, { it.customTitle })

            // Apenas conversas que estão no banco e tem o userId correto
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

    // Fluxo de mensagens para a conversa atual
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
    1. **NÃO FAÇA DIAGNÓSTICOS:** Apenas um profissional pode diagnosticar.
    2. **NÃO SUBSTITUA TERAPIA:** Você é um apoio, não um substituto para acompanhamento terapêutico.
    3. **FOCO NO BEM-ESTAR:** Redirecione temas fora do escopo.
    4. **EVITE CONSELHOS DIRETOS:** Oriente sem dar ordens diretas.
    5. **SITUAÇÕES DE CRISE:** Direcione para recursos imediatos (ex.: CVV).

    ## Quem é você?
    Ao ser perguntado "Quem é você?" responda apenas com a mensagem de boas-vindas.

    ## Objetivo Final
    Ser um companheiro virtual que promove autoconhecimento e bem-estar, dentro dos limites éticos e de segurança.
    """.trimIndent()

    private val generativeModel = GenerativeModel(
        modelName = "gemini-2.0-flash",
        apiKey = BuildConfig.GEMINI_API_KEY,
        systemInstruction = content { text(menteSaSystemPrompt) },
        requestOptions = RequestOptions(timeout = 60000)
    )

    // --- Funções de Ação e Comunicação ---

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

        if (this.isProhibitedTopic(userMessageText)) {
            Log.w("ChatViewModel", "Prohibited topic detected: '${userMessageText.take(50)}...'")
            val botResponse = "Desculpe, sou especializado apenas em temas de saúde mental. Posso ajudar você com ansiedade, depressão, técnicas de autocuidado ou outros assuntos relacionados ao bem-estar emocional."
            saveMessageToDb(ChatMessage(botResponse, Sender.BOT), targetConversationId)
            _loadingState.value = LoadingState.IDLE
            return
        }

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

    private fun isProhibitedTopic(message: String): Boolean {
        val prohibitedTopics = mapOf(
            "Ciências Exatas" to listOf("física", "química", "matemática", "equação", "fórmula", "cálculo", "átomo", "molécula", "reação", "elemento", "tabela periódica", "teorema", "álgebra", "geometria", "trigonometria", "derivada", "integral", "newton", "einstein", "celsius", "kelvin", "tesla", "feynman"),
            "Astronomia" to listOf("astronomia", "planeta", "galáxia", "sistema solar", "estrela", "universo", "nasa", "spacex", "satélite", "marte", "júpiter", "lua", "eclipse", "cometa"),
            "História/Geografia" to listOf("história", "geografia", "guerra", "império", "revolução", "dinastia", "continente", "país", "capital", "mapa", "relevo", "clima", "população", "guerra mundial", "idade média", "renascimento", "colonização", "civilização"),
            "Política/Economia" to listOf("política", "economia", "presidente", "eleição", "partido", "congresso", "senador", "deputado", "ministro", "inflação", "juros", "bolsa", "mercado", "imposto", "orçamento", "déficit", "superávit", "banco central", "governo"),
            "Entretenimento" to listOf("esporte", "futebol", "basquete", "vôlei", "filme", "novela", "série", "time", "campeonato", "copa", "olimpíada", "ator", "atriz", "diretor", "netflix", "cinema", "teatro", "show", "música", "concerto", "festival"),
            "Tecnologia" to listOf("computador", "programação", "código", "app", "desenvolvimento", "software", "hardware", "internet", "rede", "algoritmo", "linguagem", "java", "python", "website", "servidor", "banco de dados", "cloud", "api", "framework"),
            "Linguística" to listOf("língua", "gramática", "sintaxe", "semântica", "verbo", "substantivo", "pronome", "preposição", "ortografia", "fonética", "tradução", "idioma")
        )
        val lowercaseMessage = message.lowercase()
        return prohibitedTopics.values.flatten().any { keyword ->
            val pattern = "\\b$keyword\\b|\\b$keyword-|\\b$keyword\\s"
            pattern.toRegex().containsMatchIn(lowercaseMessage)
        }
    }

    private fun isValidResponse(response: String): Boolean {
        val prohibitedPhrases = listOf(
            "na física", "em física", "a física", "física é", "física clássica", "física quântica", "leis da física", "conceito físico", "fenômeno físico", "teoria física",
            "em matemática", "na matemática", "fórmula", "equação", "cálculo de", "teorema", "matemática é", "matematicamente", "valor numérico", "resolva",
            "na história", "história do", "período histórico", "guerra mundial", "revolução", "império", "dinastia", "século", "era", "idade média",
            "presidente", "governador", "político", "eleição", "partido", "congresso", "senado", "câmara", "ministro", "governo",
            "astronomia", "planeta", "sistema solar", "galáxia", "estrela", "constelação", "universo", "nasa", "telescópio", "órbita",
            "computação", "programação", "código fonte", "algoritmo", "linguagem de programação", "software", "hardware", "aplicativo", "desenvolvimento web", "sistema operacional",
            "esporte", "time", "jogador", "campeonato", "liga", "filme", "série", "ator", "diretor", "cinema", "televisão", "streaming", "episódio"
        )
        val allowedContexts = listOf(
            "no contexto da saúde mental", "relacionado à saúde mental", "impacto na saúde mental", "afeta a saúde mental", "bem-estar emocional", "bem-estar psicológico", "técnica terapêutica", "abordagem terapêutica"
        )
        val lowercaseResponse = response.lowercase()
        return !prohibitedPhrases.any { phrase ->
            if (lowercaseResponse.contains(phrase)) {
                !allowedContexts.any { context ->
                    val startIndex = maxOf(0, lowercaseResponse.indexOf(phrase) - 50)
                    val endIndex = minOf(lowercaseResponse.length, lowercaseResponse.indexOf(phrase) + phrase.length + 50)
                    val surroundingText = lowercaseResponse.substring(startIndex, endIndex)
                    surroundingText.contains(context)
                }
            } else {
                false
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
                        if (!finalBotResponseText.isNullOrBlank() && !isValidResponse(finalBotResponseText!!)) {
                            Log.w("ChatViewModel", "Invalid response detected for conv $conversationId, replacing with fallback")
                            finalBotResponseText = "Desculpe, não posso fornecer essa informação pois está fora do escopo de saúde mental. Como posso ajudar com seu bem-estar emocional hoje?"
                        }
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

    // Método para uso síncrono dentro do combine (não ideal para produção)
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

    // Método para obter o título da conversa para exibição
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
