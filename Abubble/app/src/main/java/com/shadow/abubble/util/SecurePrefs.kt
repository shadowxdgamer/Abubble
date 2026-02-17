package com.shadow.abubble.util

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

/**
 * Encrypted SharedPreferences wrapper for storing sensitive data
 * like the OpenRouter API key.
 */
object SecurePrefs {

    private const val PREFS_NAME = "aboard_secure_prefs"
    private const val KEY_API_KEY = "openrouter_api_key"
    private const val KEY_SELECTED_MODEL = "selected_model_id"
    private const val KEY_REASONING_ENABLED = "reasoning_enabled"

    private var prefs: SharedPreferences? = null

    private fun getPrefs(context: Context): SharedPreferences {
        return prefs ?: run {
            val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)

            EncryptedSharedPreferences.create(
                PREFS_NAME,
                masterKeyAlias,
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            ).also { prefs = it }
        }
    }

    fun saveApiKey(context: Context, apiKey: String) {
        getPrefs(context).edit().putString(KEY_API_KEY, apiKey).apply()
    }

    fun getApiKey(context: Context): String {
        return getPrefs(context).getString(KEY_API_KEY, "") ?: ""
    }

    fun saveSelectedModel(context: Context, modelId: String) {
        getPrefs(context).edit().putString(KEY_SELECTED_MODEL, modelId).apply()
    }

    fun getSelectedModel(context: Context): String {
        return getPrefs(context).getString(KEY_SELECTED_MODEL, "") ?: ""
    }

    fun saveReasoningEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_REASONING_ENABLED, enabled).apply()
    }

    fun isReasoningEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_REASONING_ENABLED, false)
    }
}
