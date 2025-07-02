package com.example.clipcraft.models

import kotlinx.serialization.Serializable

@Serializable
data class User(
    val uid: String = "",
    val email: String = "",
    val displayName: String = "",
    val subscriptionType: SubscriptionType = SubscriptionType.FREE,
    val creditsRemaining: Int = 3
)

@Serializable
enum class SubscriptionType {
    FREE, PREMIUM
}

sealed class AuthState {
    object Loading : AuthState()
    object Unauthenticated : AuthState()
    data class Authenticated(val user: User) : AuthState()
    data class Error(val message: String) : AuthState()
}

sealed class ProcessingState {
    object Idle : ProcessingState()
    object Processing : ProcessingState()
    data class Success(val result: String) : ProcessingState()
    data class Error(val message: String) : ProcessingState()
}

@Serializable
data class EditResp(
    val success: Boolean,
    val message: String,
    val downloadUrl: String? = null
)