package com.pass.presentation.state.screen

import androidx.compose.runtime.Immutable
import com.pass.presentation.view.screen.Constants
import com.simform.ssjetpackcomposeprogressbuttonlibrary.SSButtonState
import java.time.LocalDate

@Immutable
data class AddDiaryState(
    // 화면 상태 (loading, error, standby)
    val loading: AddDiaryLoadingState = AddDiaryLoadingState.Loading,

    // 텍스트 크기
    val textSizeState: Float = DEFAULT_TEXT_SIZE,

    // 선택한 날짜
    val selectedDateWithLocalDate: LocalDate =
        LocalDate.of(
            LocalDate.now().year,
            LocalDate.now().monthValue,
            LocalDate.now().dayOfMonth
        ),

    // 이모티콘 id 리스트
    val emoticonIdList: List<Int> = arrayListOf(Constants.EMOTICON_RAW_ID_LIST[0], -1, -1),

    // '요약하기' 버튼
    val submitButtonState: SSButtonState = SSButtonState.IDLE,

    // 날짜 선택 다이얼로그
    val isDatePickerOpenState: Boolean = false,

    // 이모티콘 추가 다이얼로그
    val isDialogAddState: Boolean = false,

    // 이모티콘 수정 다이얼로그
    val isDialogEditState: Int = Constants.NOT_EDIT_INDEX,

    // 삭제 다이얼로그
    val isDeleteDialogState: Boolean = false,

    // 녹음 다이얼로그
    val isRecordDialogState: Boolean = false,

    val shouldShake: Boolean = false,

    val imageUri: String = ""
)

sealed class AddDiaryLoadingState {
    data object Loading : AddDiaryLoadingState()
    data object Complete : AddDiaryLoadingState()
    data object Standby : AddDiaryLoadingState()
    data class Error(val error: Throwable) : AddDiaryLoadingState()
}

private const val DEFAULT_TEXT_SIZE = 13f