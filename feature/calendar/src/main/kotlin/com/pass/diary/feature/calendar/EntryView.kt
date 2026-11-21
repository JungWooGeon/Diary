package com.pass.diary.feature.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.pass.diary.core.data.EntryRepository
import com.pass.diary.core.data.db.ReplyDao
import com.pass.diary.core.designsystem.IconBtn
import com.pass.diary.core.designsystem.Od
import com.pass.diary.core.designsystem.OdIcon
import com.pass.diary.core.designsystem.OdTopBar
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.Serializable
import java.time.LocalDate
import javax.inject.Inject

/** 일기 보기: one record read in full (sentence marks, 온이's reply, 비슷했던 날). Editing is one tap away. */
@Serializable data class EntryViewRoute(val entryId: String)

@HiltViewModel
class EntryViewModel @Inject constructor(savedStateHandle: SavedStateHandle, repository: EntryRepository, replies: ReplyDao) : ViewModel() {
    private val id = savedStateHandle.toRoute<EntryViewRoute>().entryId
    val state = combine(repository.observe(id), replies.observe(id)) { e, r -> e?.takeIf { !it.deleted } to r?.text }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
fun EntryViewScreen(onBack: () -> Unit, onEdit: (String) -> Unit, onRead: (String) -> Unit, vm: EntryViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().background(Od.c.bg).statusBarsPadding()) {
        OdTopBar(left = { IconBtn(OdIcon.Back, onBack, "뒤로") })
        val (e, reply) = s ?: return@Column
        if (e == null) return@Column // trashed meanwhile
        Box(Modifier.weight(1f)) {
            DayDetail(
                LocalDate.parse(e.date), listOf(e), reply?.let { mapOf(e.id to it) }.orEmpty(), onEdit, onWriteOn = {},
                single = true, onOpenSimilar = onRead,
            )
        }
    }
}
