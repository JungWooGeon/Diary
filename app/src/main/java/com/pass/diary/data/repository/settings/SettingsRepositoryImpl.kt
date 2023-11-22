package com.pass.diary.data.repository.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")
val TextSizeKey = floatPreferencesKey("text_size")

class SettingsRepositoryImpl(private val context: Context) : SettingsRepository {

    //@TODO 의존성 주입을 통해 module 에서 dadtaStore instance가 한 번만 생성될 수 있도록
    override suspend fun updateCurrentTextSize(textSize: Float) {
        context.dataStore.edit { preferences ->
            preferences[TextSizeKey] = textSize
        }
    }

    override suspend fun getCurrentTextSize(): Flow<Float> {
        return context.dataStore.data.map { preferences ->
            preferences[TextSizeKey] ?: 16f
        }
    }
}