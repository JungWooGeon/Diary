package com.pass.diary.feature.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pass.diary.core.designsystem.AiBadge
import com.pass.diary.core.designsystem.ButtonKind
import com.pass.diary.core.designsystem.DayLightStrip
import com.pass.diary.core.designsystem.DayMark
import com.pass.diary.core.designsystem.EmptyDayStrip
import com.pass.diary.core.designsystem.Od
import com.pass.diary.core.designsystem.OdButton
import com.pass.diary.core.designsystem.OdCard
import com.pass.diary.core.designsystem.OdIcon
import com.pass.diary.core.designsystem.OdType
import com.pass.diary.core.designsystem.Oni
import com.pass.diary.core.designsystem.OniExpression
import com.pass.diary.core.designsystem.OniFigure
import com.pass.diary.core.designsystem.ambient
import com.pass.diary.core.designsystem.glowColor
import com.pass.diary.core.designsystem.readableWidth
import com.pass.diary.core.designsystem.rgb
import com.pass.diary.core.light.LightMixer
import com.pass.diary.core.model.Entry
import java.time.LocalDate

/**
 * Detail pane (tablets): one day read in full — its light across the day, then every record with its photos, tags and
 * 온이's reply. Editing still happens in the editor ([onOpen]). The caller adds the status-bar padding.
 */
@Composable
internal fun DayDetail(
    day: LocalDate,
    entries: List<Entry>,
    replies: Map<String, String>,
    onOpen: (String) -> Unit,
    onWriteOn: (String) -> Unit,
    modifier: Modifier = Modifier,
    future: Boolean = day.isAfter(LocalDate.now()),
    /** One search result: no day strip, no "write another". */
    single: Boolean = false,
    /** E4: a day with one record shows its 비슷했던 날; tapping one reads it ([EntryViewRoute]). null = not here. */
    onOpenSimilar: ((String) -> Unit)? = null,
) {
    val c = Od.c
    val glow = entries.flatMap { it.emotions }.takeIf { it.isNotEmpty() }?.let { rgb(LightMixer.mix(it).glow) }
    Column(
        modifier.fillMaxSize().then(if (glow != null) Modifier.ambient(glow, 0.5f, 60.dp, 520.dp) else Modifier)
            .verticalScroll(rememberScrollState()).navigationBarsPadding(),
    ) {
        Column(Modifier.readableWidth().padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text("${day.year}년 ${day.monthValue}월", style = OdType.caption1, color = c.ink2)
                    Text(koDay(day), style = OdType.title1)
                }
                if (entries.isNotEmpty() && !single) Text("기록 ${entries.size}개", style = OdType.caption1, color = c.ink2, modifier = Modifier.padding(bottom = 4.dp))
            }
            when {
                entries.isNotEmpty() -> {
                    if (!single) OdCard(padding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 14.dp)) {
                        Text("하루의 빛", style = OdType.caption2.copy(fontWeight = FontWeight.Bold), color = c.ink2, modifier = Modifier.padding(bottom = 12.dp))
                        DayLightStrip(entries.map { DayMark(it.time, it.emotions) })
                    }
                    entries.forEach { e -> EntryDetailCard(e, replies[e.id]) { onOpen(e.id) } }
                    if (onOpenSimilar != null && entries.size == 1) SimilarDaysFor(entries.single(), onOpenSimilar, Modifier.padding(top = 4.dp))
                    if (!single) OdButton("이날 하나 더 쓰기", { onWriteOn(day.toString()) }, Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp), kind = ButtonKind.Ghost, small = true, icon = OdIcon.Pen)
                }
                else -> Column(Modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    OniFigure(null, OniExpression.SLEEP, 150.dp)
                    Text(if (future) "아직 오지 않은 날이에요" else "이날은 기록이 없어요", style = OdType.title3, modifier = Modifier.padding(top = 12.dp))
                    if (!future) {
                        EmptyDayStrip(Modifier.padding(top = 16.dp).fillMaxWidth(0.7f))
                        OdButton("이날 쓰기", { onWriteOn(day.toString()) }, Modifier.padding(top = 20.dp), kind = ButtonKind.Secondary, small = true, icon = OdIcon.Pen)
                    }
                }
            }
        }
    }
}

/** One record in full: time, emotions, title, body, photos, tags, then 온이's reply. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EntryDetailCard(e: Entry, reply: String?, onOpen: () -> Unit) {
    val c = Od.c
    val light = LightMixer.mix(e.emotions)
    OdCard(Modifier.fillMaxWidth(), padding = PaddingValues(start = 22.dp, end = 22.dp, top = 18.dp, bottom = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (e.timeUnknown) "시간 모름" else koClock(e.time), style = OdType.caption2.copy(fontWeight = FontWeight.Bold), color = c.ink2, modifier = Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { e.emotions.forEach { EmotionTag(it.key) } }
        }
        val title = e.title.ifBlank { e.emotions.joinToString(" · ") { it.key.label }.ifBlank { "기록" } }
        Text(title, style = OdType.title2, modifier = Modifier.padding(top = 8.dp))
        if (e.body.isNotBlank()) MarkedBody(e.body.trim(), OdType.diary, Modifier.padding(top = 8.dp))
        if (e.photos.isNotEmpty()) Row(Modifier.padding(top = 14.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            e.photos.forEach { PhotoThumb(it, 0, 132.dp, 12.dp) }
        }
        if (e.tags.isNotEmpty()) FlowRow(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            e.tags.forEach { t ->
                Text("#${t.value}", style = OdType.caption1.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(c.surface2).padding(horizontal = 11.dp, vertical = 5.dp))
            }
        }
        if (reply != null) Column(Modifier.padding(top = 14.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.surface2).padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Oni(light, OniExpression.forGroup(LightMixer.dominantGroup(e.emotions)).takeIf { !light.off } ?: OniExpression.DEFAULT, Modifier.size(28.dp), animate = false)
                AiBadge("온이의 한마디", icon = false)
            }
            Text(reply, style = OdType.body2, modifier = Modifier.padding(top = 8.dp))
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.End) {
            OdButton("열어서 고치기", onOpen, kind = ButtonKind.Ghost, small = true, icon = OdIcon.Pen, height = 36.dp)
        }
    }
}

private fun koClock(time: String): String {
    val (h, m) = time.split(":").map { it.toInt() }
    return "${if (h < 12) "오전" else "오후"} ${(h % 12).let { if (it == 0) 12 else it }}:%02d".format(m)
}
