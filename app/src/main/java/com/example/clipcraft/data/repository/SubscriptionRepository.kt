package com.example.clipcraft.data.repository

import com.example.clipcraft.domain.model.CreditPackage
import com.example.clipcraft.domain.model.PromoCode
import com.example.clipcraft.domain.model.Subscription
import com.example.clipcraft.domain.model.UserSubscription
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SubscriptionRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {
    
    fun getSubscriptionPlans(): List<Subscription> {
        // TODO: Load from remote config or CRM in the future
        return listOf(
            Subscription(
                id = "free",
                name = "Free",
                price = 0.0,
                priceString = "0$",
                credits = 3,
                features = listOf("AI editing"),
                colorResId = android.R.color.darker_gray
            ),
            Subscription(
                id = "starter",
                name = "Starter",
                price = 5.0,
                priceString = "5$",
                credits = 30,
                features = listOf("AI editing"),
                colorResId = android.R.color.holo_green_dark
            ),
            Subscription(
                id = "creator",
                name = "Creator",
                price = 15.0,
                priceString = "15$",
                credits = 100,
                features = listOf("AI editing", "Analysis & Suggestions"),
                isPopular = true,
                colorResId = android.R.color.holo_blue_dark
            ),
            Subscription(
                id = "studio",
                name = "Studio",
                price = 60.0,
                priceString = "60$",
                credits = 500,
                features = listOf("AI editing", "Analysis & Suggestions", "Batch processing"),
                colorResId = android.R.color.holo_orange_dark
            )
        )
    }
    
    fun getCreditPackages(): List<CreditPackage> {
        // TODO: Load from remote config or CRM in the future
        return listOf(
            CreditPackage(
                id = "credits_5",
                credits = 5,
                price = 0.0,
                priceString = "0$",
                colorResId = android.R.color.darker_gray
            ),
            CreditPackage(
                id = "credits_50",
                credits = 50,
                price = 0.0,
                priceString = "0$",
                colorResId = android.R.color.holo_green_dark
            ),
            CreditPackage(
                id = "credits_500",
                credits = 500,
                price = 0.0,
                priceString = "0$",
                colorResId = android.R.color.holo_blue_dark
            ),
            CreditPackage(
                id = "credits_1000",
                credits = 1000,
                price = 0.0,
                priceString = "0$",
                colorResId = android.R.color.holo_orange_dark
            )
        )
    }
    
    suspend fun getUserSubscription(): UserSubscription? {
        val userId = auth.currentUser?.uid ?: return null
        
        return try {
            val doc = firestore.collection("users")
                .document(userId)
                .get()
                .await()
            
            UserSubscription(
                userId = userId,
                subscriptionType = doc.getString("subscriptionType") ?: "FREE",
                creditsRemaining = doc.getLong("creditsRemaining")?.toInt() ?: 3,
                subscriptionEndDate = doc.getLong("subscriptionEndDate"),
                autoRenew = doc.getBoolean("autoRenew") ?: true
            )
        } catch (e: Exception) {
            null
        }
    }
    
    suspend fun updateUserCredits(credits: Int): Boolean {
        val userId = auth.currentUser?.uid ?: return false
        
        return try {
            firestore.collection("users")
                .document(userId)
                .update("creditsRemaining", credits)
                .await()
            true
        } catch (e: Exception) {
            false
        }
    }
    
    suspend fun addCredits(additionalCredits: Int): Boolean {
        val userId = auth.currentUser?.uid ?: return false
        
        return try {
            val currentCredits = getUserSubscription()?.creditsRemaining ?: 0
            updateUserCredits(currentCredits + additionalCredits)
        } catch (e: Exception) {
            false
        }
    }
    
    suspend fun validatePromoCode(code: String): PromoCode? {
        return try {
            val doc = firestore.collection("promo_codes")
                .document(code)
                .get()
                .await()
            
            if (doc.exists() && doc.getBoolean("isUsed") != true) {
                val expirationDate = doc.getLong("expirationDate")
                if (expirationDate == null || expirationDate > System.currentTimeMillis()) {
                    PromoCode(
                        code = code,
                        credits = doc.getLong("credits")?.toInt() ?: 0,
                        isUsed = false,
                        expirationDate = expirationDate
                    )
                } else {
                    null
                }
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    suspend fun redeemPromoCode(code: String): Boolean {
        val userId = auth.currentUser?.uid ?: return false
        val promoCode = validatePromoCode(code) ?: return false
        
        return try {
            // Mark promo code as used
            firestore.collection("promo_codes")
                .document(code)
                .update(
                    mapOf(
                        "isUsed" to true,
                        "usedBy" to userId,
                        "usedAt" to System.currentTimeMillis()
                    )
                )
                .await()
            
            // Add credits to user
            addCredits(promoCode.credits)
        } catch (e: Exception) {
            false
        }
    }
    
    suspend fun purchaseSubscription(subscriptionId: String): Boolean {
        val userId = auth.currentUser?.uid ?: return false
        val subscription = getSubscriptionPlans().find { it.id == subscriptionId } ?: return false
        
        return try {
            val endDate = System.currentTimeMillis() + (30L * 24 * 60 * 60 * 1000) // 30 days
            
            firestore.collection("users")
                .document(userId)
                .update(
                    mapOf(
                        "subscriptionType" to subscription.name.uppercase(),
                        "subscriptionEndDate" to endDate,
                        "autoRenew" to true
                    )
                )
                .await()
            
            // Add monthly credits
            addCredits(subscription.credits)
        } catch (e: Exception) {
            false
        }
    }
    
    fun observeUserCredits(): Flow<Int> = callbackFlow {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            close()
            return@callbackFlow
        }
        
        val listener = firestore.collection("users")
            .document(userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                
                snapshot?.getLong("creditsRemaining")?.toInt()?.let { credits ->
                    trySend(credits)
                }
            }
        
        awaitClose {
            listener.remove()
        }
    }
}