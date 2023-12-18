package com.pass.presentation.viewmodel

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
import com.pass.presentation.view.screen.Constants
import com.simform.ssjetpackcomposeprogressbuttonlibrary.SSButtonState
import kotlinx.coroutines.Dispatchers
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

            is AddDiaryIntent.UpdateDiary -> {
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

            is AddDiaryIntent.SummaryContent -> {
                _titleTextState.value = ""
                _submitButtonState.value = SSButtonState.LOADING

                viewModelScope.launch(Dispatchers.Main) {
                    try {
                        withContext(Dispatchers.IO) {
                            summaryDiaryUseCase(intent.content)
                        }.collect { summary ->
                            if (summary == "") {
                                _submitButtonState.value = SSButtonState.FAILIURE
                            } else {
                                _submitButtonState.value = SSButtonState.SUCCESS
                                _titleTextState.value = summary
                            }

                            _submitButtonState.value = SSButtonState.IDLE
                        }
                    } catch (e: Exception) {
                        _submitButtonState.value = SSButtonState.FAILIURE
                        _submitButtonState.value = SSButtonState.IDLE
                    }
                }
            }

            is AddDiaryIntent.SelectDate -> { _selectedDateWithLocalDate.value = intent.localDate }
            is AddDiaryIntent.WriteTitle -> { _titleTextState.value = intent.text }
            is AddDiaryIntent.WriteContent -> { _contentTextState.value = intent.text }

            is AddDiaryIntent.DeleteEmoticon -> {
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

            is AddDiaryIntent.UpdateEmoticon -> { _emoticonIdListState.value[intent.index] = intent.emoticonId }
            is AddDiaryIntent.UpdateAddDialog -> { _isDialogAddState.value = intent.isOpen }
            is AddDiaryIntent.UpdateEditDialog -> { _isDialogEditState.value = intent.index }
            is AddDiaryIntent.UpdateDatePickerDialog -> { _isDatePickerOpenState.value = intent.isOpen }
            is AddDiaryIntent.UpdateRecordDialog -> { _isRecordDialogState.value = intent.isOpen }
            is AddDiaryIntent.UpdateDeleteDialog -> { _isDeleteDialogState.value = intent.isOpen }
        }
    }
}