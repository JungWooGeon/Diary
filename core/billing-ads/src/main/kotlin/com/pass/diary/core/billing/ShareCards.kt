package com.pass.diary.core.billing

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.pass.diary.core.designsystem.ButtonKind
import com.pass.diary.core.designsystem.IconBtn
import com.pass.diary.core.designsystem.ListRow
import com.pass.diary.core.designsystem.Od
import com.pass.diary.core.designsystem.OdButton
import com.pass.diary.core.designsystem.OdChip
import com.pass.diary.core.designsystem.OdColors
import com.pass.diary.core.designsystem.OdIcon
import com.pass.diary.core.designsystem.OdToggle
import com.pass.diary.core.designsystem.OdTopBar
import com.pass.diary.core.designsystem.OdType
import com.pass.diary.core.designsystem.PremiumBadge
import com.pass.diary.core.designsystem.ambient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/*
 * 공유 카드 (AI_GROWTH §3): one renderer for 오늘의 빛, 주간 온이 편지, 월간 빛 캘린더 and 올해의 빛. The card is laid
 * out at 236×420 dp and, for the image, again off screen at 1080×1920 px (density scaled), so text stays sharp.
 * Diary text never goes on a card. Free cards carry a small "온이 일기 · web" line; premium can turn it off and pick a theme.
 */

/** Instagram story size. */
const val STORY_W = 1080
const val STORY_H = 1920
private const val CARD_W = 236
private const val CARD_H = 420

const val WATERMARK = "온이 일기 · jungwoogeon.github.io/Diary"

/** Card backgrounds. [NIGHT] is free; the others come with premium. */
enum class CardTheme(val label: String, val premium: Boolean, val top: Color, val bottom: Color, val ink: Color) {
    NIGHT("밤", false, Color(0xFF2A2448), OdColors.Night, Color(0xFFF4EFE8)),
    DAWN("새벽", true, Color(0xFFF7E4E4), Color(0xFFE2DCF3), Color(0xFF3A2D3C)),
    PAPER("종이", true, Color(0xFFFBF4E7), Color(0xFFEFE3CB), Color(0xFF3A2E25)),
    ;

    val dark get() = this == NIGHT
    /** An unlit mosaic / calendar cell on this background. */
    val offCell get() = ink.copy(alpha = if (dark) 0.07f else 0.08f)
}

/** The card itself: theme gradient, [glow] rising from the bottom, [content] padded, watermark at the bottom. */
@Composable
fun StoryFrame(theme: CardTheme, glow: Color, watermark: Boolean, modifier: Modifier = Modifier, export: Boolean = false, content: @Composable BoxScope.() -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier.size(CARD_W.dp, CARD_H.dp)
            .let { if (export) it else it.floating(shape).clip(shape) }
            .background(Brush.verticalGradient(listOf(theme.top, theme.bottom)))
            .drawBehind {
                val center = Offset(size.width / 2, size.height * 0.83f)
                drawCircle(Brush.radialGradient(listOf(glow.copy(alpha = if (theme.dark) 0.35f else 0.45f), Color.Transparent), center = center, radius = size.width * 0.9f), size.width * 0.9f, center)
            }
            .padding(horizontal = 18.dp, vertical = 22.dp),
    ) {
        content()
        if (watermark) Text(
            WATERMARK, style = OdType.caption2.copy(fontSize = 8.5.sp, fontWeight = FontWeight.SemiBold),
            color = theme.ink.copy(alpha = 0.5f), modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun Modifier.floating(shape: androidx.compose.ui.graphics.Shape): Modifier {
    val m = dropShadow(shape, Shadow(radius = 50.dp, color = Color(0x591C1830), offset = DpOffset(0.dp, 20.dp)))
    return if (Od.c.dark) m.border(1.dp, Color.White.copy(alpha = 0.08f), shape) else m
}

/**
 * Preview + 카드 테마 + 워터마크 + 저장 / 스토리에 공유. [premiumOnly] (올해의 빛): free users get the paywall on export.
 * Otherwise free users may share right away; once a day the [NudgeReason.SHARE] sheet comes first ("그냥 공유할게요" goes on).
 */
@Composable
fun StoryShare(
    title: String,
    fileName: String,
    glow: Color,
    onClose: () -> Unit,
    onPremium: () -> Unit,
    headline: String? = null,
    premiumOnly: Boolean = false,
    topRight: (@Composable () -> Unit)? = null,
    card: @Composable BoxScope.(CardTheme) -> Unit,
) {
    val c = Od.c
    val ctx = LocalContext.current
    val premium = Od.premium
    val gate = remember { NudgeGate(ctx.applicationContext) }
    var theme by rememberSaveable { mutableStateOf(CardTheme.NIGHT) }
    var hideMark by rememberSaveable { mutableStateOf(false) }
    val usedTheme = if (premium) theme else CardTheme.NIGHT
    val watermark = !(premium && hideMark)
    val layer = rememberGraphicsLayer()
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }

    val export = { block: suspend (Bitmap) -> Unit ->
        val run = {
            if (!busy) {
                busy = true
                scope.launch {
                    runCatching { block(layer.toImageBitmap().asAndroidBitmap().toStory()) }.onFailure {
                        Log.w("StoryShare", "export failed", it)
                        Toast.makeText(ctx, "이미지를 만들지 못했어요. 다시 해 주세요", Toast.LENGTH_SHORT).show()
                    }
                    busy = false
                }
            }
        }
        when {
            premiumOnly && !premium -> onPremium()
            !premium && gate.canShow(null) -> { gate.shown(null); pending = run }
            else -> run()
        }
    }

    Box(Modifier.fillMaxSize().background(c.bg).ambient(glow, 1.1f, 360.dp, 460.dp)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            OdTopBar(title = title, left = { IconBtn(OdIcon.Back, onClose, "뒤로") }, right = topRight)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                StoryFrame(usedTheme, glow, watermark, Modifier.padding(top = 14.dp)) { card(usedTheme) }
                if (headline != null) Text(headline, style = OdType.title2, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 22.dp, start = 20.dp, end = 20.dp))
                Text(
                    "인스타그램 스토리 비율(9:16)로 저장돼요.\n일기 내용은 들어가지 않아요.", style = OdType.caption1, color = c.ink2,
                    textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp),
                )
                Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    CardTheme.entries.forEach { t ->
                        Box {
                            OdChip(t.label, selected = t == usedTheme, outlined = true, height = 34.dp, dot = t.top) {
                                if (t.premium && !premium) onPremium() else theme = t
                            }
                            if (t.premium && !premium) PremiumBadge(null, Modifier.align(Alignment.TopEnd).offset(x = 8.dp, y = (-10).dp), height = 16.dp)
                        }
                    }
                }
                Box(Modifier.padding(horizontal = 20.dp).padding(top = 6.dp, bottom = 16.dp).fillMaxWidth()) {
                    ListRow(null, "워터마크 끄기", if (premium) null else "프리미엄", if (premium) null else onPremium, height = 48.dp, trailing = if (premium) ({ OdToggle(hideMark, { hideMark = it }) }) else null)
                }
            }
            Row(Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp).navigationBarsPadding(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val locked = premiumOnly && !premium
                OdButton("저장", {
                    export { bmp ->
                        if (saveToGallery(ctx, bmp, fileName)) Toast.makeText(ctx, "사진첩 「온이 일기」에 저장했어요", Toast.LENGTH_SHORT).show()
                        else shareImage(ctx, bmp, fileName, title)
                    }
                }, Modifier.weight(1f), kind = ButtonKind.Secondary, icon = if (locked) OdIcon.Lock else OdIcon.Download)
                OdButton("스토리에 공유", { export { shareImage(ctx, it, fileName, title) } }, Modifier.weight(1.4f), icon = OdIcon.Share)
            }
        }
        // The shared image, recorded into [layer] and never drawn on screen.
        Box(Modifier.size(0.dp)) {
            CompositionLocalProvider(LocalDensity provides Density(STORY_H / CARD_H.toFloat(), 1f)) {
                StoryFrame(
                    usedTheme, glow, watermark,
                    Modifier.wrapContentSize(Alignment.TopStart, unbounded = true).drawWithContent { layer.record { this@drawWithContent.drawContent() } },
                    export = true,
                ) { card(usedTheme) }
            }
        }
    }
    pending?.let { go ->
        PremiumNudgeSheet(NudgeReason.SHARE, gate, onPremium = { pending = null; onPremium() }, onDismiss = { pending = null; go() }, dismissLabel = "그냥 공유할게요")
    }
}

