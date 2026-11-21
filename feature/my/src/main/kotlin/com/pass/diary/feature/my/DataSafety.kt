package com.pass.diary.feature.my

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.pass.diary.core.data.EntryRepository
import com.pass.diary.core.designsystem.ButtonKind
import com.pass.diary.core.designsystem.Divider
import com.pass.diary.core.designsystem.IconBtn
import com.pass.diary.core.designsystem.Od
import com.pass.diary.core.designsystem.OdButton
import com.pass.diary.core.designsystem.OdCard
import com.pass.diary.core.designsystem.OdIcon
import com.pass.diary.core.designsystem.OdToggle
import com.pass.diary.core.designsystem.OdTopBar
import com.pass.diary.core.designsystem.OdType
import com.pass.diary.core.designsystem.OniExpression
import com.pass.diary.core.designsystem.OniFigure
import com.pass.diary.core.light.LightMixer
import com.pass.diary.core.model.Entry
import com.pass.diary.core.sync.DiaryArchive
import com.pass.diary.core.sync.DriveAuth
import com.pass.diary.core.sync.SyncLocalStore
import com.pass.diary.core.sync.SyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.time.LocalDate
import javax.inject.Inject

@Serializable data object TrashRoute
@Serializable data object ExportRoute

@HiltViewModel
class TrashViewModel @Inject constructor(private val repository: EntryRepository) : ViewModel() {
    val items = repository.observeTrash().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    init { viewModelScope.launch { repository.purgeTrash() } } // the app may have been open for days
    fun restore(id: String) = viewModelScope.launch { repository.restore(id) }
    fun deleteForever(id: String) = viewModelScope.launch { repository.delete(id) }
    fun empty() = viewModelScope.launch { repository.emptyTrash() }
}

/** "D-n": whole days until the entry leaves the trash for good (at least 1 while it is still here). */
internal fun daysLeft(trashedAt: Long, now: Long): Int =
    ((trashedAt + EntryRepository.TRASH_KEEP_MS - now + DAY_MS - 1) / DAY_MS).toInt().coerceIn(1, 30)

private const val DAY_MS = 24L * 60 * 60 * 1000

/** 마이 › 휴지통: 지운 일기는 30일 동안 여기 있다가 완전히 지워져요. */
@Composable
fun TrashScreen(onBack: () -> Unit, vm: TrashViewModel = hiltViewModel()) {
    val c = Od.c
    val items by vm.items.collectAsStateWithLifecycle()
    var forever by remember { mutableStateOf<Entry?>(null) }
    var emptying by remember { mutableStateOf(false) }
    BackHandler(onBack = onBack)
    forever?.let { e ->
        Confirm("이 기록을 영구 삭제할까요?", "되돌릴 수 없어요. 드라이브 백업에서도 내용이 지워져요.", "영구 삭제", { vm.deleteForever(e.id) }, { forever = null })
    }
    if (emptying) Confirm("휴지통을 비울까요?", "휴지통의 기록 ${items?.size ?: 0}개가 완전히 지워져요. 되돌릴 수 없어요.", "비우기", vm::empty) { emptying = false }

    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding().verticalScroll(rememberScrollState())) {
        OdTopBar(
            title = "휴지통", left = { IconBtn(OdIcon.Back, onBack, "뒤로") },
            right = { if (!items.isNullOrEmpty()) Text("비우기", style = OdType.body2.copy(fontWeight = FontWeight.SemiBold), color = c.ink2, modifier = Modifier.clip(CircleShape).clickable { emptying = true }.padding(12.dp)) },
        )
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("지운 기록은 30일 동안 여기 있다가 완전히 지워져요.", style = OdType.caption1, color = c.ink2)
            val list = items ?: return@Column
            if (list.isEmpty()) Column(Modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                OniFigure(null, OniExpression.SLEEP, 120.dp)
                Text("휴지통이 비어 있어요", style = OdType.body2, color = c.ink2, modifier = Modifier.padding(top = 12.dp))
            }
            val now = System.currentTimeMillis()
            list.forEach { e -> TrashRow(e, daysLeft(e.trashedAt ?: now, now), onRestore = { vm.restore(e.id) }, onDelete = { forever = e }) }
        }
        Spacer(Modifier.navigationBarsPadding().height(24.dp))
    }
}

@Composable
private fun TrashRow(e: Entry, left: Int, onRestore: () -> Unit, onDelete: () -> Unit) {
    val c = Od.c
    val light = LightMixer.mix(e.emotions)
    OdCard(padding = PaddingValues(horizontal = 18.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(12.dp).clip(CircleShape).background(Color(0xFF000000.toInt() or light.glow)))
            Text(dateLabel(e.date) + " " + e.time, style = OdType.caption1, color = c.ink2, modifier = Modifier.weight(1f))
            Text("${left}일 남음", style = OdType.caption2.copy(fontWeight = FontWeight.SemiBold), color = if (left <= 3) c.ink else c.ink3)
        }
        val head = e.title.ifBlank { e.body.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty() }
            .ifBlank { e.emotions.joinToString(" · ") { it.key.label }.ifBlank { "빈 기록" } }
        Text(head, style = OdType.body2.copy(fontWeight = FontWeight.SemiBold), maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        if (e.title.isNotBlank() && e.body.isNotBlank()) Text(e.body.replace('\n', ' '), style = OdType.caption1, color = c.ink2, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OdButton("복원", onRestore, kind = ButtonKind.Secondary, small = true, height = 36.dp)
            OdButton("영구 삭제", onDelete, kind = ButtonKind.Ghost, small = true, height = 36.dp)
        }
    }
}

