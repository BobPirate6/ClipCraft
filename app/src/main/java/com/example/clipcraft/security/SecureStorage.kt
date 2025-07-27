package com.example.clipcraft.security

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Безопасное хранилище для чувствительных данных
 */
@Singleton
class SecureStorage @Inject constructor(
    private val context: Context
) {
    companion object {
        private const val PREFS_NAME = "secure_prefs"
        private const val KEY_ALIAS = "ClipCraftSecureKey"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_SEPARATOR = ":"
    }
    
    private val sharedPreferences: SharedPreferences by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            createEncryptedSharedPreferences()
        } else {
            // Для старых версий Android используем обычные SharedPreferences
            // В продакшене лучше использовать свою реализацию шифрования
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }
    
    private fun createEncryptedSharedPreferences(): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
            
        return EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
    
    /**
     * Сохраняет строку в безопасном хранилище
     */
    fun saveString(key: String, value: String?) {
        if (value == null) {
            sharedPreferences.edit().remove(key).apply()
        } else {
            sharedPreferences.edit().putString(key, value).apply()
        }
    }
    
    /**
     * Получает строку из безопасного хранилища
     */
    fun getString(key: String, defaultValue: String? = null): String? {
        return sharedPreferences.getString(key, defaultValue)
    }
    
    /**
     * Сохраняет токен
     */
    fun saveAuthToken(token: String?) {
        saveString("auth_token", token)
    }
    
    /**
     * Получает токен
     */
    fun getAuthToken(): String? {
        return getString("auth_token")
    }
    
    /**
     * Очищает все данные
     */
    fun clear() {
        sharedPreferences.edit().clear().apply()
    }
    
    /**
     * Проверяет, есть ли сохраненный токен
     */
    fun hasAuthToken(): Boolean {
        return getAuthToken() != null
    }
    
    /**
     * Шифрует строку используя Android Keystore (для критически важных данных)
     */
    fun encryptSensitiveData(data: String): String? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val cipher = getCipher()
                cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
                val iv = cipher.iv
                val encryptedData = cipher.doFinal(data.toByteArray(Charsets.UTF_8))
                
                // Сохраняем IV вместе с зашифрованными данными
                val ivString = Base64.encodeToString(iv, Base64.NO_WRAP)
                val encryptedString = Base64.encodeToString(encryptedData, Base64.NO_WRAP)
                "$ivString$IV_SEPARATOR$encryptedString"
            } else {
                // Для старых версий возвращаем как есть (в продакшене нужна альтернатива)
                data
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * Расшифровывает строку
     */
    fun decryptSensitiveData(encryptedData: String): String? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val parts = encryptedData.split(IV_SEPARATOR)
                if (parts.size != 2) return null
                
                val iv = Base64.decode(parts[0], Base64.NO_WRAP)
                val encrypted = Base64.decode(parts[1], Base64.NO_WRAP)
                
                val cipher = getCipher()
                val spec = GCMParameterSpec(128, iv)
                cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), spec)
                
                val decryptedData = cipher.doFinal(encrypted)
                String(decryptedData, Charsets.UTF_8)
            } else {
                // Для старых версий возвращаем как есть
                encryptedData
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    private fun getCipher(): Cipher {
        return Cipher.getInstance(TRANSFORMATION)
    }
    
    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)
        
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE
            )
            
            val keyGenParameterSpec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build()
            } else {
                throw UnsupportedOperationException("API level 23+ required")
            }
            
            keyGenerator.init(keyGenParameterSpec)
            keyGenerator.generateKey()
        }
        
        return keyStore.getKey(KEY_ALIAS, null) as SecretKey
    }
}