/** [StoryShare] over the current screen (오늘·리포트 cards). */
@Composable
fun StoryShareDialog(
    title: String, fileName: String, glow: Color, onClose: () -> Unit, onPremium: () -> Unit, headline: String? = null,
    card: @Composable BoxScope.(CardTheme) -> Unit,
) {
    Dialog(onClose, DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        // The dialog window has its own status bar look: match the app's (dark icons on paper).
        val view = androidx.compose.ui.platform.LocalView.current
        val dark = Od.c.dark
        androidx.compose.runtime.SideEffect {
            (view.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window?.let {
                androidx.core.view.WindowCompat.getInsetsController(it, view).isAppearanceLightStatusBars = !dark
            }
        }
        StoryShare(title, fileName, glow, onClose, { onClose(); onPremium() }, headline, card = card)
    }
}

internal fun Bitmap.toStory(): Bitmap = if (width == STORY_W && height == STORY_H) this else Bitmap.createScaledBitmap(this, STORY_W, STORY_H, true)

/** Saves to Pictures/온이 일기 (no permission on API 29+). Returns false on API < 29: the caller shares instead. */
internal suspend fun saveToGallery(ctx: Context, bmp: Bitmap, name: String): Boolean = withContext(Dispatchers.IO) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return@withContext false // ponytail: pre-10 would need WRITE_EXTERNAL_STORAGE; share sheet covers it
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "$name.png")
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/온이 일기")
        put(MediaStore.Images.Media.IS_PENDING, 1)
    }
    val r = ctx.contentResolver
    val uri = r.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return@withContext false
    runCatching {
        r.openOutputStream(uri)!!.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        r.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
    }.onFailure { r.delete(uri, null, null) }.isSuccess
}

/** Share sheet with the PNG (cache/share/, served by the app's FileProvider "<appId>.files"). */
internal suspend fun shareImage(ctx: Context, bmp: Bitmap, name: String, title: String) {
    val file = withContext(Dispatchers.IO) {
        File(ctx.cacheDir, "share").apply { mkdirs() }.resolve("$name.png").also { f -> f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } }
    }
    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", file)
    // ClipData carries the read grant to the chooser too (its image preview), not only to the picked app.
    val send = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    send.clipData = android.content.ClipData.newRawUri("", uri)
    ctx.startActivity(Intent.createChooser(send, "$title 공유").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION))
}
