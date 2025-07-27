package com.example.clipcraft.security

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Base64
import java.security.MessageDigest

object SecurityConfig {
    // Проверка целостности приложения
    fun verifyAppSignature(context: Context): Boolean {
        val expectedSignatures = listOf(
            // Добавьте сюда SHA-256 хеш вашего релизного сертификата
            // Получить можно командой: keytool -list -v -keystore your-release-key.keystore
        )
        
        return try {
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
            
            signatures?.any { signature ->
                val signatureHash = MessageDigest.getInstance("SHA256")
                    .digest(signature.toByteArray())
                    .let { Base64.encodeToString(it, Base64.NO_WRAP) }
                
                expectedSignatures.contains(signatureHash)
            } ?: false
        } catch (e: Exception) {
            false
        }
    }
    
    // Проверка на root
    fun isDeviceRooted(): Boolean {
        val paths = arrayOf(
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su"
        )
        
        return paths.any { java.io.File(it).exists() }
    }
    
    // Проверка на эмулятор (опционально для альфа-тестирования)
    fun isEmulator(): Boolean {
        return (Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
                || "google_sdk" == Build.PRODUCT)
    }
}