private fun dateLabel(date: String) = runCatching { LocalDate.parse(date).let { "${it.year}. ${it.monthValue}. ${it.dayOfMonth}." } }.getOrDefault(date)

@Composable
private fun Confirm(title: String, text: String, yes: String, onYes: () -> Unit, onDismiss: () -> Unit) {
    val c = Od.c
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = OdType.title3) },
        text = { Text(text, style = OdType.body2, color = c.ink2) },
        confirmButton = { TextButton(onClick = { onDismiss(); onYes() }) { Text(yes, color = c.ink, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소", color = c.ink2) } },
        containerColor = c.surface,
    )
}

// ======================================================================= 내보내기·가져오기

@HiltViewModel
class ExportViewModel @Inject constructor(@ApplicationContext private val context: Context, private val store: SyncLocalStore) : ViewModel() {
    val includeTrash = MutableStateFlow(false)
    val busy = MutableStateFlow(false)

    fun export(uri: Uri) = run {
        val n = withContext(Dispatchers.IO) {
            context.contentResolver.openOutputStream(uri, "wt")!!.use { DiaryArchive.export(store, it, includeTrash.value) }
        }
        "기록 ${n}개를 파일로 저장했어요"
    }

    fun import(uri: Uri) = run {
        val r = withContext(Dispatchers.IO) { DiaryArchive.import(store) { context.contentResolver.openInputStream(uri)!! } }
        if (store.meta(DriveAuth.META_ACCOUNT) != null && r.added + r.updated > 0) SyncScheduler.syncNow(context)
        if (r.added + r.updated == 0) "새로 가져올 기록이 없어요. 이 폰에 이미 다 있어요"
        else "새 기록 ${r.added}개, 고친 기록 ${r.updated}개를 가져왔어요" + if (r.skipped > 0) " (이미 있는 기록 ${r.skipped}개는 그대로 뒀어요)" else ""
    }

    private fun run(work: suspend () -> String) = viewModelScope.launch {
        busy.value = true
        val msg = runCatching { work() }.getOrElse { "파일을 처리하지 못했어요. 온이 일기에서 내보낸 zip 파일인지 확인해 주세요" }
        busy.value = false
        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
    }
}

/** 마이 › 내보내기: zip (entries.json + photos/ + diary.html + README.txt) through the Storage Access Framework. Free. */
@Composable
fun ExportScreen(onBack: () -> Unit, vm: ExportViewModel = hiltViewModel()) {
    val c = Od.c
    val includeTrash by vm.includeTrash.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { it?.let(vm::export) }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::import) }
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding().verticalScroll(rememberScrollState())) {
        OdTopBar(title = "내보내기", left = { IconBtn(OdIcon.Back, onBack, "뒤로") })
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OdCard(padding = PaddingValues(horizontal = 18.dp, vertical = 16.dp)) {
                Text("내 파일로 내보내기", style = OdType.title3)
                Text(
                    "일기·사진을 zip 파일 하나로 저장해요. 안에 든 diary.html을 열면 날짜별로 읽고 인쇄할 수 있어요. 드라이브 없이도 언제든 다시 가져올 수 있어요.",
                    style = OdType.caption1, color = c.ink2, modifier = Modifier.padding(top = 6.dp),
                )
                Row(Modifier.fillMaxWidth().clickable { vm.includeTrash.value = !includeTrash }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("휴지통의 기록도 넣기", style = OdType.body2, modifier = Modifier.weight(1f))
                    OdToggle(includeTrash, { vm.includeTrash.value = it })
                }
                OdButton(
                    if (busy) "처리하는 중…" else "zip 파일로 저장", { save.launch("온이일기-${LocalDate.now()}.zip") },
                    Modifier.fillMaxWidth(), icon = OdIcon.Download, enabled = !busy,
                )
            }
            OdCard(padding = PaddingValues(horizontal = 18.dp, vertical = 16.dp)) {
                Text("파일에서 가져오기", style = OdType.title3)
                Text(
                    "전에 내보낸 zip 파일에서 일기를 되살려요. 이 폰에 있는 기록은 더 최근에 고친 쪽이 남아요.",
                    style = OdType.caption1, color = c.ink2, modifier = Modifier.padding(top = 6.dp, bottom = 12.dp),
                )
                OdButton("zip 파일 고르기", { open.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) }, Modifier.fillMaxWidth(), kind = ButtonKind.Secondary, enabled = !busy)
            }
            Divider()
            Text("내보낸 파일에는 일기 내용이 그대로 들어 있어요. 다른 사람과 공유하지 않도록 조심해 주세요.", style = OdType.caption2, color = c.ink2)
        }
        Spacer(Modifier.navigationBarsPadding().height(24.dp))
    }
}
