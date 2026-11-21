package com.pass.diary

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.pass.diary.core.data.EntryRepository
import com.pass.diary.core.model.EmotionKey
import com.pass.diary.core.model.EntryEmotion
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Debug only: one entry with a given body (Korean can't be typed through `adb input`), to check the 답장 flow.
 *   adb shell am broadcast -n com.pass.diary/.DebugAiReceiver --es id g1-joy --es body '오늘 ...' [--es emotion joy]
 *   adb shell am start -n com.pass.diary/.MainActivity --es nav reply:g1-joy
 * Or just put Korean on the clipboard to paste into a real field: --es clip '오늘 ...'
 */
@AndroidEntryPoint
class DebugAiReceiver : BroadcastReceiver() {
    @Inject lateinit var entries: EntryRepository

    override fun onReceive(context: Context, intent: Intent) {
        intent.getStringExtra("clip")?.let {
            context.getSystemService(android.content.ClipboardManager::class.java).setPrimaryClip(android.content.ClipData.newPlainText("debug", it))
            return
        }
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val e = EntryRepository.newEntry(EntryRepository.today()).copy(
                    id = intent.getStringExtra("id") ?: "g1-test",
                    body = intent.getStringExtra("body").orEmpty(),
                    emotions = intent.getStringExtra("emotion")?.let(EmotionKey::of)?.let { listOf(EntryEmotion(it, 3)) }.orEmpty(),
                )
                entries.save(e)
            } finally { pending.finish() }
        }
    }
}
