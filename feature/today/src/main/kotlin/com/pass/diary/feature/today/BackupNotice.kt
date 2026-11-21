package com.pass.diary.feature.today

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.pass.diary.core.designsystem.Od
import com.pass.diary.core.designsystem.OdIcon
import com.pass.diary.core.designsystem.OdType
import com.pass.diary.core.sync.DriveAuth
import com.pass.diary.core.sync.SyncEngine
import com.pass.diary.core.sync.SyncError
import com.pass.diary.core.sync.SyncLocalStore
import com.pass.diary.core.sync.SyncStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 오늘 화면 위 띠: backup trouble (backup on), or a weekly "turn it on" hint (backup off). */
enum class BackupNotice(val text: String, val cta: String, val hint: Boolean = false) {
    QUOTA("드라이브 공간이 가득 차서 백업이 멈췄어요", "백업 보기"),
    AUTH("드라이브 연결이 끊겨 백업이 멈췄어요", "다시 연결"),
    STALE("3일 넘게 백업하지 못한 기록이 있어요", "백업 보기"),
    HINT("백업을 켜 두면 폰을 바꿔도 안전해요", "백업 켜기", hint = true),
}

internal const val HOUR_MS = 60L * 60 * 1000
internal const val DAY_MS = 24 * HOUR_MS

/**
 * [dismissedAt]: last ✕ on a trouble notice (hidden 24 h). [hintAt]: start of the current hint week (the hint shows
 * for the first day of each week); null = never shown. Off → only the hint; on → only trouble, never the hint.
 */
fun backupNotice(signedIn: Boolean, s: SyncStatus, now: Long, dismissedAt: Long?, hintAt: Long?, hintNever: Boolean): BackupNotice? {
    if (!signedIn) {
        if (hintNever) return null
        return BackupNotice.HINT.takeIf { hintAt == null || now - hintAt < DAY_MS || now - hintAt >= 7 * DAY_MS }
    }
    if (dismissedAt != null && now - dismissedAt in 0 until DAY_MS) return null
    return when {
        s.error == SyncError.STORAGE_FULL -> BackupNotice.QUOTA
        s.error == SyncError.NOT_SIGNED_IN -> BackupNotice.AUTH
        s.pending > 0 && s.lastBackupAt != null && now - s.lastBackupAt!! > 3 * DAY_MS -> BackupNotice.STALE
        else -> null
    }
}

@HiltViewModel
class BackupNoticeViewModel @Inject constructor(private val sync: SyncEngine, private val store: SyncLocalStore) : ViewModel() {
    private val tick = MutableStateFlow(0)
    val notice = combine(sync.status, tick) { s, _ ->
        val now = System.currentTimeMillis()
        val hintAt = store.meta(HINT_AT)?.toLongOrNull()
        backupNotice(store.meta(DriveAuth.META_ACCOUNT) != null, s, now, store.meta(DISMISSED_AT)?.toLongOrNull(), hintAt, store.meta(HINT_NEVER) != null)
            .also { if (it == BackupNotice.HINT && (hintAt == null || now - hintAt >= 7 * DAY_MS)) store.setMeta(HINT_AT, now.toString()) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init { viewModelScope.launch { runCatching { sync.refreshStatus() } } }

    /** ✕: trouble hides for 24 h; the hint until next week. */
    fun dismiss(n: BackupNotice) = viewModelScope.launch {
        val now = System.currentTimeMillis()
        if (n.hint) store.setMeta(HINT_AT, (now - DAY_MS).toString()) else store.setMeta(DISMISSED_AT, now.toString())
        tick.value++
    }

    fun never() = viewModelScope.launch { store.setMeta(HINT_NEVER, "1"); tick.value++ }

    private companion object {
        // Not "sync.*": these survive an account switch (clearSyncState).
        const val DISMISSED_AT = "notice.backupDismissedAt"
        const val HINT_AT = "notice.backupHintAt"
        const val HINT_NEVER = "notice.backupHintNever"
    }
}

/** Compact and calm: one line, a soft surface, no alarm colors. */
@Composable
fun BackupNoticeBar(onBackup: () -> Unit, modifier: Modifier = Modifier, vm: BackupNoticeViewModel = hiltViewModel()) {
    val n by vm.notice.collectAsStateWithLifecycle()
    val notice = n ?: return
    val c = Od.c
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.surface2).clickable(onClick = onBackup).padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        OdIcon(if (notice.hint) OdIcon.Shield else OdIcon.Download, size = 18.dp, tint = c.ink2)
        Column(Modifier.weight(1f)) {
            Text(notice.text, style = OdType.caption1.copy(fontWeight = FontWeight.SemiBold), color = c.ink)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 2.dp)) {
                Text("${notice.cta} ›", style = OdType.caption2.copy(fontWeight = FontWeight.Bold), color = c.ink2)
                if (notice.hint) Text("다시 보지 않기", style = OdType.caption2, color = c.ink3, modifier = Modifier.clickable { vm.never() })
            }
        }
        Box(Modifier.size(40.dp).clip(CircleShape).clickable { vm.dismiss(notice) }, contentAlignment = Alignment.Center) {
            OdIcon(OdIcon.Close, size = 16.dp, tint = c.ink3, contentDescription = "닫기")
        }
    }
}
