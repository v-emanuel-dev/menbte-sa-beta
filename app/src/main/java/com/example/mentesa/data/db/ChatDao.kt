package com.example.mentesa.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

// Import da Entidade
import com.example.mentesa.data.db.ChatMessageEntity

/**
 * Data class para informações da conversa.
 */
data class ConversationInfo(
    @ColumnInfo(name = "conversation_id") val id: Long,
    @ColumnInfo(name = "last_timestamp") val lastTimestamp: Long
)

/**
 * DAO atualizado para suportar múltiplas conversas e busca de título.
 */
@Dao
interface ChatDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity)

    @Query("SELECT * FROM chat_messages WHERE conversation_id = :conversationId ORDER BY timestamp ASC")
    fun getMessagesForConversation(conversationId: Long): Flow<List<ChatMessageEntity>>

    @Query("""
        SELECT conversation_id, MAX(timestamp) as last_timestamp
        FROM chat_messages
        GROUP BY conversation_id
        ORDER BY last_timestamp DESC
    """)
    fun getConversations(): Flow<List<ConversationInfo>>

    /**
     * Busca o texto da primeira mensagem enviada pelo USUÁRIO em uma conversa específica.
     * Retorna null se não houver mensagens do usuário na conversa.
     */
    @Query("""
        SELECT message_text FROM chat_messages
        WHERE conversation_id = :conversationId AND sender_type = 'USER'
        ORDER BY timestamp ASC
        LIMIT 1
    """)
    suspend fun getFirstUserMessageText(conversationId: Long): String? // <-- NOVO MÉTODO

    @Query("DELETE FROM chat_messages WHERE conversation_id = :conversationId")
    suspend fun clearConversation(conversationId: Long)

    @Query("DELETE FROM chat_messages")
    suspend fun clearAllMessages()
}