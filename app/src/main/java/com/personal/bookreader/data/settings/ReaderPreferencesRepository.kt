package com.personal.bookreader.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.readium.r2.navigator.preferences.Theme

data class ReaderSettings(
    val theme: Theme = Theme.LIGHT,
    val fontSize: Double = 1.0,
    val lineHeight: Double? = null, // null = Publisher default
    val fontFamily: String? = null  // null = Publisher default, "sans-serif", "serif", "monospace"
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "reader_preferences")

class ReaderPreferencesRepository private constructor(private val context: Context) {

    private object PreferencesKeys {
        val THEME = stringPreferencesKey("reader_theme")
        val FONT_SIZE = doublePreferencesKey("reader_font_size")
        val LINE_HEIGHT = doublePreferencesKey("reader_line_height")
        val FONT_FAMILY = stringPreferencesKey("reader_font_family")
    }

    val settings: Flow<ReaderSettings> = context.dataStore.data.map { preferences ->
        val themeName = preferences[PreferencesKeys.THEME]
        val theme = when (themeName) {
            Theme.DARK.name -> Theme.DARK
            Theme.SEPIA.name -> Theme.SEPIA
            else -> Theme.LIGHT
        }
        val fontSize = preferences[PreferencesKeys.FONT_SIZE] ?: 1.0
        val lineHeight = preferences[PreferencesKeys.LINE_HEIGHT]
        val fontFamily = preferences[PreferencesKeys.FONT_FAMILY]

        ReaderSettings(
            theme = theme,
            fontSize = fontSize,
            lineHeight = lineHeight,
            fontFamily = fontFamily
        )
    }

    suspend fun updateTheme(theme: Theme) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.THEME] = theme.name
        }
    }

    suspend fun updateFontSize(fontSize: Double) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.FONT_SIZE] = fontSize
        }
    }

    suspend fun updateLineHeight(lineHeight: Double?) {
        context.dataStore.edit { preferences ->
            if (lineHeight == null) {
                preferences.remove(PreferencesKeys.LINE_HEIGHT)
            } else {
                preferences[PreferencesKeys.LINE_HEIGHT] = lineHeight
            }
        }
    }

    suspend fun updateFontFamily(fontFamily: String?) {
        context.dataStore.edit { preferences ->
            if (fontFamily == null) {
                preferences.remove(PreferencesKeys.FONT_FAMILY)
            } else {
                preferences[PreferencesKeys.FONT_FAMILY] = fontFamily
            }
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: ReaderPreferencesRepository? = null

        fun getInstance(context: Context): ReaderPreferencesRepository {
            return INSTANCE ?: synchronized(this) {
                val instance = ReaderPreferencesRepository(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}
