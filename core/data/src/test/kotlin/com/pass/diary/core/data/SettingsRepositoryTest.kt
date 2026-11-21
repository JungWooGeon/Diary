package com.pass.diary.core.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.pass.diary.core.model.FontId
import com.pass.diary.core.model.LightPaletteId
import com.pass.diary.core.model.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SettingsRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun decorateChoicesPersistAndSync() = runTest {
        val file = tmp.newFile("s.preferences_pb").apply { delete() }
        var clock = 100L
        val repo = SettingsRepository(PreferenceDataStoreFactory.create(scope = backgroundScope) { file }) { clock }
        repo.setPalette(LightPaletteId.SUNSET)
        repo.setFont(FontId.SERIF)
        clock = 200L
        repo.setTextSize(9) // clamped
        repo.setThemeMode(ThemeMode.PAPER)
        val s = repo.settings.first()
        assertEquals(LightPaletteId.SUNSET, s.palette)
        assertEquals(FontId.SERIF, s.font)
        assertEquals(4, s.textSize)
        assertEquals(ThemeMode.PAPER, s.themeMode)
        assertEquals(200L, s.updatedAt)

        // Lock options don't touch settings.json's clock.
        clock = 300L
        repo.setSecureScreen(true)
        assertEquals(200L, repo.settings.first().updatedAt)

        // settings.json round trip onto a fresh store.
        val other = SettingsRepository(PreferenceDataStoreFactory.create(scope = backgroundScope) { tmp.newFile("o.preferences_pb").apply { delete() } }) { 0 }
        other.applyRemote(repo.toJson(s))
        val o = other.settings.first()
        assertEquals(s.copy(secureScreen = false), o)
    }

    @Test fun trialIsNotSynced() = runTest {
        val repo = SettingsRepository(PreferenceDataStoreFactory.create(scope = backgroundScope) { tmp.newFile("t.preferences_pb").apply { delete() } }) { 5 }
        repo.startPaletteTrial(LightPaletteId.WINTER, until = 99)
        val s = repo.settings.first()
        assertEquals(LightPaletteId.WINTER, s.palette)
        assertEquals(99L, s.paletteTrialUntil)
        assertEquals(LightPaletteId.WINTER, s.paletteTrial)
        assertEquals(0L, s.updatedAt)
    }

    @Test fun aiSwitchesAndWidgetPrivacyPersistLocally() = runTest {
        val file = tmp.newFile("a.preferences_pb").apply { delete() }
        val repo = SettingsRepository(PreferenceDataStoreFactory.create(scope = backgroundScope) { file }) { 7 }
        repo.settings.first().let { assertTrue(it.aiReply && it.aiSuggest && !it.hideWidgetContent) }
        repo.setAiAll(false) // onboarding "AI 안 쓸래요"
        repo.setAiSuggest(true)
        repo.setHideWidgetContent(true)
        val s = repo.settings.first()
        assertEquals(listOf(false, true, true), listOf(s.aiReply, s.aiSuggest, s.hideWidgetContent))
        assertEquals(listOf(false, false), listOf(s.aiHighlight, s.aiRecall))
        assertEquals(0L, s.updatedAt) // not in settings.json
        assertFalse(repo.toJson(s).contains("ai"))
    }

    @Test fun reportReasonStoredLocally() = runTest {
        val r = ReplyReports(PreferenceDataStoreFactory.create(scope = backgroundScope) { tmp.newFile("r.preferences_pb").apply { delete() } })
        assertNull(r.reason("e1"))
        r.save("e1", ReplyReports.REASONS[2], at = 5)
        assertEquals("없는 이야기를 지어냈어요", r.reason("e1"))
    }
}
