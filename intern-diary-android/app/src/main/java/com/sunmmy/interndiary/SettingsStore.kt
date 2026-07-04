package com.sunmmy.interndiary

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

/**
 * Persists the server URL, API token, and UI preferences in SharedPreferences.
 */
class SettingsStore(context: Context) {
    private val sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var serverUrl: String
        get() = sp.getString(KEY_SERVER_URL, Config.DEFAULT_BASE_URL) ?: Config.DEFAULT_BASE_URL
        set(value) {
            sp.edit().putString(KEY_SERVER_URL, value.trim().trimEnd('/')).apply()
        }

    var token: String
        get() = sp.getString(KEY_TOKEN, "") ?: ""
        set(value) {
            sp.edit().putString(KEY_TOKEN, value.trim()).apply()
        }

    /**
     * Theme mode, one of AppCompatDelegate.MODE_NIGHT_NO / _YES / _FOLLOW_SYSTEM.
     * Defaults to following the system setting.
     */
    var themeMode: Int
        get() = sp.getInt(KEY_THEME_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        set(value) {
            sp.edit().putInt(KEY_THEME_MODE, value).apply()
        }

    companion object {
        private const val KEY_SERVER_URL = "serverUrl"
        private const val KEY_TOKEN = "token"
        private const val KEY_THEME_MODE = "themeMode"
    }
}
