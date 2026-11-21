package com.pass.diary.feature.calendar

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.pass.diary.core.data.DiaryInsights
import com.pass.diary.core.data.SettingsRepository
import com.pass.diary.core.designsystem.AiMark
import com.pass.diary.core.designsystem.Od
import com.pass.diary.core.designsystem.OdColors
import com.pass.diary.core.designsystem.OdType
import com.pass.diary.core.designsystem.SimilarDays
import com.pass.diary.core.designsystem.glowColor
import com.pass.diary.core.model.Entry
import com.pass.diary.core.model.SentenceMark
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** E3 / E4 for whatever screen reads an entry (calendar pane, search pane, 일기 보기). One per back-stack entry. */
@HiltViewModel
class InsightsViewModel @Inject constructor(private val insights: DiaryInsights, private val settings: SettingsRepository) : ViewModel() {
    val prefs = settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    suspend fun marks(body: String) = runCatching { insights.sentenceMarks(body) }.getOrDefault(emptyList())
    suspend fun similar(id: String) = runCatching { insights.similarDays(id) }.getOrDefault(emptyList())
    fun hideHighlights() = viewModelScope.launch { settings.setAiHighlight(false) }
}

/** Body text with E3 marks: a soft underline in each strongly felt sentence's light, fading in once they're ready. */
@Composable
internal fun MarkedBody(text: String, style: TextStyle, modifier: Modifier = Modifier, vm: InsightsViewModel = hiltViewModel()) {
    val c = Od.c
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    val on = prefs?.aiHighlight == true
    val marks by produceState(emptyList<SentenceMark>(), text, on) { value = if (on) vm.marks(text) else emptyList() }
    val shown by animateFloatAsState(if (marks.isEmpty()) 0f else 1f, tween(700), label = "marks")
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        text, style = style, onTextLayout = { layout = it },
        modifier = modifier.drawBehind {
            val l = layout ?: return@drawBehind
            if (shown == 0f) return@drawBehind
            val h = 5.dp.toPx(); val r = CornerRadius(h / 2)
            for (m in marks) {
                if (m.end > l.layoutInput.text.length) continue
                val color = (m.emotion.group?.glowColor ?: OdColors.OffGlow).copy(alpha = (if (c.dark) 0.5f else 0.42f) * shown)
                for (line in l.getLineForOffset(m.start)..l.getLineForOffset(maxOf(m.start, m.end - 1))) {
                    val left = if (line == l.getLineForOffset(m.start)) l.getHorizontalPosition(m.start, true) else l.getLineLeft(line)
                    val right = if (line == l.getLineForOffset(m.end - 1)) l.getHorizontalPosition(m.end, true) else l.getLineRight(line)
                    val y = l.getLineBaseline(line) + 1.dp.toPx()
                    drawRoundRect(color, Offset(minOf(left, right), y - h * 0.4f), Size(kotlin.math.abs(right - left), h), r)
                }
            }
        },
    )
    if (marks.isNotEmpty()) Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        AiMark()
        Text("감정이 진한 문장에 그 빛으로 밑줄을 그었어요", style = OdType.caption2, color = c.ink2, modifier = Modifier.weight(1f, fill = false))
        Text("끄기", style = OdType.caption2.copy(fontWeight = FontWeight.SemiBold), color = c.ink2, modifier = Modifier.clickable { vm.hideHighlights() }.padding(4.dp))
    }
}

/** E4 under an entry read in full. */
@Composable
internal fun SimilarDaysFor(entry: Entry, onOpen: (String) -> Unit, modifier: Modifier = Modifier, vm: InsightsViewModel = hiltViewModel()) {
    val days by produceState(emptyList<Entry>(), entry.id, entry.body) { value = vm.similar(entry.id) }
    SimilarDays(days, onOpen, modifier)
}

