package com.example.clipcraft.security

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Base64
import android.util.Log
import java.security.MessageDigest

/**
 * Класс для проверки безопасности приложения
 */
object AppSecurity {
    private const val TAG = "AppSecurity"
    
    // SHA-256 хэши разрешенных сертификатов (нужно будет обновить после подписи)
    private val ALLOWED_CERTIFICATE_HASHES = setOf(
        // Debug сертификат (для разработки)
        "YOUR_DEBUG_CERTIFICATE_SHA256_HERE",
        // Release сертификат (для продакшена)
        "YOUR_RELEASE_CERTIFICATE_SHA256_HERE",
        // Alpha сертификат
        "YOUR_ALPHA_CERTIFICATE_SHA256_HERE"
    )
    
    /**
     * Проверяет подпись приложения
     */
    fun verifyAppSignature(context: Context): Boolean {
        try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNATURES
                )
            }
            
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                packageInfo.signatures
            }
            
            signatures?.forEach { signature ->
                val signatureHash = getSHA256(signature.toByteArray())
                Log.d(TAG, "Current app signature SHA256: $signatureHash")
                
                if (ALLOWED_CERTIFICATE_HASHES.contains(signatureHash)) {
                    return true
                }
            }
            
            Log.e(TAG, "App signature verification failed!")
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Error verifying app signature", e)
            return false
        }
    }
    
    /**
     * Проверяет, что приложение не запущено в эмуляторе
     */
    fun isRunningOnEmulator(): Boolean {
        return (Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
                || "google_sdk" == Build.PRODUCT)
    }
    
    /**
     * Проверяет, установлено ли приложение из Play Store
     */
    fun isInstalledFromPlayStore(context: Context): Boolean {
        val installer = context.packageManager.getInstallerPackageName(context.packageName)
        return installer != null && installer.startsWith("com.android.vending")
    }
    
    /**
     * Проверяет целостность приложения
     */
    fun verifyAppIntegrity(context: Context): Boolean {
        // Для альфа-тестирования можем пропустить эту проверку
        if (context.packageName.endsWith(".alpha")) {
            return true
        }
        
        // Проверяем подпись
        if (!verifyAppSignature(context)) {
            return false
        }
        
        // Для релизной версии можем добавить дополнительные проверки
        return true
    }
    
    private fun getSHA256(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(data)
        return Base64.encodeToString(hash, Base64.NO_WRAP)
    }
    
    /**
     * Печатает текущий SHA256 хэш сертификата (для отладки)
     */
    fun printCurrentCertificateHash(context: Context) {
        try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNATURES
                )
            }
            
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                packageInfo.signatures
            }
            
            signatures?.forEach { signature ->
                val signatureHash = getSHA256(signature.toByteArray())
                Log.i(TAG, "===========================================")
                Log.i(TAG, "CURRENT CERTIFICATE SHA256: $signatureHash")
                Log.i(TAG, "===========================================")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting certificate hash", e)
        }
    }
}