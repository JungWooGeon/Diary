package com.pass.data.repository.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pass.domain.repository.settings.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(private val context: Context) : SettingsRepository {

    companion object {
        private val TEXT_SIZE_KEY = floatPreferencesKey("text_size")
        private val FONT_KEY = stringPreferencesKey("text_font")
    }

    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")
    private val Context.fontDataStore: DataStore<Preferences> by preferencesDataStore(name = "font_settings")

    override suspend fun updateCurrentTextSize(textSize: Float) {
        context.dataStore.edit { preferences ->
            preferences[TEXT_SIZE_KEY] = textSize
        }
    }

    override suspend fun getCurrentTextSize(): Flow<Float> {
        return context.dataStore.data.map { preferences ->
            preferences[TEXT_SIZE_KEY] ?: 16f
        }
    }

    override suspend fun updateCurrentFont(font: String) {
        context.fontDataStore.edit { preferences ->
            preferences[FONT_KEY] = font
        }
    }

    override suspend fun getCurrentFont(): Flow<String> {
        return context.fontDataStore.data.map { preferences ->
            preferences[FONT_KEY] ?: "default"
        }
    }
}