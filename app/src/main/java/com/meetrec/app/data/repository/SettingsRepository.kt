package com.meetrec.app.data.repository

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsRepository private constructor(context: Context) {

    private val prefs = context.getSharedPreferences("meetrec_settings", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_API_KEY = "openrouter_api_key"
        private const val KEY_MODEL = "llm_model"
        const val DEFAULT_MODEL = "openai/gpt-4o-mini"
        const val MODEL_CLAUDE = "anthropic/claude-3.5-haiku"
        const val MODEL_LLAMA = "meta-llama/llama-3.3-70b-instruct"

        @Volatile
        private var instance: SettingsRepository? = null

        fun getInstance(context: Context): SettingsRepository {
            return instance ?: synchronized(this) {
                instance ?: SettingsRepository(context.applicationContext).also { instance = it }
            }
        }
    }

    private val _apiKeyFlow = MutableStateFlow(getApiKey())
    val apiKeyFlow: StateFlow<String> = _apiKeyFlow.asStateFlow()

    fun getApiKey(): String {
        return prefs.getString(KEY_API_KEY, "") ?: ""
    }

    fun setApiKey(apiKey: String) {
        val clean = apiKey.trim()
        prefs.edit().putString(KEY_API_KEY, clean).apply()
        _apiKeyFlow.value = clean
    }

    fun hasApiKey(): Boolean {
        return getApiKey().isNotBlank()
    }

    fun getSelectedModel(): String {
        return prefs.getString(KEY_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL
    }

    fun setSelectedModel(model: String) {
        prefs.edit().putString(KEY_MODEL, model.trim()).apply()
    }
}

