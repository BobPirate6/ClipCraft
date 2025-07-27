package com.example.clipcraft.domain.repository

import android.content.SharedPreferences
import android.util.Log
import com.example.clipcraft.models.EditHistory
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Репозиторий для управления историей монтажей.
 * Сохраняет и загружает историю из SharedPreferences.
 */
@Singleton
class EditHistoryRepository @Inject constructor(
    private val sharedPreferences: SharedPreferences,
    private val gson: Gson
) {
    companion object {
        private const val KEY_EDIT_HISTORY = "edit_history"
    }

    /**
     * Загружает историю монтажей из локального кэша.
     */
    fun loadEditHistory(): List<EditHistory> {
        return try {
            val json = sharedPreferences.getString(KEY_EDIT_HISTORY, null)
            if (!json.isNullOrEmpty()) {
                val type = object : TypeToken<List<EditHistory>>() {}.type
                gson.fromJson(json, type) ?: emptyList()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e("EditHistoryRepository", "Error loading history", e)
            emptyList()
        }
    }

    /**
     * Сохраняет новую запись в историю монтажей.
     */
    fun saveEditHistory(historyEntry: EditHistory) {
        try {
            val currentHistory = loadEditHistory().toMutableList()
            // Добавляем новый элемент в начало списка, если его там еще нет
            if (currentHistory.none { it.id == historyEntry.id }) {
                currentHistory.add(0, historyEntry)
            }
            val json = gson.toJson(currentHistory)
            sharedPreferences.edit().putString(KEY_EDIT_HISTORY, json).apply()
        } catch (e: Exception) {
            Log.e("EditHistoryRepository", "Error saving history", e)
        }
    }
    
    /**
     * Обновляет существующую запись в истории монтажей.
     */
    fun updateEditHistory(historyEntry: EditHistory) {
        try {
            val currentHistory = loadEditHistory().toMutableList()
            val index = currentHistory.indexOfFirst { it.id == historyEntry.id }
            if (index != -1) {
                currentHistory[index] = historyEntry
                val json = gson.toJson(currentHistory)
                sharedPreferences.edit().putString(KEY_EDIT_HISTORY, json).apply()
                Log.d("EditHistoryRepository", "Updated history entry: ${historyEntry.id}")
            } else {
                Log.w("EditHistoryRepository", "History entry not found for update: ${historyEntry.id}")
                // If not found, save as new
                saveEditHistory(historyEntry)
            }
        } catch (e: Exception) {
            Log.e("EditHistoryRepository", "Error updating history", e)
        }
    }
}
