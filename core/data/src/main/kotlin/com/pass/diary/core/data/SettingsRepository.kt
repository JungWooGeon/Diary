package com.pass.diary.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pass.diary.core.model.FontId
import com.pass.diary.core.model.LightPaletteId
import com.pass.diary.core.model.Settings
import com.pass.diary.core.model.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsStore by preferencesDataStore("settings_v2") // v1 used "settings"

@Singleton
class SettingsRepository(private val store: DataStore<Preferences>, private val now: () -> Long) {
    @Inject constructor(@ApplicationContext context: Context) : this(context.settingsStore, System::currentTimeMillis)

    private val theme = stringPreferencesKey("theme_mode")
    private val secure = booleanPreferencesKey("secure_screen")
    private val relock = intPreferencesKey("relock_after_sec")
    private val palette = stringPreferencesKey("palette")
    private val trial = longPreferencesKey("palette_trial_until")
    private val trialKey = stringPreferencesKey("palette_trial")
    private val font = stringPreferencesKey("font")
    private val textSize = intPreferencesKey("text_size")
    private val updated = longPreferencesKey("updated_at")
    private val aiReply = booleanPreferencesKey("ai_reply")
    private val aiSuggest = booleanPreferencesKey("ai_suggest")
    private val aiChat = booleanPreferencesKey("ai_chat")
    private val hideWidget = booleanPreferencesKey("hide_widget_content")

    val settings: Flow<Settings> = store.data.map { p ->
        Settings(
            themeMode = p[theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            secureScreen = p[secure] ?: false,
            relockAfterSec = p[relock] ?: Settings().relockAfterSec,
            palette = LightPaletteId.of(p[palette]),
            paletteTrial = p[trialKey]?.let { k -> LightPaletteId.entries.firstOrNull { it.key == k } },
            paletteTrialUntil = p[trial] ?: 0,
            font = FontId.of(p[font]),
            textSize = (p[textSize] ?: 2).coerceIn(0, Settings.TEXT_SCALES.lastIndex),
            updatedAt = p[updated] ?: 0,
            aiReply = p[aiReply] ?: true,
            aiSuggest = p[aiSuggest] ?: true,
            aiChat = p[aiChat] ?: true,
            hideWidgetContent = p[hideWidget] ?: false,
        )
    }

    /** Edits that belong in settings.json on Drive bump [Settings.updatedAt]. Lock options stay on this phone. */
    private suspend fun editSynced(block: (MutablePreferences) -> Unit) = store.edit { block(it); it[updated] = now() }

    suspend fun setThemeMode(mode: ThemeMode) = editSynced { it[theme] = mode.name }
    suspend fun setSecureScreen(on: Boolean) = store.edit { it[secure] = on }
    suspend fun setRelockAfterSec(sec: Int) = store.edit { it[relock] = sec }
    suspend fun setPalette(p: LightPaletteId) = editSynced { it[palette] = p.key }
    /** A day's trial of a premium palette (rewarded ad): select it until [until]. Not synced — it's this phone's ad. */
    suspend fun startPaletteTrial(p: LightPaletteId, until: Long) = store.edit { it[palette] = p.key; it[trialKey] = p.key; it[trial] = until }
    suspend fun setFont(f: FontId) = editSynced { it[font] = f.key }
    // AI and widget privacy are this phone's choice: not in settings.json.
    suspend fun setAiReply(on: Boolean) = store.edit { it[aiReply] = on }
    suspend fun setAiSuggest(on: Boolean) = store.edit { it[aiSuggest] = on }
    suspend fun setAiChat(on: Boolean) = store.edit { it[aiChat] = on }
    /** Onboarding's AI choice: all three at once. */
    suspend fun setAiAll(on: Boolean) = store.edit { it[aiReply] = on; it[aiSuggest] = on; it[aiChat] = on }
    suspend fun setHideWidgetContent(on: Boolean) = store.edit { it[hideWidget] = on }
    suspend fun setTextSize(step: Int) = editSynced { it[textSize] = step.coerceIn(0, Settings.TEXT_SCALES.lastIndex) }

    /** settings.json (ARCHITECTURE §2.2): theme + 꾸미기. No PIN, nothing lock-related. */
    fun toJson(s: Settings): String = buildJsonObject {
        put("version", 1)
        put("theme", s.themeMode.name.lowercase())
        put("palette", s.palette.key)
        put("font", s.font.key)
        put("textSize", s.textSize)
        put("updatedAt", s.updatedAt)
    }.toString()

    /** Newer settings.json from another device. Unknown values keep the local ones. */
    suspend fun applyRemote(json: String) {
        val o = runCatching { Json.parseToJsonElement(json).jsonObject }.getOrNull() ?: return
        fun str(k: String) = o[k]?.jsonPrimitive?.content
        store.edit { p ->
            str("theme")?.let { t -> ThemeMode.entries.firstOrNull { it.name.equals(t, true) } }?.let { p[theme] = it.name }
            str("palette")?.let { k -> LightPaletteId.entries.firstOrNull { it.key == k } }?.let { p[palette] = it.key }
            str("font")?.let { k -> FontId.entries.firstOrNull { it.key == k } }?.let { p[font] = it.key }
            runCatching { o["textSize"]!!.jsonPrimitive.int }.getOrNull()?.let { p[textSize] = it.coerceIn(0, Settings.TEXT_SCALES.lastIndex) }
            runCatching { o["updatedAt"]!!.jsonPrimitive.long }.getOrNull()?.let { p[updated] = it }
        }
    }
}
