package com.pass.presentation.viewmodel

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pass.domain.model.Diary
import com.pass.domain.usecase.diary.AddDiaryUseCase
import com.pass.domain.usecase.diary.DeleteDiaryUseCase
import com.pass.domain.usecase.diary.SummaryDiaryUseCase
import com.pass.domain.usecase.diary.UpdateDiaryUseCase
import com.pass.domain.usecase.settings.font.GetCurrentTextSizeUseCase
import com.pass.presentation.intent.AddDiaryIntent
import com.pass.presentation.state.AddDiaryState
import com.pass.presentation.state.WorkState
import com.pass.presentation.view.screen.Constants
import com.simform.ssjetpackcomposeprogressbuttonlibrary.SSButtonState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

class AddDiaryViewModel(
    private val addDiaryUseCase: AddDiaryUseCase,
    private val updateDiaryUseCase: UpdateDiaryUseCase,
    private val deleteDiaryUseCase: DeleteDiaryUseCase,
    private val getCurrentTextSizeUseCase: GetCurrentTextSizeUseCase,
    private val summaryDiaryUseCase: SummaryDiaryUseCase
) : ViewModel() {

    // 화면 상태 (loading, error, standby)
    private val _addDiaryState = MutableStateFlow<AddDiaryState>(AddDiaryState.Loading)
    val addDiaryState: StateFlow<AddDiaryState> = _addDiaryState

    // 텍스트 크기
    private val _testSizeState = MutableStateFlow(13f)
    val textSizeState: StateFlow<Float> = _testSizeState

    // 일기 제목
    private val _titleTextState = MutableStateFlow("")
    val titleTextState: StateFlow<String> = _titleTextState

    // 일기 내용
    private val _contentTextState = MutableStateFlow("")
    val contentTextState: StateFlow<String> = _contentTextState

    // 선택한 날짜
    private val _selectedDateWithLocalDate = MutableStateFlow<LocalDate>(
        LocalDate.of(
            LocalDate.now().year,
            LocalDate.now().monthValue,
            LocalDate.now().dayOfMonth
        )
    )
    val selectedDateWithLocalDate: StateFlow<LocalDate> = _selectedDateWithLocalDate

    // '요약하기' 버튼
    private val _submitButtonState = MutableStateFlow(SSButtonState.IDLE)
    val submitButtonState: StateFlow<SSButtonState> = _submitButtonState

    // 이모티콘 id 리스트
    private val _emoticonIdListState = MutableStateFlow(arrayListOf(Constants.EMOTICON_RAW_ID_LIST[0], -1, -1))
    val emoticonIdListState: StateFlow<ArrayList<Int>> = _emoticonIdListState

    // 이모티콘 추가 다이얼로그
    private val _isDialogAddState = MutableStateFlow(false)
    val isDialogAddState: StateFlow<Boolean> = _isDialogAddState

    // 이모티콘 수정 다이얼로그
    private val _isDialogEditState = MutableStateFlow(Constants.NOT_EDIT_INDEX)
    val isDialogEditState: StateFlow<Int> = _isDialogEditState

    // 날짜 선택 다이얼로그
    private val _isDatePickerOpenState = MutableStateFlow(false)
    val isDatePickerOpenState: StateFlow<Boolean> = _isDatePickerOpenState

    // 녹음 다이얼로그
    private val _isRecordDialogState = MutableStateFlow(false)
    val isRecordDialogState: StateFlow<Boolean> = _isRecordDialogState

    // 삭제 다이얼로그
    private val _isDeleteDialogState = MutableStateFlow(false)
    val isDeleteDialogState: StateFlow<Boolean> = _isDeleteDialogState

    // 수정하기 일 때 diary 정보
    private var updateDiary: Diary? = null

    // 삭제 작업 완료 여부
    private val _onCompleteDeleteEmotionState: MutableStateFlow<WorkState> = MutableStateFlow(WorkState.Standby)
    val onCompleteDeleteEmotionState: StateFlow<WorkState> = _onCompleteDeleteEmotionState

    // 이모티콘 추가 예외 처리
    private val _addEmoticonErrorState = MutableStateFlow(false)
    val addEmoticonErrorState: StateFlow<Boolean> = _addEmoticonErrorState

    private val _shouldShake = MutableStateFlow(false)
    val shouldShake: StateFlow<Boolean> = _shouldShake

    init {
        _addDiaryState.value = AddDiaryState.Loading

        viewModelScope.launch(Dispatchers.Main) {
            withContext(Dispatchers.IO) {
                getCurrentTextSizeUseCase()
            }.collect { size ->
                _testSizeState.value = size
                _addDiaryState.value = AddDiaryState.Standby
            }
        }
    }

    fun processIntent(intent: AddDiaryIntent) {
        when (intent) {
            is AddDiaryIntent.Initialize -> {
                // 수정하기 화면일 경우 초기화
                if (intent.diary != null) {
                    updateDiary = intent.diary
                    _selectedDateWithLocalDate.value = LocalDate.of(intent.diary.year.toInt(), intent.diary.month.toInt(), intent.diary.day.toInt())
                    _titleTextState.value = intent.diary.title
                    _contentTextState.value = intent.diary.content

                    _emoticonIdListState.value = arrayListOf<Int>().apply {
                        (if (intent.diary.emoticonId1 == null) -1 else intent.diary.emoticonId1)?.let { add(it) }
                        (if (intent.diary.emoticonId2 == null) -1 else intent.diary.emoticonId2)?.let { add(it) }
                        (if (intent.diary.emoticonId3 == null) -1 else intent.diary.emoticonId3)?.let { add(it) }
                    }
                }
            }

            is AddDiaryIntent.AddDiary -> {
                _addDiaryState.value = AddDiaryState.Loading

                var emoticonId1: Int? = null
                var emoticonId2: Int? = null
                var emoticonId3: Int? = null

                if (emoticonIdListState.value[0] != -1) emoticonId1 = emoticonIdListState.value[0]
                if (emoticonIdListState.value[1] != -1) emoticonId2 = emoticonIdListState.value[1]
                if (emoticonIdListState.value[2] != -1) emoticonId3 = emoticonIdListState.value[2]

                val addDiary = Diary(
                    id = null,
                    year = selectedDateWithLocalDate.value.year.toString(),
                    month = selectedDateWithLocalDate.value.monthValue.toString(),
                    day = selectedDateWithLocalDate.value.dayOfMonth.toString(),
                    dayOfWeek = Constants.DAY_OF_WEEK_TO_KOREAN[selectedDateWithLocalDate.value.dayOfWeek.toString()]!!,
                    emoticonId1 = emoticonId1,
                    emoticonId2 = emoticonId2,
                    emoticonId3 = emoticonId3,
                    imageUri = null,
                    content = contentTextState.value,
                    title = titleTextState.value
                )

                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        addDiaryUseCase(addDiary)
                        withContext(Dispatchers.Main) {
                            _addDiaryState.value = AddDiaryState.Complete
                        }
                    } catch (e: Exception) {
                        _addDiaryState.value = AddDiaryState.Error(e)
                    }
                }
            }

            is AddDiaryIntent.DeleteDiary -> {
                _addDiaryState.value = AddDiaryState.Loading
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        updateDiary?.let { deleteDiaryUseCase(it) }
                        withContext(Dispatchers.Main) {
                            _addDiaryState.value = AddDiaryState.Complete
                        }
                    } catch (e: Exception) {
                        _addDiaryState.value = AddDiaryState.Error(e)
                    }
                }
            }

            is AddDiaryIntent.SelectDate -> { _selectedDateWithLocalDate.value = intent.localDate }
            is AddDiaryIntent.WriteTitle -> { _titleTextState.value = intent.text }
            is AddDiaryIntent.WriteContent -> { _contentTextState.value = intent.text }

            is AddDiaryIntent.DeleteEmoticon -> {
                if (emoticonIdListState.value[1] == -1) {
                    _onCompleteDeleteEmotionState.value = WorkState.Fail
                    return
                } else {
                    _onCompleteDeleteEmotionState.value = WorkState.Success
                }

                val tmpList = arrayListOf<Int>()
                when (intent.index) {
                    0 -> {
                        tmpList.add(emoticonIdListState.value[1])
                        tmpList.add(emoticonIdListState.value[2])
                        tmpList.add(-1)
                    }

                    1 -> {
                        tmpList.add(emoticonIdListState.value[0])
                        tmpList.add(emoticonIdListState.value[2])
                        tmpList.add(-1)
                    }

                    2 -> {
                        tmpList.add(emoticonIdListState.value[0])
                        tmpList.add(emoticonIdListState.value[1])
                        tmpList.add(-1)
                    }
                }

                _emoticonIdListState.value = tmpList
            }

            is AddDiaryIntent.UpdateAddDialog -> { _isDialogAddState.value = intent.isOpen }
            is AddDiaryIntent.UpdateEditDialog -> { _isDialogEditState.value = intent.index }
            is AddDiaryIntent.UpdateDatePickerDialog -> { _isDatePickerOpenState.value = intent.isOpen }
            is AddDiaryIntent.UpdateRecordDialog -> { _isRecordDialogState.value = intent.isOpen }
            is AddDiaryIntent.UpdateDeleteDialog -> { _isDeleteDialogState.value = intent.isOpen }
            is AddDiaryIntent.OnCompleteShowToastDeleteEmoticon -> { _onCompleteDeleteEmotionState.value = WorkState.Standby }
            is AddDiaryIntent.OnClickSSProgressButton -> {
                // 요약 중에는 버튼 클릭 방지
                if (submitButtonState.value != SSButtonState.IDLE && submitButtonState.value != SSButtonState.SUCCESS) return

                if (updateDiary == null) {
                    // 추가하기 화면일 때
                    if (contentTextState.value.length < 20) {
                        // 내용이 너무 적을 경우 예외 처리
                        _submitButtonState.value = SSButtonState.FAILIURE
                        return
                    }

                    // 요약하기
                    _titleTextState.value = ""
                    _submitButtonState.value = SSButtonState.LOADING

                    viewModelScope.launch(Dispatchers.Main) {
                        try {
                            withContext(Dispatchers.IO) {
                                summaryDiaryUseCase(contentTextState.value)
                            }.collect { summary ->
                                if (summary == "") {
                                    _submitButtonState.value = SSButtonState.FAILIURE
                                } else {
                                    // _titleTextState.value = summary
                                    for (char in summary) {
                                        _titleTextState.value += char
                                        delay(100)  // 각 글자가 추가되는 시간 간격
                                    }
                                    _submitButtonState.value = SSButtonState.SUCCESS

                                    delay(2000)
                                    // 2초 후 2초간 제목 박스 흔들기 애니메이션 적용 -> 요약하기 성공 시 3초 후 토스트 메시지를 출력하기 때문
                                    _shouldShake.value = true
                                    delay(2000)
                                    _shouldShake.value = false
                                }
                            }
                        } catch (e: Exception) {
                            _submitButtonState.value = SSButtonState.FAILIURE
                        }
                    }
                } else {
                    // 수정하기 화면일 때
                    _addDiaryState.value = AddDiaryState.Loading

                    // 수정하기일 때
                    var emoticonId1: Int? = null
                    var emoticonId2: Int? = null
                    var emoticonId3: Int? = null

                    if (emoticonIdListState.value[0] != -1) emoticonId1 = emoticonIdListState.value[0]
                    if (emoticonIdListState.value[1] != -1) emoticonId2 = emoticonIdListState.value[1]
                    if (emoticonIdListState.value[2] != -1) emoticonId3 = emoticonIdListState.value[2]

                    updateDiary?.year = selectedDateWithLocalDate.value.year.toString()
                    updateDiary?.month = selectedDateWithLocalDate.value.monthValue.toString()
                    updateDiary?.day = selectedDateWithLocalDate.value.dayOfMonth.toString()
                    updateDiary?.dayOfWeek = Constants.DAY_OF_WEEK_TO_KOREAN[selectedDateWithLocalDate.value.dayOfWeek.toString()]!!
                    updateDiary?.emoticonId1 = emoticonId1
                    updateDiary?.emoticonId2 = emoticonId2
                    updateDiary?.emoticonId3 = emoticonId3
                    updateDiary?.imageUri = null
                    updateDiary?.content = contentTextState.value
                    updateDiary?.title = titleTextState.value

                    viewModelScope.launch(Dispatchers.IO) {
                        try {
                            updateDiary?.let { updateDiaryUseCase(it) }
                            withContext(Dispatchers.Main) {
                                _addDiaryState.value = AddDiaryState.Complete
                            }
                        } catch (e: Exception) {
                            _addDiaryState.value = AddDiaryState.Error(e)
                        }
                    }
                }
            }

            is AddDiaryIntent.OnSelectEmoticon -> {
                if (isDialogEditState.value == Constants.NOT_EDIT_INDEX) {
                    // 이모티콘 추가할 인덱스 탐색
                    var index = -1
                    for (i in 0..< emoticonIdListState.value.size) {
                        if (emoticonIdListState.value[i] == -1) {
                            index = i
                            break
                        }
                    }

                    if (index != -1) {
                        // 이모티콘 추가
                        _emoticonIdListState.value[index] = intent.emoticonId
                    } else {
                        // 3개 이상 추가 불가 -> 예외 처리
                        _addEmoticonErrorState.value = true
                    }

                    _isDialogAddState.value = false
                } else {
                    // emoticon 수정
                    _emoticonIdListState.value[isDialogEditState.value] = intent.emoticonId

                    // emoticon 수정 다이얼로그 닫기 상태로 수정
                    _isDialogEditState.value = Constants.NOT_EDIT_INDEX
                }
            }

            is AddDiaryIntent.OnCompleteShowToastAddEmoticon -> { _addEmoticonErrorState.value = false }

            is AddDiaryIntent.SetIDleSSButtonState -> { _submitButtonState.value = SSButtonState.IDLE }
        }
    }
}