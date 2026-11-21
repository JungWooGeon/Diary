package com.pass.diary.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private val Context.reportStore by preferencesDataStore("reply_reports")

/**
 * Why the user reported an AI reply (Play AI-content policy). Kept on this phone only: never synced, never sent.
 * The reply itself is marked in Room ([com.pass.diary.core.data.db.ReplyEntity.reported]).
 */
@Singleton
class ReplyReports internal constructor(private val store: DataStore<Preferences>) {
    @Inject constructor(@ApplicationContext context: Context) : this(context.reportStore)

    suspend fun save(entryId: String, reason: String, at: Long = System.currentTimeMillis()) =
        store.edit { it[stringPreferencesKey("report.$entryId")] = "$at|$reason" }

    suspend fun reason(entryId: String): String? = store.data.first()[stringPreferencesKey("report.$entryId")]?.substringAfter('|')

    companion object {
        val REASONS = listOf("상처가 되는 말이에요", "내 일기와 맞지 않아요", "없는 이야기를 지어냈어요", "충고나 진단처럼 들려요", "기타")
    }
}
