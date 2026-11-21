package com.pass.diary

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.res.ResourcesCompat
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.pass.diary.core.data.EntryRepository
import com.pass.diary.core.designsystem.OniExpression
import com.pass.diary.core.light.Light
import com.pass.diary.core.light.LightMixer
import com.pass.diary.core.model.EmotionGroup
import com.pass.diary.core.model.EmotionKey
import com.pass.diary.core.model.Entry
import com.pass.diary.core.security.LockManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.YearMonth
import com.pass.diary.core.designsystem.R as DsR

/*
 * 위젯 3종 (시안 08): ① 1탭 기분 4×1 ② 오늘의 온이 2×2 ③ 월간 빛 4×2. Glance can't draw radial gradients or the layered
 * Oni, so those parts are small bitmaps drawn with android.graphics (same recipe as Oni.kt / Components.kt).
 * 잠금 › 위젯에 내용 숨기기, or the PIN lock on → no emotion names or mood colors on widgets, only "오늘 기록 N개" (README §5).
 */

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetDeps {
    fun repository(): EntryRepository
    fun lock(): LockManager
    fun settings(): com.pass.diary.core.data.SettingsRepository
}

private fun deps(context: Context) = EntryPointAccessors.fromApplication(context.applicationContext, WidgetDeps::class.java)
/** Follows the app's theme (꾸미기), not just the system's; also picks up the 빛 팔레트 for the bitmaps. */
private suspend fun isNight(context: Context): Boolean {
    val s = deps(context).settings().settings.first()
    // ponytail: palette shown as chosen; a lapsed trial falls back in-app first and reaches widgets on the next refresh.
    WidgetArt.palette = com.pass.diary.core.light.Palette[s.palette]
    return when (s.themeMode) {
        com.pass.diary.core.model.ThemeMode.DARK -> true
        com.pass.diary.core.model.ThemeMode.LIGHT, com.pass.diary.core.model.ThemeMode.PAPER -> false
        com.pass.diary.core.model.ThemeMode.SYSTEM -> (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }
}

private val PaperW = Color(0xEBFBF7F1) // paper 92 %
private val NightW = Color(0xE6262141) // surface night 90 %
private val Ink = Color(0xFF2E2622)
private val Ink2 = Color(0xFF6B5E55)
private val InkD = Color(0xFFF4EFE8)
private val Ink2D = Color(0xFFBDB5CC)

/** 위젯에 내용 숨기기: the setting, or forced while the PIN lock is on. */
private suspend fun hidden(context: Context) = deps(context).let { it.settings().settings.first().hideWidgetContent || it.lock().enabled.value }

/** A light that says "recorded" without saying which mood (brand amber). */
internal val NeutralLight = Light(0xFFF0CC, 0xF5A957, 0.6f, off = false)

/** 오늘의 온이 title / subtitle. [hidden]: no emotion names. */
internal fun oniWidgetText(today: List<Entry>, hidden: Boolean): Pair<String, String> {
    val names = today.flatMap { it.emotions }.sortedByDescending { it.intensity }.map { it.key.label }.distinct().take(2).joinToString(" · ")
    return when {
        today.isEmpty() -> "오늘은 아직 꺼져 있어요" to "톡 눌러서 불 켜 주기"
        hidden -> "오늘 기록 ${today.size}개" to "온이가 지키고 있어요"
        else -> names.ifBlank { "잘 모르겠음" } to "오늘 빛 · 기록 ${today.size}개"
    }
}

/** Refreshes all three after any save (called from Startup). */
suspend fun updateWidgets(context: Context) {
    refreshes.value++
    MoodWidget().updateAll(context)
    OniWidget().updateAll(context)
    MonthWidget().updateAll(context)
}

/**
 * Glance keeps a widget's session open for a while after a tap and then only recomposes on update(), so data read
 * before provideContent would stay stale (the 1탭 widget kept saying "오늘 6번째"). Widgets reload through this instead.
 */
private val refreshes = kotlinx.coroutines.flow.MutableStateFlow(0)

@androidx.compose.runtime.Composable
private fun <T> reloading(first: T, load: suspend () -> T): T {
    val n by refreshes.collectAsState()
    return androidx.compose.runtime.produceState(first, n) { if (n > 0) value = load() }.value
}

// ------------------------------------------------------------------ ① 1탭 기분 4×1

class MoodWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        suspend fun load() = deps(context).repository().observeDay(EntryRepository.today()).first()
        val first = load()
        val night = isNight(context)
        val density = context.resources.displayMetrics.density
        val orbs = EmotionKey.QUICK.associateWith { WidgetArt.orb(it.group!!, (34 * density).toInt(), (10 * density).toInt()) }
        provideContent {
            val today = reloading(first, ::load)
            Column(
                GlanceModifier.fillMaxSize().cornerRadius(26.dp).background(if (night) NightW else PaperW).padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Row(GlanceModifier.fillMaxWidth()) {
                    Text("지금 기분 톡", style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ColorProvider(if (night) Ink2D else Ink2)), modifier = GlanceModifier.defaultWeight())
                    Text("오늘 ${today.size + 1}번째", style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ColorProvider(if (night) Ink2D else Ink2)))
                }
                Spacer(GlanceModifier.height(4.dp))
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    EmotionKey.QUICK.forEachIndexed { i, k ->
                        Box(GlanceModifier.defaultWeight(), contentAlignment = Alignment.Center) {
                            Image(
                                ImageProvider(orbs.getValue(k)), contentDescription = k.label + "으로 기록",
                                modifier = GlanceModifier.size(48.dp).clickable(actionRunCallback<QuickMoodAction>(actionParametersOf(QuickMoodAction.KEY to k.key))),
                            )
                        }
                    }
                }
            }
        }
    }
}

class QuickMoodAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val key = parameters[KEY]?.let(EmotionKey::of) ?: return
        deps(context).repository().quickMood(key)
        updateWidgets(context)
    }

    companion object { val KEY = ActionParameters.Key<String>("mood") }
}

class MoodWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget = MoodWidget() }

// ------------------------------------------------------------------ ② 오늘의 온이 2×2

class OniWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val density = context.resources.displayMetrics.density
        suspend fun load(): Triple<Bitmap, String, String> {
            isNight(context) // always a night card; this loads the 빛 팔레트
            val today = deps(context).repository().observeDay(EntryRepository.today()).first()
            val hide = hidden(context)
            val light = if (today.isEmpty()) LightMixer.OFF else if (hide) NeutralLight else LightMixer.mixDay(today.map { it.emotions })
            val expr = if (hide && today.isNotEmpty()) OniExpression.DEFAULT else OniExpression.forState(today.flatMap { it.emotions })
            val (title, sub) = oniWidgetText(today, hide)
            return Triple(WidgetArt.oni(context, light.takeUnless { today.isEmpty() }, expr, (112 * density).toInt()), title, sub)
        }
        val first = load()
        val bg = WidgetArt.nightCard((160 * density).toInt(), (160 * density).toInt(), 26 * density)
        provideContent {
            val (oni, title, sub) = reloading(first, ::load)
            Box(GlanceModifier.fillMaxSize().cornerRadius(26.dp).background(ImageProvider(bg)).clickable(actionStartActivity<MainActivity>())) {
                Box(GlanceModifier.fillMaxWidth().padding(top = 2.dp), contentAlignment = Alignment.TopCenter) {
                    Image(ImageProvider(oni), contentDescription = "오늘의 온이", modifier = GlanceModifier.size(104.dp), contentScale = ContentScale.Fit)
                }
                Column(GlanceModifier.fillMaxSize().padding(start = 14.dp, end = 12.dp, bottom = 11.dp), verticalAlignment = Alignment.Bottom) {
                    Text(title, style = TextStyle(fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = ColorProvider(InkD)), maxLines = 1)
                    Text(sub, style = TextStyle(fontSize = 10.5.sp, color = ColorProvider(InkD.copy(alpha = 0.7f))), maxLines = 1)
                }
            }
        }
    }
}

class OniWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget = OniWidget() }

// ------------------------------------------------------------------ ③ 월간 빛 4×2

class MonthWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val m = YearMonth.now()
        val night = isNight(context)
        val density = context.resources.displayMetrics.density
        suspend fun load(): Pair<Bitmap, Int> {
            val entries = deps(context).repository().observeRange(m.atDay(1).toString(), m.atEndOfMonth().toString()).first()
            return WidgetArt.month(context, m, entries.groupBy { it.date }, night, (300 * density).toInt(), density, hidden(context)) to
                entries.map { it.date }.distinct().size
        }
        val first = load()
        provideContent {
            val (grid, days) = reloading(first, ::load)
            Column(
                GlanceModifier.fillMaxSize().cornerRadius(26.dp).background(if (night) NightW else PaperW).padding(horizontal = 14.dp, vertical = 12.dp)
                    .clickable(actionStartActivity<MainActivity>()),
            ) {
                Row(GlanceModifier.fillMaxWidth()) {
                    Text("${m.monthValue}월의 빛", style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ColorProvider(if (night) InkD else Ink)), modifier = GlanceModifier.defaultWeight())
                    Text("${days}일 기록", style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ColorProvider(if (night) Ink2D else Ink2)))
                }
                Spacer(GlanceModifier.height(8.dp))
                Image(ImageProvider(grid), contentDescription = "${m.monthValue}월 빛 캘린더", modifier = GlanceModifier.fillMaxWidth().defaultWeight(), contentScale = ContentScale.Fit)
            }
        }
    }
}

class MonthWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget = MonthWidget() }

// ------------------------------------------------------------------ bitmaps

private object WidgetArt {
    @Volatile var palette = com.pass.diary.core.light.Palette[com.pass.diary.core.model.LightPaletteId.BASIC]
    private fun argb(rgb: Int, a: Float = 1f) = ((a * 255).toInt().coerceIn(0, 255) shl 24) or (rgb and 0xFFFFFF)
    /** An emotion color in the active palette. */
    private val Int.p get() = palette.remap(this)

