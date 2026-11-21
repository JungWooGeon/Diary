package com.pass.diary.feature.report

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.pass.diary.core.data.EntryRepository
import com.pass.diary.core.data.db.ReplyDao
import com.pass.diary.core.designsystem.Dot
import com.pass.diary.core.designsystem.IconBtn
import com.pass.diary.core.designsystem.Od
import com.pass.diary.core.designsystem.OdChip
import com.pass.diary.core.designsystem.OdColors
import com.pass.diary.core.designsystem.OdIcon
import com.pass.diary.core.designsystem.OdTopBar
import com.pass.diary.core.designsystem.OdType
import com.pass.diary.core.designsystem.Oni
import com.pass.diary.core.designsystem.OniExpression
import com.pass.diary.core.designsystem.glowColor
import com.pass.diary.core.designsystem.odCard
import com.pass.diary.core.light.LightMixer
import com.pass.diary.core.model.EmotionGroup
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

data class Letter(val entryId: String, val date: LocalDate, val group: EmotionGroup?, val text: String)

@HiltViewModel
class LettersViewModel @Inject constructor(replies: ReplyDao, repository: EntryRepository) : ViewModel() {
    val letters = combine(replies.observeAll(), repository.all()) { rs, entries ->
        val byId = entries.associateBy { it.id } // deleted entries aren't in [entries], so their replies drop out
        rs.filter { !it.reported }.mapNotNull { r ->
            val e = byId[r.entryId] ?: return@mapNotNull null
            Letter(e.id, LocalDate.parse(e.date), LightMixer.dominantGroup(e.emotions), r.text)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
fun LettersScreen(onBack: () -> Unit, onOpen: (String) -> Unit, vm: LettersViewModel = hiltViewModel()) {
    val c = Od.c
    val all by vm.letters.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    val shown = all.orEmpty().filter { filter == null || it.group?.key == filter }
    LazyColumn(
        Modifier.fillMaxSize().background(c.bg).statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item { OdTopBar(title = "온이 편지함", left = { IconBtn(OdIcon.Back, onBack, "뒤로") }) }
        item {
            Row(Modifier.padding(horizontal = 20.dp).padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Oni(null, OniExpression.LETTER, Modifier.width(76.dp))
                Column {
                    Text("받은 답장 ${all?.size ?: 0}통", style = OdType.title2)
                    Text("온이의 한마디를 모아 뒀어요", style = OdType.caption1, color = c.ink2)
                }
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 16.dp, bottom = 0.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                OdChip("전체", selected = filter == null, outlined = true, height = 32.dp, style = OdType.chip.copy(fontSize = 13.sp)) { filter = null }
                EmotionGroup.entries.forEach { g ->
                    OdChip(g.label, selected = filter == g.key, outlined = true, height = 32.dp, dot = g.glowColor, style = OdType.chip.copy(fontSize = 13.sp)) { filter = g.key }
                }
            }
        }
        if (all != null && shown.isEmpty()) item {
            Text(
                if (filter == null) "아직 받은 답장이 없어요. 일기를 쓰면 온이가 답장을 써 줘요." else "이 빛의 답장은 아직 없어요.",
                style = OdType.body2, color = c.ink2, modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
            )
        }
        items(shown, key = { it.entryId }) { l ->
            val glow = l.group?.glowColor ?: OdColors.OffGlow
            Row(Modifier.padding(horizontal = 20.dp).padding(top = 10.dp).fillMaxWidth().odCard().clickable { onOpen(l.entryId) }.height(IntrinsicSize.Min)) {
                Box(Modifier.width(5.dp).fillMaxHeight().background(glow))
                Column(Modifier.padding(start = 13.dp, end = 18.dp, top = 16.dp, bottom = 16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${l.date.monthValue}월 ${l.date.dayOfMonth}일", style = OdType.caption2.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
                        Dot(glow, 8.dp)
                        Text(l.group?.label ?: "잘 모르겠음", style = OdType.caption2, color = c.ink2, modifier = Modifier.padding(start = 4.dp))
                    }
                    Text(l.text, style = OdType.body2.copy(fontSize = 14.5.sp, lineHeight = 23.sp), modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
        item { Spacer(Modifier.navigationBarsPadding().height(8.dp)) }
    }
}
