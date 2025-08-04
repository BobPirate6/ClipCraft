package com.example.clipcraft.presentation.subscription

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.clipcraft.data.repository.SubscriptionRepository
import com.example.clipcraft.domain.model.CreditPackage
import com.example.clipcraft.domain.model.Subscription
import com.example.clipcraft.domain.model.UserSubscription
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SubscriptionUiState(
    val subscriptionPlans: List<Subscription> = emptyList(),
    val creditPackages: List<CreditPackage> = emptyList(),
    val userSubscription: UserSubscription? = null,
    val isLoading: Boolean = false,
    val promoCodeInput: String = "",
    val isPromoCodeLoading: Boolean = false,
    val promoCodeMessage: String? = null,
    val promoCodeSuccess: Boolean = false,
    val creditsAdded: Int = 0
)

@HiltViewModel
class SubscriptionViewModel @Inject constructor(
    private val subscriptionRepository: SubscriptionRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(SubscriptionUiState())
    val uiState: StateFlow<SubscriptionUiState> = _uiState.asStateFlow()
    
    init {
        loadSubscriptionData()
    }
    
    private fun loadSubscriptionData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            val plans = subscriptionRepository.getSubscriptionPlans()
            val packages = subscriptionRepository.getCreditPackages()
            val userSub = subscriptionRepository.getUserSubscription()
            
            _uiState.value = _uiState.value.copy(
                subscriptionPlans = plans,
                creditPackages = packages,
                userSubscription = userSub,
                isLoading = false
            )
        }
    }
    
    fun updatePromoCode(code: String) {
        _uiState.value = _uiState.value.copy(
            promoCodeInput = code,
            promoCodeMessage = null
        )
    }
    
    fun redeemPromoCode() {
        if (_uiState.value.promoCodeInput.isBlank()) {
            _uiState.value = _uiState.value.copy(
                promoCodeMessage = "Please enter a promo code",
                promoCodeSuccess = false
            )
            return
        }
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isPromoCodeLoading = true)
            
            val promoCode = subscriptionRepository.validatePromoCode(_uiState.value.promoCodeInput)
            
            if (promoCode != null) {
                val success = subscriptionRepository.redeemPromoCode(_uiState.value.promoCodeInput)
                
                if (success) {
                    _uiState.value = _uiState.value.copy(
                        promoCodeMessage = "Promo code applied! +${promoCode.credits} credits added",
                        promoCodeSuccess = true,
                        creditsAdded = promoCode.credits,
                        promoCodeInput = "",
                        isPromoCodeLoading = false
                    )
                    // Reload user subscription to reflect new credits
                    loadSubscriptionData()
                } else {
                    _uiState.value = _uiState.value.copy(
                        promoCodeMessage = "Failed to apply promo code",
                        promoCodeSuccess = false,
                        isPromoCodeLoading = false
                    )
                }
            } else {
                _uiState.value = _uiState.value.copy(
                    promoCodeMessage = "Invalid or expired promo code",
                    promoCodeSuccess = false,
                    isPromoCodeLoading = false
                )
            }
        }
    }
    
    fun purchaseSubscription(subscriptionId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            // TODO: Integrate with Google Play Billing
            // For now, just update in Firebase
            val success = subscriptionRepository.purchaseSubscription(subscriptionId)
            
            if (success) {
                loadSubscriptionData()
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    promoCodeMessage = "Failed to purchase subscription"
                )
            }
        }
    }
    
    fun purchaseCredits(packageId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            // TODO: Integrate with Google Play Billing
            val creditPackage = _uiState.value.creditPackages.find { it.id == packageId }
            
            if (creditPackage != null) {
                val success = subscriptionRepository.addCredits(creditPackage.credits)
                
                if (success) {
                    _uiState.value = _uiState.value.copy(
                        promoCodeMessage = "Successfully added ${creditPackage.credits} credits!",
                        promoCodeSuccess = true,
                        creditsAdded = creditPackage.credits
                    )
                    loadSubscriptionData()
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        promoCodeMessage = "Failed to purchase credits"
                    )
                }
            }
        }
    }
    
    fun clearMessage() {
        _uiState.value = _uiState.value.copy(
            promoCodeMessage = null,
            promoCodeSuccess = false
        )
    }
}