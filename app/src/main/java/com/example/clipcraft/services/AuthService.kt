// app/src/main/java/com/example/clipcraft/services/AuthService.kt
package com.example.clipcraft.services

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.example.clipcraft.models.User
import com.example.clipcraft.models.SubscriptionType
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthService @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) {

    fun getCurrentUserFlow(): Flow<User?> {
        return auth.authStateFlow().map { firebaseUser ->
            if (firebaseUser != null) {
                getUserData(firebaseUser.uid)
            } else null
        }
    }

    suspend fun signInWithGoogle(idToken: String): Result<User> {
        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val result = auth.signInWithCredential(credential).await()

            val firebaseUser = result.user ?: return Result.failure(Exception("User is null"))

            val user = User(
                uid = firebaseUser.uid,
                email = firebaseUser.email ?: "",
                displayName = firebaseUser.displayName ?: "",
                subscriptionType = SubscriptionType.FREE,
                creditsRemaining = 3
            )

            saveUserData(user)
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signInWithEmailPassword(email: String, password: String): Result<User> {
        return try {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            val firebaseUser = result.user ?: return Result.failure(Exception("User is null"))

            val userData = getUserData(firebaseUser.uid)
            if (userData != null) {
                Result.success(userData)
            } else {
                Result.failure(Exception("User data not found"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createUserWithEmailPassword(email: String, password: String): Result<User> {
        return try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            val firebaseUser = result.user ?: return Result.failure(Exception("User is null"))

            val user = User(
                uid = firebaseUser.uid,
                email = email,
                subscriptionType = SubscriptionType.FREE,
                creditsRemaining = 3
            )

            saveUserData(user)
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun signOut() {
        auth.signOut()
    }

    private suspend fun getUserData(uid: String): User? {
        return try {
            val doc = firestore.collection("users").document(uid).get().await()
            doc.toObject(User::class.java)
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun saveUserData(user: User) {
        firestore.collection("users")
            .document(user.uid)
            .set(user)
            .await()
    }

    suspend fun updateUserCredits(userId: String, credits: Int) {
        firestore.collection("users")
            .document(userId)
            .update("creditsRemaining", credits)
            .await()
    }
}

// Extension для Firebase Auth Flow
fun FirebaseAuth.authStateFlow(): Flow<com.google.firebase.auth.FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}