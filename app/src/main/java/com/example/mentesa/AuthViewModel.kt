package com.example.mentesa

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    private val _currentUser = MutableStateFlow<FirebaseUser?>(auth.currentUser)
    val currentUser: StateFlow<FirebaseUser?> = _currentUser

    private val _authState = MutableStateFlow<AuthState>(AuthState.Initial)
    val authState: StateFlow<AuthState> = _authState

    // Evento para comunicar que o logout ocorreu (para limpar a UI)
    private val _logoutEvent = MutableStateFlow(false)
    val logoutEvent: StateFlow<Boolean> = _logoutEvent

    init {
        auth.addAuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            _currentUser.value = user
            Log.d("AuthViewModel", "AuthStateListener: usuário ${if (user != null) "logado" else "deslogado"}")
        }
    }

    fun registerWithEmail(email: String, password: String) {
        viewModelScope.launch {
            try {
                _authState.value = AuthState.Loading
                Log.d("AuthViewModel", "Iniciando registro com email: $email")
                auth.createUserWithEmailAndPassword(email, password).await()
                Log.d("AuthViewModel", "Registro com email concluído com sucesso")
                _authState.value = AuthState.Success
            } catch (e: Exception) {
                Log.e("AuthViewModel", "Erro no registro: ${e.message}")
                _authState.value = AuthState.Error(e.message ?: "Erro desconhecido no registro")
            }
        }
    }

    fun loginWithEmail(email: String, password: String) {
        viewModelScope.launch {
            try {
                _authState.value = AuthState.Loading
                Log.d("AuthViewModel", "Iniciando login com email: $email")
                auth.signInWithEmailAndPassword(email, password).await()
                Log.d("AuthViewModel", "Login com email concluído com sucesso")
                _authState.value = AuthState.Success
            } catch (e: Exception) {
                Log.e("AuthViewModel", "Erro no login: ${e.message}")
                _authState.value = AuthState.Error(e.message ?: "Erro desconhecido no login")
            }
        }
    }

    /**
     * Tenta fazer login com o email e senha fornecidos.
     * Se o login falhar porque o usuário não existe, automaticamente cria uma nova conta.
     */

    // Função auxiliar para validar email
    private fun isValidEmail(email: String): Boolean {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }

    fun signInWithGoogle(idToken: String) {

        viewModelScope.launch {
            try {
                _authState.value = AuthState.Loading
                Log.d("AuthViewModel", "Iniciando login com Google. Token recebido.")
                val credential = GoogleAuthProvider.getCredential(idToken, null)
                Log.d("AuthViewModel", "Credential criada, tentando signInWithCredential")
                auth.signInWithCredential(credential).await()
                Log.d("AuthViewModel", "Login com Google concluído com sucesso")
                _authState.value = AuthState.Success
            } catch (e: Exception) {
                Log.e("AuthViewModel", "Erro no login com Google via Firebase: ${e.message}", e)
                _authState.value = AuthState.Error(e.message ?: "Erro ao autenticar com Google")
            }
        }
    }

    fun resetPassword(email: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                Log.d("AuthViewModel", "Enviando email de recuperação para $email")
                auth.sendPasswordResetEmail(email).await()
                Log.d("AuthViewModel", "Email de recuperação enviado para $email")
                _authState.value = AuthState.Success
            } catch (e: Exception) {
                Log.e("AuthViewModel", "Erro na recuperação de senha: ${e.message}")
                _authState.value = AuthState.Error(e.message ?: "Erro ao enviar email de recuperação")
            }
        }
    }

    fun logout() {
        // Emitir evento de logout para limpar a UI
        _logoutEvent.value = true

        // Fazer logout
        auth.signOut()
        _authState.value = AuthState.Initial
        Log.d("AuthViewModel", "Usuário deslogado.")

        // Resetar o evento depois de um momento (para permitir que observers reajam)
        viewModelScope.launch {
            kotlinx.coroutines.delay(300) // Aumentar o delay para garantir que todos os componentes tenham tempo de reagir
            _logoutEvent.value = false
        }
    }

    fun isLoggedIn(): Boolean {
        return auth.currentUser != null
    }
}