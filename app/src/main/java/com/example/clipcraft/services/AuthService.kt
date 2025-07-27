package com.example.clipcraft.services

import android.util.Log
import com.example.clipcraft.models.SubscriptionType
import com.example.clipcraft.models.User
import com.example.clipcraft.utils.authStateFlow
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthService @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) {
    companion object {
        private const val TAG = "AuthService"
    }

    fun getCurrentUserFlow(): Flow<User?> {
        return auth.authStateFlow().map { firebaseUser ->
            if (firebaseUser != null) {
                getUserData(firebaseUser.uid)
            } else null
        }
    }

    suspend fun signInWithGoogle(idToken: String): Result<User> {
        return try {
            Log.d(TAG, "Starting Google sign in with token")
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            
            Log.d(TAG, "Signing in with credential")
            val authResult = auth.signInWithCredential(credential).await()
            val firebaseUser = authResult.user!!
            
            Log.d(TAG, "Successfully authenticated. UID: ${firebaseUser.uid}, Email: ${firebaseUser.email}")

            var user = getUserData(firebaseUser.uid)
            if (user == null) {
                Log.d(TAG, "New user, creating profile")
                user = User(
                    uid = firebaseUser.uid,
                    email = firebaseUser.email ?: "",
                    displayName = firebaseUser.displayName,
                    photoUrl = firebaseUser.photoUrl?.toString(),
                    subscriptionType = SubscriptionType.FREE,
                    creditsRemaining = 10,
                    isFirstTime = true
                )
                saveUserData(user)
                Log.d(TAG, "User profile created successfully")
            } else {
                Log.d(TAG, "Existing user found")
            }
            Result.success(user)
        } catch (e: Exception) {
            Log.e(TAG, "Google sign in failed", e)
            Result.failure(e)
        }
    }

    suspend fun signInWithEmailPassword(email: String, password: String): Result<User> {
        return try {
            Log.d(TAG, "Attempting to sign in with email: $email")
            val result = auth.signInWithEmailAndPassword(email, password).await()
            val firebaseUser = result.user!!
            Log.d(TAG, "Firebase auth successful, UID: ${firebaseUser.uid}")
            
            val user = getUserData(firebaseUser.uid)
            if (user == null) {
                Log.w(TAG, "User authenticated but no Firestore data found, creating profile")
                val newUser = User(
                    uid = firebaseUser.uid,
                    email = email,
                    subscriptionType = SubscriptionType.FREE,
                    creditsRemaining = 10,
                    isFirstTime = true
                )
                saveUserData(newUser)
                Result.success(newUser)
            } else {
                Log.d(TAG, "User data retrieved successfully")
                Result.success(user)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Sign in with email failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun createUserWithEmailPassword(email: String, password: String): Result<User> {
        return try {
            Log.d(TAG, "Creating new account for email: $email")
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            val firebaseUser = result.user!!
            Log.d(TAG, "Account created successfully, UID: ${firebaseUser.uid}")
            
            val user = User(
                uid = firebaseUser.uid,
                email = email,
                subscriptionType = SubscriptionType.FREE,
                creditsRemaining = 10,
                isFirstTime = true
            )
            saveUserData(user)
            Log.d(TAG, "User profile saved to Firestore")
            Result.success(user)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create account: ${e.message}", e)
            Result.failure(e)
        }
    }

    fun signOut() {
        auth.signOut()
    }

    private suspend fun saveUserData(user: User) {
        firestore.collection("users").document(user.uid).set(user).await()
    }

    private suspend fun getUserData(uid: String): User? {
        return try {
            firestore.collection("users").document(uid).get().await().toObject(User::class.java)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun updateUserCredits(userId: String, credits: Int) {
        firestore.collection("users").document(userId).update("creditsRemaining", credits).await()
    }

    suspend fun updateUserDisplayName(uid: String, newName: String): Result<Unit> {
        return try {
            val profileUpdates = UserProfileChangeRequest.Builder()
                .setDisplayName(newName)
                .build()
            auth.currentUser?.updateProfile(profileUpdates)?.await()

            firestore.collection("users").document(uid).update("displayName", newName).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Удаляет аккаунт пользователя из Firestore и Firebase Auth.
     */
    suspend fun deleteAccount(): Result<Unit> {
        return try {
            val user = auth.currentUser ?: return Result.failure(Exception("User not found"))
            val uid = user.uid
            // Сначала удаляем данные из Firestore
            firestore.collection("users").document(uid).delete().await()
            // Затем удаляем пользователя из Auth
            user.delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Отправляет письмо для восстановления пароля
     */
    suspend fun sendPasswordResetEmail(email: String): Result<Unit> {
        return try {
            Log.d(TAG, "Sending password reset email to: $email")
            auth.sendPasswordResetEmail(email).await()
            Log.d(TAG, "Password reset email sent successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send password reset email", e)
            Result.failure(e)
        }
    }
    
    /**
     * Обновляет флаг первого входа пользователя
     */
    suspend fun updateFirstTimeFlag(uid: String, isFirstTime: Boolean): Result<Unit> {
        return try {
            Log.d(TAG, "Updating first time flag for user: $uid to $isFirstTime")
            firestore.collection("users").document(uid).update("isFirstTime", isFirstTime).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update first time flag", e)
            Result.failure(e)
        }
    }
}
