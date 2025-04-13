package com.example.mentesa.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

// Atualizando a versão de 3 para 4
@Database(
    entities = [ChatMessageEntity::class, ConversationMetadataEntity::class],
    version = 4,  // Versão incrementada de 3 para 4
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun conversationMetadataDao(): ConversationMetadataDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "mentesa_database"
                )
                    .fallbackToDestructiveMigration()  // Permite que o Room recrie o banco quando a versão muda
                    // Você pode substituir isso por uma estratégia de migração adequada em produção
                    // .addMigrations(MIGRATION_3_4)
                    .build()
                INSTANCE = instance
                instance
            }
        }

        // Caso prefira uma migração específica em vez de destruir e recriar o banco:
        /*
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Aqui você escreve o SQL para atualizar o esquema
                // Por exemplo:
                // database.execSQL("ALTER TABLE conversation_metadata ADD COLUMN user_id TEXT NOT NULL DEFAULT 'local_user'")
                // database.execSQL("CREATE INDEX IF NOT EXISTS index_conversation_metadata_user_id ON conversation_metadata(user_id)")
            }
        }
        */
    }
}                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   