<img width="2719" alt="알람 플레이리스트 아키텍처" src="https://github.com/JungWooGeon/Diary/assets/61993128/737ad8b8-6acf-4fcd-948b-33617c723031"><img src="https://github.com/JungWooGeon/Diary/assets/61993128/66672f41-0d4c-41aa-b295-9c4568975f78" width="300" height="300"/>

<br>

## 💡 AI 일기장
AI를 사용하여 편리하고 똑똑하게 작성하는 감정 일기장 앱

<br><br>

## 🔥 Google Play
https://play.google.com/store/apps/details?id=com.pass.diary

<br><br>

## 🛠 사용
- android, android studio
- kotlin
- MVI, Clean Architecture, koin, Room, DataStore, Retrofit, Coroutine, Junit, MockK
- Google STT (SpeechRecognizer), Naver CLOVA Summary API, Google OAuth(Firebase Authentication), Google Drive API

<br><br>

## 🚀 설계
<img src="https://github.com/JungWooGeon/Diary/assets/61993128/81dcaa7c-4aee-4a42-898d-fa0ead18df41" width="593" height="653"/>
<img src="https://github.com/JungWooGeon/Diary/assets/61993128/abb4f48c-bf1c-4ba4-8a55-3d008031443c" width="600" height="600"/>

<br><br>

## ⭐️ 기능
- 일기 쓰기 / 수정
    - **음성으로 내용 입력 (AI)**
    - **일기 내용 요약하여 제목으로 작성하기 (AI)**
    - 이모티콘(기분, 감정 등)
    - 내용
    - 이미지 (미구현)
- 일기 조회 (월별)
    - 달력으로 보기
    - 타임라인으로 보기
- 디자인 기능
    - 일기장 테마 설정 (분홍, 검정 등) (미구현)
    - 다크모드 설정 (미구현)
    - 여러 폰트 설정
- 다양한 기능
    - **감정 통계**
    - 일기장 잠금 기능 (미구현)
    - 알림 기능 (미구현)
- 추가 사항
    - 위젯 기능 (미구현)
    - 한 주의 시작 요일 설정 (일요일 / 월요일) (미구현)

<br><br>

## 📷 화면

<img src="https://github.com/JungWooGeon/Diary/assets/61993128/3727d47e-8b68-42f4-9edc-fd2f34e60fa3" width="400" height="760"/> <img src="https://github.com/JungWooGeon/Diary/assets/61993128/de0da521-2abe-41fd-a145-e0f384a915ef" width="400" height="760" />
<img src="https://github.com/JungWooGeon/Diary/assets/61993128/0928347c-56ea-4f2a-82ed-9e421de3e6dd" width="400" height="760"/> <img src="https://github.com/JungWooGeon/Diary/assets/61993128/c422b093-419d-44cd-a40a-aed882b178e7" width="400" height="760" />
<img src="https://github.com/JungWooGeon/Diary/assets/61993128/8c0f39e2-2c67-49eb-a1af-8f1fe332ab3d" width="400" height="760" />
      
<br><br>

## 🧩 코드 구조

```bash
│── presentation
│   ├── ui
│   │   └── theme
│   │       ├── Color.kt
│   │       ├── Theme.kt
│   │       └── Type.kt
│   ├── state
│   │   ├── AddDiaryState.kt
│   │   ├── LoginState.kt
│   │   ├── MainState.kt
│   │   ├── SettingState.kt
│   │   ├── TimelineState.kt
│   │   └── WorkState.kt
│   ├── view
│   │   ├── activity
│   │   │   ├── MainActivity.kt
│   │   │   └── AddDiaryActivity.kt
│   │   ├── composable
│   │   │   └── ...
│   │   └── screen
│   │       ├── MainScreen.kt
│   │       ├── TimelineScreen.kt
│   │       ├── CalendarScreen.kt
│   │       ├── AddDiaryScreen.kt
│   │       ├── AnalysisScreen.kt
│   │       ├── SettingsScreen.kt
│   │       └── Constants.kt
│   ├── viewmodel
│   │   ├── AddDiaryViewModel.kt
│   │   ├── AnalysisViewModel.kt
│   │   ├── CalendarViewModel.kt
│   │   ├── SettingsViewModel.kt
│   │   ├── ThemeViewModel.kt
│   │   └── TimelineViewModel.kt
│   ├── intent
│   │   ├── AddDiaryIntent.kt
│   │   ├── AnalysisIntent.kt
│   │   ├── CalendarIntent.kt
│   │   ├── SettingsIntent.kt
│   │   └── TimelineIntent.kt
│   ├── di
│   │   ├── databaseModule.kt
│   │   ├── repositoryModule.kt
│   │   ├── useCaseModule.kt
│   │   └── viewModelModule.kt
│   └── DiaryApplication.kt
│
├── domain
│   ├── model
│   │   └── Diary.kt
│   ├── repository
│   │   ├── diary
│   │   │   └── DiaryRepository.kt
│   │   ├── google
│   │   │   └── GoogleManagerRepository.kt
│   │   └── settings
│   │       └── SettingsRepository.kt
│   └── usecase
│       ├── diary
│       │   ├── AddDiaryUseCase.kt
│       │   ├── DeleteDiaryUseCase.kt
│       │   ├── GetAllDiariesUseCase.kt
│       │   ├── GetDiariesByMonthUseCase.kt
│       │   ├── SummaryDiaryUseCase.kt
│       │   └── UpdateDiaryUseCase.kt
│       ├── google
│       │   ├── BackupDiariesToGoogleDriveUseCase.kt
│       │   ├── isLoggedInUseCase.kt
│       │   ├── LogInForGoogleUseCase.kt
│       │   ├── LogOutForGoogleUseCase.kt
│       │   └── RestoreDiariesForGoogleDriveUseCase.kt
│       └── settings
│           └── font
│               ├── GetCurrentFontUseCase.kt
│               ├── GetCurrentTextSizeUseCase.kt
│               ├── UpdateCurrentFontUseCase.kt
│               └── UpdateCurrentTextSizeUseCase.kt
│
└── data
    ├── db
    │   └── diary
    │       ├── DiaryDao.kt
    │       └── DiaryDataBase.kt
    ├── remote
    │   ├── dto
    │   │   ├── SummaryRequest.kt
    │   │   └── SummaryResponse.kt
    │   └── service
    │       └── SummaryService.kt
    └── repository
        ├── diary
        │   └── DiaryRepositoryImpl.kt
        ├── google
        │   └── GoogleManagerRepositoryImpl.kt
        └── settings
            └── SettingsRepositoryImpl.kt
```
