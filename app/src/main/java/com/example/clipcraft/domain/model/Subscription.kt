package com.example.clipcraft.domain.model

data class Subscription(
    val id: String,
    val name: String,
    val price: Double,
    val priceString: String,
    val credits: Int,
    val features: List<String>,
    val isPopular: Boolean = false,
    val colorResId: Int? = null
)

data class CreditPackage(
    val id: String,
    val credits: Int,
    val price: Double,
    val priceString: String,
    val colorResId: Int? = null
)

data class PromoCode(
    val code: String,
    val credits: Int,
    val isUsed: Boolean = false,
    val expirationDate: Long? = null
)

data class UserSubscription(
    val userId: String,
    val subscriptionType: String = "FREE",
    val creditsRemaining: Int = 3,
    val subscriptionEndDate: Long? = null,
    val autoRenew: Boolean = true
)