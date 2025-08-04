package com.example.clipcraft.utils

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import java.util.Locale

object LocaleHelper {
    
    fun setLocale(context: Context, languageCode: String): Context {
        val locale = Locale(languageCode)
        Locale.setDefault(locale)
        
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        
        return context.createConfigurationContext(config)
    }
    
    fun onAttach(context: Context, defaultLanguage: String): Context {
        val lang = getPersistedLanguage(context, defaultLanguage)
        return setLocale(context, lang)
    }
    
    fun getLanguage(context: Context): String {
        return getPersistedLanguage(context, Locale.getDefault().language)
    }
    
    fun setLanguage(context: Context, language: String): Context {
        persist(context, language)
        return setLocale(context, language)
    }
    
    private fun getPersistedLanguage(context: Context, defaultLanguage: String): String {
        val prefs = context.getSharedPreferences("locale_prefs", Context.MODE_PRIVATE)
        return prefs.getString("language", defaultLanguage) ?: defaultLanguage
    }
    
    private fun persist(context: Context, language: String) {
        val prefs = context.getSharedPreferences("locale_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("language", language).apply()
    }
}