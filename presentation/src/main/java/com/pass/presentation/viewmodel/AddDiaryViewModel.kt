package com.pass.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pass.domain.entity.Diary
import com.pass.domain.usecase.diary.AddDiaryUseCase
import com.pass.domain.usecase.diary.DeleteDiaryUseCase
import com.pass.domain.usecase.diary.SummaryDiaryUseCase
import com.pass.domain.usecase.diary.UpdateDiaryUseCase
import com.pass.domain.usecase.settings.font.GetCurrentTextSizeUseCase
import com.pass.domain.usecase.storage.AddImageWithDiaryUseCase
import com.pass.presentation.intent.AddDiaryIntent
import com.pass.presentation.sideeffect.AddDiarySideEffect
import com.pass.presentation.state.screen.AddDiaryLoadingState
import com.pass.presentation.state.screen.AddDiaryState
import com.pass.presentation.view.screen.Constants
import com.simform.ssjetpackcomposeprogressbuttonlibrary.SSButtonState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.orbitmvi.orbit.Container
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.viewmodel.container
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class AddDiaryViewModel @Inject constructor(
    private val addDiaryUseCase: AddDiaryUseCase,
    private val updateDiaryUseCase: UpdateDiaryUseCase,
    private val deleteDiaryUseCase: DeleteDiaryUseCase,
    private val getCurrentTextSizeUseCase: GetCurrentTextSizeUseCase,
    private val summaryDiaryUseCase: SummaryDiaryUseCase,
    private val addImageWithDiaryUseCase: AddImageWithDiaryUseCase,
    private val deleteImageWithDiaryUseCase: AddImageWithDiaryUseCase
) : ViewModel(), ContainerHost<AddDiaryState, AddDiarySideEffect> {

    override val container: Container<AddDiaryState, AddDiarySideEffect> = container(
        initialState = AddDiaryState()
    )

    // 수정하기 일 때 diary 정보
    private var updateDiary: Diary? = null

    init {
        intent { reduce { state.copy(loading = AddDiaryLoadingState.Loading) } }

        viewModelScope.launch {
            val size = getCurrentTextSizeUseCase().first()
            intent {
                reduce {
                    state.copy(
                        textSizeState = size,
                        loading = AddDiaryLoadingState.Standby
                    )
                }
            }
        }
    }

    fun processIntent(intent: AddDiaryIntent) = intent {
        when (intent) {
            is AddDiaryIntent.Initialize -> {
                // 수정하기 화면일 경우 초기화
                if (intent.diary != null) {
                    updateDiary = intent.diary
                    reduce {
                        state.copy(
                            selectedDateWithLocalDate = LocalDate.of(
                                intent.diary.year.toInt(),
                                intent.diary.month.toInt(),
                                intent.diary.day.toInt()
                            ),
                            emoticonIdList = arrayListOf<Int>().apply {
                                (if (intent.diary.emoticonId1 == null) -1 else intent.diary.emoticonId1)?.let {
                                    add(it)
                                }
                                (if (intent.diary.emoticonId2 == null) -1 else intent.diary.emoticonId2)?.let {
                                    add(it)
                                }
                                (if (intent.diary.emoticonId3 == null) -1 else intent.diary.emoticonId3)?.let {
                                    add(it)
                                }
                            },
                            imageUri = intent.diary.imageUri.orEmpty()
                        )
                    }
                }
            }

            is AddDiaryIntent.AddDiary -> {
                reduce { state.copy(loading = AddDiaryLoadingState.Loading) }

                val addDiary = createDiary(
                    selectedDateWithLocalDate = state.selectedDateWithLocalDate,
                    emoticonIdList = state.emoticonIdList,
                    contentText = intent.contentText,
                    titleText = intent.titleText
                )

                viewModelScope.launch {
                    try {
                        addDiaryUseCase(addDiary)
                        intent { reduce { state.copy(loading = AddDiaryLoadingState.Complete) } }
                    } catch (e: Exception) {
                        intent { reduce { state.copy(loading = AddDiaryLoadingState.Error(e)) } }
                    }
                }
            }

            is AddDiaryIntent.DeleteDiary -> {
                reduce { state.copy(loading = AddDiaryLoadingState.Loading) }
                viewModelScope.launch {
                    try {
                        updateDiary?.let { deleteDiaryUseCase(it) }
                        reduce { state.copy(loading = AddDiaryLoadingState.Complete) }
                    } catch (e: Exception) {
                        reduce { state.copy(loading = AddDiaryLoadingState.Error(e)) }
                    }
                }
            }

            is AddDiaryIntent.SelectDate -> reduce {
                state.copy(selectedDateWithLocalDate = intent.localDate)
            }

            is AddDiaryIntent.DeleteEmoticon -> {
                if (state.emoticonIdList[1] == -1) {
                    postSideEffect(AddDiarySideEffect.Toast("1개까지 삭제할 수 있습니다."))
                    return@intent
                } else {
                    val tmpList = arrayListOf<Int>()
                    when (intent.index) {
                        0 -> {
                            tmpList.add(state.emoticonIdList[1])
                            tmpList.add(state.emoticonIdList[2])
                            tmpList.add(-1)
                        }

                        1 -> {
                            tmpList.add(state.emoticonIdList[0])
                            tmpList.add(state.emoticonIdList[2])
                            tmpList.add(-1)
                        }

                        2 -> {
                            tmpList.add(state.emoticonIdList[0])
                            tmpList.add(state.emoticonIdList[1])
                            tmpList.add(-1)
                        }
                    }

                    reduce { state.copy(emoticonIdList = tmpList) }
                    postSideEffect(AddDiarySideEffect.Toast("삭제되었습니다."))
                }
            }

            is AddDiaryIntent.UpdateAddDialog -> reduce {
                state.copy(isDialogAddState = intent.isOpen)
            }

            is AddDiaryIntent.UpdateEditDialog -> reduce {
                state.copy(isDialogEditState = intent.index)
            }

            is AddDiaryIntent.UpdateDatePickerDialog -> reduce {
                state.copy(isDatePickerOpenState = intent.isOpen)
            }

            is AddDiaryIntent.UpdateRecordDialog -> reduce {
                state.copy(isRecordDialogState = intent.isOpen)
            }

            is AddDiaryIntent.UpdateDeleteDialog -> reduce {
                state.copy(isDeleteDialogState = intent.isOpen)
            }

            is AddDiaryIntent.OnClickSSProgressButton -> {
                // 요약 중에는 버튼 클릭 방지
                if (state.submitButtonState == SSButtonState.LOADING) return@intent

                reduce { state.copy(submitButtonState = SSButtonState.LOADING) }

                if (updateDiary == null) {
                    // 추가하기 화면일 때
                    if (intent.contentText.length < 20) {
                        // 내용이 너무 적을 경우 예외 처리
                        delay(3000)
                        reduce { state.copy(submitButtonState = SSButtonState.FAILURE) }
                        postSideEffect(AddDiarySideEffect.Toast("내용을 요약할 수 없습니다. 더 자세히 적어주세요."))

                        return@intent
                    }

                    // 요약하기
                    postSideEffect(AddDiarySideEffect.ChangeTitle(""))

                    viewModelScope.launch {
                        try {
                            val summary = summaryDiaryUseCase(intent.contentText).first()

                            if (summary == "") {
                                reduce { state.copy(submitButtonState = SSButtonState.FAILURE) }

                                viewModelScope.launch {
                                    delay(3000)
                                    postSideEffect(AddDiarySideEffect.Toast("내용을 요약할 수 없습니다. 더 자세히 적어주세요."))
                                }
                            } else {
                                // _titleTextState.value = summary
                                var tmpTitle = intent.titleText
                                for (char in summary) {
                                    tmpTitle += char
                                    postSideEffect(AddDiarySideEffect.ChangeTitle(tmpTitle))
                                    delay(100)  // 각 글자가 추가되는 시간 간격
                                }
                                reduce { state.copy(submitButtonState = SSButtonState.SUCCESS) }

                                viewModelScope.launch {
                                    delay(3000)
                                    postSideEffect(AddDiarySideEffect.Toast("요약된 내용이 제목에 반영되었어요!"))
                                }

                                viewModelScope.launch {
                                    delay(2000)
                                    // 2초 후 2초간 제목 박스 흔들기 애니메이션 적용 -> 요약하기 성공 시 3초 후 토스트 메시지를 출력하기 때문
                                    reduce { state.copy(shouldShake = true) }
                                    delay(2000)
                                    reduce { state.copy(shouldShake = false) }
                                }
                            }
                        } catch (e: Exception) {
                            reduce { state.copy(submitButtonState = SSButtonState.FAILURE) }

                            viewModelScope.launch {
                                delay(3000)
                                postSideEffect(AddDiarySideEffect.Toast("내용을 요약할 수 없습니다. 더 자세히 적어주세요."))
                            }
                        }
                    }
                } else {
                    // 수정하기 화면일 때
                    reduce { state.copy(loading = AddDiaryLoadingState.Loading) }

                    val deleteDiary = createDiary(
                        id = updateDiary?.id,
                        selectedDateWithLocalDate = state.selectedDateWithLocalDate,
                        emoticonIdList = state.emoticonIdList,
                        contentText = intent.contentText,
                        titleText = intent.titleText
                    )

                    viewModelScope.launch {
                        try {
                            updateDiaryUseCase(deleteDiary)
                            reduce { state.copy(loading = AddDiaryLoadingState.Complete) }
                        } catch (e: Exception) {
                            reduce { state.copy(loading = AddDiaryLoadingState.Error(e)) }
                        }
                    }
                }
            }

            is AddDiaryIntent.OnSelectEmoticon -> {
                if (state.isDialogEditState == Constants.NOT_EDIT_INDEX) {
                    // 이모티콘 추가할 인덱스 탐색
                    var index = -1
                    for (i in 0..< state.emoticonIdList.size) {
                        if (state.emoticonIdList[i] == -1) {
                            index = i
                            break
                        }
                    }

                    if (index != -1) {
                        // 이모티콘 추가
                        reduce {
                            state.copy(
                                emoticonIdList = state.emoticonIdList.mapIndexed { idx, id ->
                                    if (idx == index) intent.emoticonId else id
                                }
                            )
                        }
                    } else {
                        // 3개 이상 추가 불가 -> 예외 처리
                        postSideEffect(AddDiarySideEffect.Toast("3개까지 추가할 수 있습니다."))
                    }

                    reduce { state.copy(isDialogAddState = false) }
                } else {
                    // emoticon 수정
                    reduce {
                        state.copy(
                            emoticonIdList = state.emoticonIdList.mapIndexed { idx, id ->
                                if (idx == state.isDialogEditState) intent.emoticonId else id
                            },
                            isDialogEditState = Constants.NOT_EDIT_INDEX // emoticon 수정 다이얼로그 닫기 상태로 수정
                        )
                    }
                }
            }

            is AddDiaryIntent.OnSelectImageUri -> reduce {
                state.copy(imageUri = intent.imageUri.toString())
            }
        }
    }
}

private fun createDiary(
    id: Int? = null,
    selectedDateWithLocalDate: LocalDate,
    emoticonIdList: List<Int>,
    contentText: String,
    titleText: String
): Diary {
    var emoticonId1: Int? = null
    var emoticonId2: Int? = null
    var emoticonId3: Int? = null

    if (emoticonIdList[0] != -1) emoticonId1 = emoticonIdList[0]
    if (emoticonIdList[1] != -1) emoticonId2 = emoticonIdList[1]
    if (emoticonIdList[2] != -1) emoticonId3 = emoticonIdList[2]

    return Diary(
        id = id,
        year = selectedDateWithLocalDate.year.toString(),
        month = selectedDateWithLocalDate.monthValue.toString(),
        day = selectedDateWithLocalDate.dayOfMonth.toString(),
        dayOfWeek = Constants.DAY_OF_WEEK_TO_KOREAN[selectedDateWithLocalDate.dayOfWeek.toString()]!!,
        emoticonId1 = emoticonId1,
        emoticonId2 = emoticonId2,
        emoticonId3 = emoticonId3,
        imageUri = null,
        content = contentText,
        title = titleText
    )
}