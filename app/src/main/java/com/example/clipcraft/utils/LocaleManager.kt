package com.example.clipcraft.utils

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import java.util.Locale

// Extension property for DataStore
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "locale_preferences")

class LocaleManager(
    private val context: Context
) {
    companion object {
        private val LOCALE_KEY = stringPreferencesKey("app_locale")
        
        // Supported languages
        val SUPPORTED_LOCALES = mapOf(
            "en" to "English",
            "ru" to "Русский"
        )
        
        const val DEFAULT_LOCALE = "en"
    }
    
    private val dataStore = context.dataStore
    
    // Flow для отслеживания изменений локали
    val localeFlow: Flow<String> = dataStore.data.map { preferences ->
        preferences[LOCALE_KEY] ?: getSystemLocale()
    }
    
    // Получить текущую локаль
    suspend fun getCurrentLocale(): String {
        return dataStore.data.map { preferences ->
            preferences[LOCALE_KEY] ?: getSystemLocale()
        }.first()
    }
    
    // Установить локаль
    suspend fun setLocale(languageCode: String) {
        dataStore.edit { preferences ->
            preferences[LOCALE_KEY] = languageCode
        }
        updateAppContext(languageCode)
    }
    
    // Получить системную локаль
    private fun getSystemLocale(): String {
        val systemLocale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.resources.configuration.locales[0].language
        } else {
            @Suppress("DEPRECATION")
            context.resources.configuration.locale.language
        }
        
        // Возвращаем системную локаль если она поддерживается, иначе - дефолтную
        return if (systemLocale in SUPPORTED_LOCALES.keys) systemLocale else DEFAULT_LOCALE
    }
    
    // Обновить контекст приложения с новой локалью
    fun updateAppContext(languageCode: String): Context {
        val locale = Locale(languageCode)
        Locale.setDefault(locale)
        
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        
        return context.createConfigurationContext(config)
    }
    
    // Применить сохраненную локаль при запуске
    suspend fun applyStoredLocale() {
        val storedLocale = dataStore.data.map { preferences ->
            preferences[LOCALE_KEY]
        }.first()
        
        if (storedLocale != null) {
            updateAppContext(storedLocale)
        }
    }
}