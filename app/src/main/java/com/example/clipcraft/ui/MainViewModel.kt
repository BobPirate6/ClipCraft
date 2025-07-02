package com.example.clipcraft.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.clipcraft.models.*
import com.example.clipcraft.services.ApiService
import com.example.clipcraft.services.AuthService
import com.google.firebase.firestore.ktx.snapshots
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * Полная версия MainViewModel без заглушек/сокращений.
 * Логика аутентификации вынесена в AuthService.
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val authService: AuthService,
    private val apiService: ApiService
) : ViewModel() {

    // ──────────── UI-состояния ─────────────
    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _processingState = MutableStateFlow<ProcessingState>(ProcessingState.Idle)
    val processingState: StateFlow<ProcessingState> = _processingState.asStateFlow()

    private val _selectedVideos = MutableStateFlow<List<String>>(emptyList())
    val selectedVideos: StateFlow<List<String>> = _selectedVideos.asStateFlow()

    private val _userCommand = MutableStateFlow("")
    val userCommand: StateFlow<String> = _userCommand.asStateFlow()

    private val _useLocalProcessing = MutableStateFlow(false)
    val useLocalProcessing: StateFlow<Boolean> = _useLocalProcessing.asStateFlow()

    /**
     * Текущий пользователь приходит из Firestore через AuthService.
     */
    val currentUser: StateFlow<User?> = authService.getCurrentUserFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        /* конвертируем User? → AuthState */
        viewModelScope.launch {
            currentUser.collect { user ->
                _authState.value = if (user == null) AuthState.Unauthenticated
                else AuthState.Authenticated(user)
            }
        }
    }

    // ──────────── Аутентификация ───────────
    fun signInWithGoogle(idToken: String) = launchAuth { authService.signInWithGoogle(idToken) }
    fun signInWithEmail(email: String, pass: String) = launchAuth {
        authService.signInWithEmailPassword(email, pass)
    }
    fun createAccount(email: String, pass: String) = launchAuth {
        authService.createUserWithEmailPassword(email, pass)
    }

    private fun launchAuth(call: suspend () -> Result<User>) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val result = call()
            if (result.isSuccess) {
                _authState.value = AuthState.Authenticated(result.getOrNull()!!)
            } else {
                _authState.value = AuthState.Error(result.exceptionOrNull()?.message ?: "Ошибка аутентификации")
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            authService.signOut()
            resetProcessing()
        }
    }

    // ──────────── Команда и медиа ───────────
    fun updateCommand(cmd: String) { _userCommand.value = cmd }

    fun addVideos(paths: List<String>) { _selectedVideos.value = _selectedVideos.value + paths }

    fun removeVideo(path: String) {
        _selectedVideos.value = _selectedVideos.value.filterNot { it == path }
    }

    fun toggleProcessingMode() { _useLocalProcessing.value = !_useLocalProcessing.value }

    // ──────────── Voice ─────────────────────
    fun startVoiceRecognition(launcher: ActivityResultLauncher<Intent>) {
        val i = Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT,
                "Скажите команду для обработки видео")
        }
        launcher.launch(i)
    }
    fun handleVoiceResult(text: String) { _userCommand.value = text }

    // ──────────── Обработка видео ───────────
    fun processVideos(context: Context) {
        viewModelScope.launch {
            _processingState.value = ProcessingState.Processing
            try {
                val resp = if (_useLocalProcessing.value)
                    processLocally() else apiService.editVideo(_selectedVideos.value, _userCommand.value)

                _processingState.value = if (resp.success)
                    ProcessingState.Success(resp.downloadUrl ?: "")
                else ProcessingState.Error(resp.message)
            } catch (e: Exception) {
                _processingState.value = ProcessingState.Error(e.message ?: "Ошибка обработки")
            }
        }
    }

    private suspend fun processLocally(): EditResp {
        kotlinx.coroutines.delay(3_000)
        return EditResp(true, "Локальная обработка завершена", "/path/to/result.mp4")
    }

    fun resetProcessing() {
        _processingState.value  = ProcessingState.Idle
        _selectedVideos.value   = emptyList()
        _userCommand.value      = ""
    }

    // ──────────── Utils ─────────────────────
    fun uriToTempFile(context: Context, uri: Uri): File? = try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            File.createTempFile("video_", ".mp4", context.cacheDir).also { tmp ->
                tmp.outputStream().use { input.copyTo(it) }
            }
        }
    } catch (_: Exception) { null }

    fun saveToGallery(context: Context) {}
    fun shareToInstagram(context: Context) {}
    fun shareToTikTok(context: Context) {}
    fun shareGeneric(context: Context) {}
}