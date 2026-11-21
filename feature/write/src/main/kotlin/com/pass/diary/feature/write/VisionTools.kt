package com.pass.diary.feature.write

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.pass.diary.core.data.PhotoStore
import com.pass.diary.core.designsystem.ButtonKind
import com.pass.diary.core.designsystem.IconBtn
import com.pass.diary.core.designsystem.Od
import com.pass.diary.core.designsystem.OdButton
import com.pass.diary.core.designsystem.OdIcon
import com.pass.diary.core.designsystem.OdTopBar
import com.pass.diary.core.designsystem.OdType
import com.pass.diary.core.designsystem.readableWidth
import com.pass.diary.core.model.Photo
import com.pass.diary.core.model.Tag
import com.pass.diary.core.vision.Handwriting
import com.pass.diary.core.vision.InkPoint
import com.pass.diary.core.vision.PhotoTags
import com.pass.diary.core.vision.Readiness
import com.pass.diary.core.vision.Vision
import com.pass.diary.core.vision.VisionTool
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject

/**
 * ML Kit tools in the editor (core:vision, all on the phone): photo tag chips, 종이 일기 옮기기, 사진 스티커, 손글씨로 쓰기.
 * Kept out of WriteViewModel / WriteScreen: the editor only shows [photoTags] and places [VisionTools] in its tool bar.
 */
