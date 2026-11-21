package com.pass.data.repository.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pass.data.di.IoDispatcher
import com.pass.domain.repository.settings.SettingsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val context: Context
) : SettingsRepository {

    companion object {
        private val TEXT_SIZE_KEY = floatPreferencesKey("text_size")
        private val FONT_KEY = stringPreferencesKey("text_font")
        private val PASSWORD_KEY = stringPreferencesKey("lock_password")
    }

    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")
    private val Context.fontDataStore: DataStore<Preferences> by preferencesDataStore(name = "font_settings")
    private val Context.passwordDataStore: DataStore<Preferences> by preferencesDataStore(name = "password_settings")

    override suspend fun updateCurrentTextSize(textSize: Float): Unit = withContext(ioDispatcher) {
        context.dataStore.edit { preferences ->
            preferences[TEXT_SIZE_KEY] = textSize
        }
    }

    override suspend fun getCurrentTextSize(): Flow<Float> = withContext(ioDispatcher) {
        context.dataStore.data.map { preferences ->
            preferences[TEXT_SIZE_KEY] ?: 16f
        }
    }

    override suspend fun updateCurrentFont(font: String): Unit = withContext(ioDispatcher) {
        context.fontDataStore.edit { preferences ->
            preferences[FONT_KEY] = font
        }
    }

    override suspend fun getCurrentFont(): Flow<String> = withContext(ioDispatcher) {
        context.fontDataStore.data.map { preferences ->
            preferences[FONT_KEY] ?: "default"
        }
    }

    override suspend fun updateCurrentPassword(password: String) {
        context.passwordDataStore.edit { preferences ->
            preferences[PASSWORD_KEY] = password
        }
    }

    override suspend fun getCurrentPassword(): Flow<String> = withContext(ioDispatcher) {
        context.passwordDataStore.data.map { preferences ->
            preferences[PASSWORD_KEY] ?: ""
        }
    }
}