    private fun lerp(a: Int, b: Int, t: Float): Int {
        fun ch(s: Int) = (((a shr s) and 0xFF) + (((b shr s) and 0xFF) - ((a shr s) and 0xFF)) * t).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    /** 1-tap orb with its soft colored shadow (README §4: 0 3 10 glow@50 %). */
    fun orb(g: EmotionGroup, d: Int, pad: Int): Bitmap {
        val size = d + pad * 2
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val cx = size / 2f
        val r = d / 2f
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(cx, cx + d * 0.09f, r * 1.35f, intArrayOf(argb(g.glow.p, 0.5f), argb(g.glow.p, 0f)), floatArrayOf(0.55f, 1f), Shader.TileMode.CLAMP)
        }
        c.drawCircle(cx, cx + d * 0.09f, r * 1.35f, shadow)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(cx, cx - r * 0.2f, r * 1.08f, intArrayOf(0xFFFFFDF6.toInt(), argb(g.light.p), argb(g.glow.p)), floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP)
        }
        c.drawCircle(cx, cx, r, p)
        return bmp
    }

    /** Night card behind the 2×2 widget: #2A2448 → #1C1830. */
    fun nightCard(w: Int, h: Int, radius: Float): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(0f, 0f, 0f, h.toFloat(), 0xFF2A2448.toInt(), 0xFF1C1830.toInt(), Shader.TileMode.CLAMP) }
        Canvas(bmp).drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), radius, radius, p)
        return bmp
    }

    /** Oni with halo, tinted like Oni.kt: base + light·L + glow·G (additive). [light] null = off & sleepy. */
    fun oni(context: Context, light: Light?, expr: OniExpression, px: Int): Bitmap {
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val dst = Rect(0, 0, px, px)
        val res = context.resources
        if (light == null) {
            c.drawBitmap(android.graphics.BitmapFactory.decodeResource(res, DsR.drawable.oni_off), null, dst, Paint(Paint.FILTER_BITMAP_FLAG))
            return bmp
        }
        val cx = px / 2f
        val cy = px * 232f / 512f
        val r = px * 250f / 512f
        val power = 0.6f + 0.4f * light.strength
        c.drawCircle(cx, cy, r, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                cx, cy, r, intArrayOf(argb(light.glow.p, 0.9f * power), argb(light.glow.p, 0.5f * power), argb(light.glow.p, 0.16f * power), argb(light.glow.p, 0f)),
                floatArrayOf(0f, 0.45f, 0.72f, 1f), Shader.TileMode.CLAMP,
            )
        })
        c.saveLayer(null, null)
        fun draw(id: Int, tint: Int?) {
            val p = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
            if (tint != null) {
                p.colorFilter = PorterDuffColorFilter(argb(tint), PorterDuff.Mode.SRC_IN)
                p.xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
            }
            c.drawBitmap(android.graphics.BitmapFactory.decodeResource(res, id), null, dst, p)
        }
        draw(expr.base, null); draw(expr.l, light.light.p); draw(expr.g, light.glow.p)
        c.restore()
        return bmp
    }

    /** The calendar-cell rule (README §2) in miniature: 7 columns, 14dp cells, radius 8, gradients for multi-entry days. */
    /** [hidden]: a recorded day is one neutral color (no mood). */
    fun month(context: Context, m: YearMonth, byDate: Map<String, List<Entry>>, night: Boolean, width: Int, density: Float, hidden: Boolean): Bitmap {
        val gap = 3 * density
        val cellH = 14 * density
        val lead = m.atDay(1).dayOfWeek.value % 7
        val rows = (lead + m.lengthOfMonth() + 6) / 7
        val height = (rows * cellH + (rows - 1) * gap).toInt()
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val cellW = (width - 6 * gap) / 7
        val base = ResourcesCompat.getFont(context, DsR.font.pretendard) ?: Typeface.DEFAULT
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = if (Build.VERSION.SDK_INT >= 28) Typeface.create(base, 600, false) else base
            textSize = 9 * density * context.resources.configuration.fontScale.coerceAtMost(1.2f)
            textAlign = Paint.Align.CENTER
        }
        val today = LocalDate.now()
        val sub = if (night) 0xFFBDB5CC.toInt() else 0xFF6B5E55.toInt()
        val ink = if (night) 0xFFF4EFE8.toInt() else 0xFF2E2622.toInt()
        val radius = 8 * density
        for (d in 1..m.lengthOfMonth()) {
            val i = lead + d - 1
            val x = (i % 7) * (cellW + gap)
            val y = (i / 7) * (cellH + gap)
            val rect = RectF(x, y, x + cellW, y + cellH)
            val date = m.atDay(d)
            val es = byDate[date.toString()].orEmpty().sortedBy { it.time }
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            val fg: Int
            if (es.isNotEmpty()) {
                val cols = (if (hidden) listOf(NeutralLight) else es.map { LightMixer.mix(it.emotions) })
                    .map { if (night) lerp(it.glow.p, 0x1C1830, 0.12f) else argb(it.light.p) }
                if (cols.size == 1) p.color = cols[0]
                else p.shader = LinearGradient(rect.left, rect.top, rect.right, rect.bottom, cols.toIntArray(), null, Shader.TileMode.CLAMP)
                c.drawRoundRect(rect, radius, radius, p)
                fg = if (night) 0xFF1C1830.toInt() else 0xFF2E2622.toInt()
            } else if (!date.isAfter(today)) {
                p.color = argb(0x7F7369, 0.14f)
                c.drawRoundRect(rect, radius, radius, p)
                fg = sub
            } else fg = sub
            if (date == today) {
                val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.5f * density; color = ink }
                val o = 1.2f * density
                c.drawRoundRect(RectF(rect.left - o, rect.top - o, rect.right + o, rect.bottom + o), radius + o, radius + o, ring)
            }
            text.color = fg
            c.drawText("$d", rect.centerX(), rect.centerY() - (text.descent() + text.ascent()) / 2, text)
        }
        return bmp
    }
}