data class VisionState(
    val photoTags: List<Tag> = emptyList(),
    val ready: Map<VisionTool, Readiness> = emptyMap(),
    val inkReady: Boolean? = null,
    val busy: String? = null,
    /** Recognized text waiting for the user's check (종이 일기). */
    val draft: String? = null,
    val sticker: Bitmap? = null,
    val handwriting: Boolean = false,
    val askInkDownload: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class VisionViewModel @Inject constructor(
    private val vision: Vision,
    val ink: Handwriting,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val _state = MutableStateFlow(VisionState())
    val state = _state.asStateFlow()
    private val labeled = mutableMapOf<String, List<Tag>>()

    /** New photos -> label chips (suggestions; tapping one adds it like any other tag). Not ready = silently none. */
    fun labelPhotos(photos: List<Photo>) {
        labeling?.cancel()
        labeling = viewModelScope.launch {
            // The module may still be arriving right after the first prepare(): a few tries, then give up quietly.
            repeat(LABEL_TRIES) { attempt ->
                if (attempt > 0) delay(LABEL_RETRY_MS)
                val fresh = photos.filter { it.id !in labeled }
                if (fresh.isNotEmpty() && vision.prepare(VisionTool.PHOTO_TAGS) == Readiness.READY)
                    for (p in fresh) runCatching { vision.labels(Uri.fromFile(File(p.localPath))) }.onSuccess { labeled[p.id] = PhotoTags.from(it) }
                val ids = photos.map { it.id }.toSet()
                _state.update { it.copy(photoTags = labeled.filterKeys { k -> k in ids }.values.flatten().distinctBy { t -> t.value }) }
                if (photos.all { it.id in labeled }) return@launch
            }
        }
    }
    private var labeling: kotlinx.coroutines.Job? = null

    /** Menu opened: each row fills in as its check returns (Play services answers in ~1-5 s). */
    fun refresh() {
        for (tool in listOf(VisionTool.PAPER, VisionTool.STICKER)) viewModelScope.launch {
            val r = vision.prepare(tool)
            _state.update { it.copy(ready = it.ready + (tool to r)) }
        }
        viewModelScope.launch { val d = ink.isDownloaded(); _state.update { it.copy(inkReady = d) } }
    }

    suspend fun scanIntent(activity: Activity) = vision.scanIntent(activity)

    fun readPages(data: android.content.Intent?) {
        val pages = vision.scannedPages(data)
        if (pages.isEmpty()) return
        work("글자를 읽는 중…") {
            val text = pages.map { vision.readText(it).trim() }.filter { it.isNotEmpty() }.joinToString("\n\n")
            _state.update { if (text.isBlank()) it.copy(message = "글자를 찾지 못했어요. 밝은 곳에서 다시 찍어 볼까요?") else it.copy(draft = text) }
        }
    }

    fun cutout(uri: Uri) = work("사진에서 오려 내는 중…") {
        val b = vision.cutout(uri)
        _state.update { if (b == null) it.copy(message = "사람이나 물건을 찾지 못했어요. 다른 사진으로 해 볼까요?") else it.copy(sticker = b) }
    }

    /** The sticker as a PNG file PhotoStore.import keeps transparent (it's then just one of the entry's photos). */
    suspend fun stickerFile(b: Bitmap): Uri = withContext(Dispatchers.IO) {
        // Earlier stickers were imported (copied to photos/) long ago.
        context.cacheDir.listFiles { f -> f.name.startsWith("sticker-") }?.forEach { it.delete() }
        val f = File(context.cacheDir, "sticker-${UUID.randomUUID()}.png")
        f.outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
        Uri.fromFile(f)
    }

    fun openHandwriting() = viewModelScope.launch {
        if (ink.isDownloaded()) _state.update { it.copy(handwriting = true) } else _state.update { it.copy(askInkDownload = true) }
    }

    fun downloadInk() {
        _state.update { it.copy(askInkDownload = false) }
        work("손글씨 모델을 받는 중…", "받지 못했어요. 인터넷 연결을 확인하고 다시 해 볼까요?") {
            ink.download()
            _state.update { it.copy(inkReady = true, handwriting = true) }
        }
    }

    fun update(block: (VisionState) -> VisionState) = _state.update(block)

    private companion object {
        const val LABEL_TRIES = 4
        const val LABEL_RETRY_MS = 5_000L
    }

    private fun work(label: String, failed: String = "잘 되지 않았어요. 잠시 뒤 다시 해 볼까요?", block: suspend () -> Unit) = viewModelScope.launch {
        _state.update { it.copy(busy = label) }
        runCatching { block() }.onFailure { e ->
            android.util.Log.w("VisionTools", label, e)
            _state.update { it.copy(message = failed) }
        }
        _state.update { it.copy(busy = null) }
    }
}

/** Photo chips for the editor's "온이가 찾은 태그" row. */
@Composable
internal fun photoTagSuggestions(photos: List<Photo>, vm: VisionViewModel = hiltViewModel()): List<Tag> {
    LaunchedEffect(photos.map { it.id }) { vm.labelPhotos(photos) }
    return vm.state.collectAsStateWithLifecycle().value.photoTags
}

/** Tool bar item "더보기": menu with the three ML Kit tools, plus their dialogs. */
@Composable
internal fun VisionTools(photoCount: Int, onText: (String) -> Unit, onSticker: (Uri) -> Unit, vm: VisionViewModel = hiltViewModel()) {
    val c = Od.c
    val s by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }

    val scan = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        if (it.resultCode == Activity.RESULT_OK) vm.readPages(it.data)
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { it?.let(vm::cutout) }
    LaunchedEffect(s.message) { s.message?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show(); vm.update { st -> st.copy(message = null) } } }

    Box {
        ToolItem(OdIcon.More, "더보기") { open = true; vm.refresh() }
        DropdownMenu(open, { open = false }, containerColor = c.surface, shape = RoundedCornerShape(16.dp)) {
            val paper = s.ready[VisionTool.PAPER]
            val sticker = s.ready[VisionTool.STICKER]
            ToolMenuItem(OdIcon.Doc, "종이 일기 옮기기", hint(paper, "종이에 쓴 일기를 찍어 글자로 옮겨요"), paper == Readiness.READY) {
                open = false
                scope.launch {
                    runCatching { vm.scanIntent(context.findActivity()!!) }
                        .onSuccess { scan.launch(IntentSenderRequest.Builder(it).build()) }
                        .onFailure { vm.update { st -> st.copy(message = "스캐너를 열지 못했어요. 잠시 뒤 다시 해 볼까요?") } }
                }
            }
            val full = photoCount >= PhotoStore.MAX_PER_ENTRY
            ToolMenuItem(
                OdIcon.Image, "사진 스티커", if (full) "사진은 ${PhotoStore.MAX_PER_ENTRY}장까지 붙일 수 있어요" else hint(sticker, "사진에서 사람·물건만 오려 붙여요"),
                sticker == Readiness.READY && !full,
            ) { open = false; pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
            ToolMenuItem(
                OdIcon.Pen, "손글씨로 쓰기",
                when (s.inkReady) { false -> "처음 한 번 약 ${vm.ink.modelSizeMb}MB를 내려받아요"; true -> "펜이나 손가락으로 써요"; null -> "확인하는 중…" }, s.inkReady != null,
            ) { open = false; vm.openHandwriting() }
        }
    }

    s.busy?.let { label ->
        Dialog({}, DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)) {
            Row(Modifier.clip(RoundedCornerShape(20.dp)).background(c.surface).padding(horizontal = 22.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(22.dp), color = c.ink, strokeWidth = 2.5.dp)
                Spacer(Modifier.width(14.dp))
                Text(label, style = OdType.body2)
            }
        }
    }

    if (s.askInkDownload) AlertDialog(
        onDismissRequest = { vm.update { it.copy(askInkDownload = false) } },
        title = { Text("손글씨 인식 모델 받기", style = OdType.title3) },
        text = { Text("한국어 손글씨를 알아보려면 모델을 한 번 내려받아야 해요.\n약 ${vm.ink.modelSizeMb}MB · Wi-Fi에서 받는 걸 권장해요.\n받은 뒤에는 인터넷 없이 폰 안에서만 인식해요.", style = OdType.body2) },
        confirmButton = { TextButton(onClick = vm::downloadInk) { Text("받기", color = c.ink, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = { vm.update { it.copy(askInkDownload = false) } }) { Text("나중에", color = c.ink2) } },
        containerColor = c.surface,
    )

    s.draft?.let { text ->
        TextCheckDialog(
            "옮길 글 확인", "잘못 읽은 글자가 있으면 고친 뒤 넣어요", text,
            onClose = { vm.update { it.copy(draft = null) } }, onInsert = { onText(it); vm.update { st -> st.copy(draft = null) } },
        )
    }

    s.sticker?.let { b ->
        StickerDialog(
            b, onClose = { vm.update { it.copy(sticker = null) } },
            onRetry = { vm.update { it.copy(sticker = null) }; pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onAttach = { scope.launch { onSticker(vm.stickerFile(b)); vm.update { it.copy(sticker = null) } } },
        )
    }

    if (s.handwriting) HandwritingDialog(vm.ink, onClose = { vm.update { it.copy(handwriting = false) } }, onInsert = { onText(it); vm.update { st -> st.copy(handwriting = false) } })
}

private fun hint(r: Readiness?, ready: String) = when (r) {
    Readiness.READY -> ready
    Readiness.PREPARING -> "준비하는 중이에요 · 잠시 뒤 다시 열어 주세요"
    Readiness.UNSUPPORTED -> "이 기기에서는 쓸 수 없어요"
    null -> "확인하는 중…"
}

@Composable
private fun ToolMenuItem(icon: OdIcon, title: String, sub: String, enabled: Boolean, onClick: () -> Unit) {
    val c = Od.c
    DropdownMenuItem(
        text = {
            Column(Modifier.padding(vertical = 6.dp).width(220.dp)) {
                Text(title, style = OdType.title3, color = if (enabled) c.ink else c.ink3)
                Text(sub, style = OdType.caption2, color = if (enabled) c.ink2 else c.ink3)
            }
        },
        leadingIcon = { OdIcon(icon, size = 22.dp, tint = if (enabled) c.ink else c.ink3) },
        onClick = onClick, enabled = enabled,
    )
}

/** Full-screen sheet: top bar (닫기 · title · 넣기) over [content]. */
@Composable
private fun ToolSheet(title: String, canInsert: Boolean, onClose: () -> Unit, onInsert: (() -> Unit)?, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    val c = Od.c
    Dialog(onClose, DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        // Edge-to-edge dialog window: status/nav bar icons follow the theme like the screen behind.
        val view = androidx.compose.ui.platform.LocalView.current
        androidx.compose.runtime.SideEffect {
            (view.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window?.let {
                androidx.core.view.WindowCompat.getInsetsController(it, view).apply { isAppearanceLightStatusBars = !c.dark; isAppearanceLightNavigationBars = !c.dark }
            }
        }
        Box(Modifier.fillMaxSize().background(c.bg), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.readableWidth(720.dp).fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
                OdTopBar(
                    title = title,
                    left = { IconBtn(OdIcon.Close, onClose, "닫기") },
                    right = { if (onInsert != null) OdButton("넣기", onInsert, Modifier.padding(end = 4.dp), small = true, height = 36.dp, enabled = canInsert) },
                )
                content()
            }
        }
    }
}

@Composable
private fun TextCheckDialog(title: String, hint: String, initial: String, onClose: () -> Unit, onInsert: (String) -> Unit) {
    val c = Od.c
    var text by remember(initial) { mutableStateOf(initial) }
    ToolSheet(title, text.isNotBlank(), onClose, { onInsert(text.trim()) }) {
        Text(hint, style = OdType.caption1, color = c.ink2, modifier = Modifier.padding(horizontal = 20.dp))
        Box(Modifier.padding(20.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.surface).verticalScroll(rememberScrollState()).padding(16.dp)) {
            BasicTextField(text, { text = it }, Modifier.fillMaxWidth(), textStyle = OdType.diary.copy(color = c.ink), cursorBrush = SolidColor(c.ink))
        }
    }
}

@Composable
private fun StickerDialog(b: Bitmap, onClose: () -> Unit, onRetry: () -> Unit, onAttach: () -> Unit) {
    val c = Od.c
    ToolSheet("사진 스티커", true, onClose, null) {
        Text("배경을 지운 스티커예요. 사진처럼 일기에 붙어요.", style = OdType.caption1, color = c.ink2, modifier = Modifier.padding(horizontal = 20.dp))
        Box(Modifier.weight(1f).fillMaxWidth().padding(20.dp).clip(RoundedCornerShape(20.dp)).checkerboard(c.surface, c.surface2).padding(24.dp), contentAlignment = Alignment.Center) {
            Image(remember(b) { b.asImageBitmap() }, "오려 낸 스티커", Modifier.fillMaxSize())
        }
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OdButton("다른 사진", onRetry, Modifier.weight(1f), kind = ButtonKind.Secondary)
            OdButton("스티커 붙이기", onAttach, Modifier.weight(1.5f))
        }
    }
}

