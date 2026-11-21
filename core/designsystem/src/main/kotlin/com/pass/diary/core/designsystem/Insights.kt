package com.pass.diary.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pass.diary.core.light.LightMixer
import com.pass.diary.core.model.EmotionKey
import com.pass.diary.core.model.Entry
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** The small "AI" mark: only where the on-device model did the work (AI_GROWTH §1), never on 온이의 한마디. */
@Composable
fun AiMark(modifier: Modifier = Modifier) {
    Box(modifier.height(16.dp).clip(RoundedCornerShape(5.dp)).background(Od.c.surface2).padding(horizontal = 5.dp), contentAlignment = Alignment.Center) {
        Text("AI", style = OdType.caption2.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold, lineHeight = 12.sp), color = Od.c.ink2)
    }
}

/** E4 비슷했던 날: 1–3 earlier entries close to this one. Nothing when [days] is empty. */
@Composable
fun SimilarDays(days: List<Entry>, onOpen: (String) -> Unit, modifier: Modifier = Modifier, today: LocalDate = LocalDate.now()) {
    if (days.isEmpty()) return
    val c = Od.c
    OdCard(modifier.fillMaxWidth(), padding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Oni(null, OniExpression.DEFAULT, Modifier.size(34.dp), animate = false)
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("비슷했던 날", style = OdType.title3)
                    AiMark()
                }
                Text("그때는 이렇게 지나갔어", style = OdType.caption1, color = c.ink2)
            }
        }
        days.forEachIndexed { i, e ->
            if (i > 0) Divider()
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onOpen(e.id) }.padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                LightOrb(LightMixer.mix(e.emotions), 26.dp, Modifier.padding(top = 2.dp))
                Column(Modifier.weight(1f)) {
                    Text(whenLabel(LocalDate.parse(e.date), today), style = OdType.caption2.copy(fontWeight = FontWeight.Bold), color = c.ink2)
                    val title = e.title.ifBlank { e.emotions.filter { it.key != EmotionKey.UNSURE }.joinToString(" · ") { it.key.label } }
                    if (title.isNotBlank()) Text(title, style = OdType.body2.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                    if (e.body.isNotBlank()) Text(
                        e.body.trim().replace('\n', ' '), style = OdType.caption1, color = c.ink2, maxLines = 2,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}

/** "2025년 10월 3일 · 한 달 전" */
internal fun whenLabel(d: LocalDate, today: LocalDate): String {
    val days = ChronoUnit.DAYS.between(d, today)
    val months = ChronoUnit.MONTHS.between(d, today)
    val ago = when {
        days <= 0 -> "오늘"
        days == 1L -> "어제"
        days < 7 -> "${days}일 전"
        days < 30 -> "${days / 7}주 전"
        months < 12 -> if (months <= 1) "한 달 전" else "${months}달 전"
        else -> "${months / 12}년 전"
    }
    val date = if (d.year == today.year) "${d.monthValue}월 ${d.dayOfMonth}일" else "${d.year}년 ${d.monthValue}월 ${d.dayOfMonth}일"
    return "$date · $ago"
}
