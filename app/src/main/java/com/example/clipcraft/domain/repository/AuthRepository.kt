package com.example.clipcraft.domain.repository

import com.example.clipcraft.models.AuthState
import com.example.clipcraft.models.User
import com.example.clipcraft.services.AuthService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Репозиторий для управления аутентификацией и данными пользователя.
 * Инкапсулирует всю логику взаимодействия с AuthService.
 */
@Singleton
class AuthRepository @Inject constructor(
    private val authService: AuthService
) {

    /**
     * Предоставляет Flow для отслеживания состояния аутентификации.
     */
    fun getAuthState(): Flow<AuthState> {
        return authService.getCurrentUserFlow()
            .map { user ->
                if (user != null) AuthState.Authenticated(user) else AuthState.Unauthenticated
            }
            .catch { emit(AuthState.Error(it.message ?: "Ошибка аутентификации")) }
    }

    /**
     * Возвращает Flow с текущим пользователем.
     */
    fun getCurrentUser(): Flow<User?> {
        return authService.getCurrentUserFlow()
    }

    /**
     * Выполняет вход через Google.
     */
    suspend fun signInWithGoogle(idToken: String): Result<User> {
        return authService.signInWithGoogle(idToken)
    }

    /**
     * Выполняет вход по email и паролю.
     */
    suspend fun signInWithEmail(email: String, password: String): Result<User> {
        return authService.signInWithEmailPassword(email, password)
    }

    /**
     * Создает новый аккаунт.
     */
    suspend fun createAccount(email: String, password: String): Result<User> {
        return authService.createUserWithEmailPassword(email, password)
    }

    /**
     * Выполняет выход из системы.
     */
    fun signOut() {
        authService.signOut()
    }

    /**
     * Обновляет отображаемое имя пользователя.
     */
    suspend fun updateUserName(uid: String, newName: String): Result<Unit> {
        return authService.updateUserDisplayName(uid, newName)
    }

    /**
     * Обновляет количество кредитов пользователя.
     */
    suspend fun updateUserCredits(userId: String, credits: Int) {
        authService.updateUserCredits(userId, credits)
    }
    
    /**
     * Отправляет письмо для восстановления пароля.
     */
    suspend fun sendPasswordResetEmail(email: String): Result<Unit> {
        return authService.sendPasswordResetEmail(email)
    }
    
    /**
     * Обновляет флаг первого входа пользователя.
     */
    suspend fun updateFirstTimeFlag(uid: String, isFirstTime: Boolean): Result<Unit> {
        return authService.updateFirstTimeFlag(uid, isFirstTime)
    }
}