/** Transparency shown as the usual checkerboard. */
private fun Modifier.checkerboard(a: Color, b: Color) = drawBehind {
    drawRect(a)
    val cell = 12.dp.toPx()
    var y = 0; var yy = 0f
    while (yy < size.height) {
        var x = y % 2; var xx = x * cell
        while (xx < size.width) { drawRect(b, Offset(xx, yy), androidx.compose.ui.geometry.Size(cell, cell)); xx += cell * 2 }
        y++; yy += cell
    }
}

@Composable
private fun HandwritingDialog(ink: Handwriting, onClose: () -> Unit, onInsert: (String) -> Unit) {
    val c = Od.c
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    val strokes = remember { mutableStateListOf<List<InkPoint>>() }
    var current by remember { mutableStateOf<List<InkPoint>>(emptyList()) }
    var area by remember { mutableStateOf(androidx.compose.ui.geometry.Size.Zero) }
    var reading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val penColor = c.ink

    fun recognize() {
        if (strokes.isEmpty() || reading) return
        reading = true
        scope.launch {
            val r = runCatching { ink.recognize(strokes.toList(), area.width, area.height, text) }.getOrNull()
            reading = false
            if (r.isNullOrBlank()) failed = true else {
                failed = false
                text = if (text.isEmpty()) r else "$text $r"
                strokes.clear()
            }
        }
    }

    ToolSheet("손글씨로 쓰기", text.isNotBlank(), onClose, { onInsert(text.trim()) }) {
        Box(Modifier.padding(horizontal = 20.dp).fillMaxWidth().heightIn(min = 64.dp).clip(RoundedCornerShape(16.dp)).background(c.surface).padding(14.dp)) {
            if (text.isEmpty()) Text("한 줄씩 쓰고 '글자로'를 눌러요", style = OdType.diary, color = c.ink3)
            BasicTextField(text, { text = it }, Modifier.fillMaxWidth(), textStyle = OdType.diary.copy(color = c.ink), cursorBrush = SolidColor(c.ink))
        }
        if (failed) Text("글자를 알아보지 못했어요. 조금 더 크게 써 볼까요?", style = OdType.caption2, color = c.ink2, modifier = Modifier.padding(start = 24.dp, top = 6.dp))
        Box(
            Modifier.weight(1f).fillMaxWidth().padding(20.dp).clip(RoundedCornerShape(20.dp)).background(c.surface)
                .onSizeChanged { area = androidx.compose.ui.geometry.Size(it.width.toFloat(), it.height.toFloat()) }
                .pointerInput(Unit) {
                    val t0 = System.currentTimeMillis()
                    detectDragGestures(
                        onDragStart = { o -> current = listOf(InkPoint(o.x, o.y, System.currentTimeMillis() - t0 + 1)) },
                        onDrag = { ch, _ -> current = current + InkPoint(ch.position.x, ch.position.y, System.currentTimeMillis() - t0 + 1) },
                        onDragEnd = { strokes.add(current); current = emptyList() },
                        onDragCancel = { strokes.add(current); current = emptyList() },
                    )
                },
        ) {
            // guide line
            Canvas(Modifier.fillMaxSize()) {
                drawLine(c.line, Offset(24f, size.height * 0.62f), Offset(size.width - 24f, size.height * 0.62f), 2f)
                for (s in strokes + listOf(current)) {
                    if (s.isEmpty()) continue
                    val path = Path().apply { moveTo(s[0].x, s[0].y); s.drop(1).forEach { lineTo(it.x, it.y) } }
                    drawPath(path, penColor, style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
            if (strokes.isEmpty() && current.isEmpty()) Text("여기에 써요", style = OdType.title2, color = c.ink3.copy(alpha = 0.6f), modifier = Modifier.align(Alignment.Center))
        }
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OdButton("지우기", { strokes.clear(); failed = false }, Modifier.weight(1f), kind = ButtonKind.Secondary, enabled = strokes.isNotEmpty())
            OdButton(if (reading) "읽는 중…" else "글자로", ::recognize, Modifier.weight(1.5f), enabled = strokes.isNotEmpty() && !reading)
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
