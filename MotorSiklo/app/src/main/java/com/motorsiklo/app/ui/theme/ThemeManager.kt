package com.motorsiklo.app.ui.theme

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object ThemeManager {
    var isDarkTheme by mutableStateOf(true)
        private set

    fun init(context: Context) {
        val prefs = context.getSharedPreferences("motorsiklo_prefs", Context.MODE_PRIVATE)
        isDarkTheme = prefs.getBoolean("is_dark_theme", true)
    }

    fun toggleTheme(context: Context) {
        isDarkTheme = !isDarkTheme
        context.getSharedPreferences("motorsiklo_prefs", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("is_dark_theme", isDarkTheme)
            .apply()
    }
}