## 💡 AI 일기장

AI 가 일기 내용을 한 줄로 요약해주고, 그 날의 감정 이모티콘을 추천해줍니다.
이모티콘과 같이 일기를 기록할 수 있고, 지금까지 기록한 감정들을 월별로 통계내어 확인할 수도 있어요!

<br><br>

## 🛠 설계

- 아키텍처 : Clean Architecture
- 디자인 패턴 : MVI
- 개발 방식 : TDD
- ui : Compose
- di : Koin
- db : Room, DataStore
- async : Coroutine, WorkManager
- test : Junit, MokK

<img src="https://github.com/JungWooGeon/Diary/assets/61993128/81dcaa7c-4aee-4a42-898d-fa0ead18df41" width="593" height="653"/>

<br><br>

## 📷 화면

<img src="https://github.com/JungWooGeon/Diary/assets/61993128/d61070c0-04cd-42b8-b63d-66a6699226b3" width="360" height="760"/> <img src="https://github.com/JungWooGeon/Diary/assets/61993128/841eb46c-9efb-4ef8-ac39-c64bd625d26f" width="360" height="760" />
<img src="https://github.com/JungWooGeon/Diary/assets/61993128/df020259-56fb-4012-a38e-78084405def8" width="360" height="760"/> <img src="https://github.com/JungWooGeon/Diary/assets/61993128/00928480-630b-4a7b-863d-0fbf0fab59c1" width="360" height="760" />

<br><br>

## ⭐️ 기능

일기 쓰기 / 수정
  - 이모티콘(기분, 감정 등)
  - 내용 (이미지 포함)
  - ** 음성 녹음
  - ** 이모티콘, 일기 내용 요약(한 줄 요약) AI 

일기 보기
  - 달력으로 보기
  - 타임라인으로 보기

디자인 기능
  - 일기장 테마 설정 (분홍, 검정 등)
  - 다크모드 설정
  - 여러 폰트 설정

다양한 기능
  - ** 기분 통계 (기존 감정 통계 어플리케이션 참고)
  - ** 일기장 잠금 기능
  - 알림 기능

추가 사항
  - 위젯 기능
  - 한 주의 시작 요일 설정 (일요일 / 월요일)

<br><br>

## 🧩 구조

```bash
│── di
│   ├── databaseModule.kt
│   ├── repositoryModule.kt
│   ├── useCaseModule.kt
│   └── viewModelModule.kt
│
│── presentation
│   ├── ui
│   │   └── theme
│   │       ├── Color.kt
│   │       ├── Theme.kt
│   │       └── Type.kt
│   ├── state
│   │   ├── MainState.kt
│   │   ├── AddDiaryState.kt
│   │   ├── GenerateImageState.kt
│   │   ├── TimelineState.kt
│   │   └── SettingsState.kt
│   ├── view
│   │   ├── activity
│   │   │   ├── MainActivity.kt
│   │   │   ├── AddDiaryActivity.kt
│   │   │   ├── GenerateImageActivity.kt
│   │   │   ├── ScreenLockActivity.kt
│   │   │   └── LicenseActivity.kt
│   │   ├── composable
│   │   │   └── ...
│   │   └── screen
│   │       ├── MainScreen.kt
│   │       ├── TimelineScreen.kt
│   │       ├── CalendarScreen.kt
│   │       ├── AddDiaryScreen.kt
│   │       ├── AnalysisScreen.kt
│   │       ├── SettingsScreen.kt
│   │       ├── LicenseScreen.kt
│   │       └── Constants.kt
│   ├── viewmodel
│   │   ├── MainViewModel.kt
│   │   ├── AddDiaryViewModel.kt
│   │   ├── GenerateImageViewModel.kt
│   │   ├── TimelineViewModel.kt
│   │   └── SettingsViewModel.kt
│   └── intent
│       ├── MainIntent.kt
│       ├── AddDiaryIntent.kt
│       ├── GenerateImageIntent.kt
│       ├── TimelineIntent.kt
│       └── SettingsIntent.kt
│
├── domain
│   ├── diary
│   │   ├── GetDiariesByMonthUseCase.kt
│   │   ├── AddDiaryUseCase.kt
│   │   ├── UpdateDiaryUseCase.kt
│   │   └── DeleteDiaryUseCase.kt
│   ├── upload
│   │   ├── RecordUseCase.kt
│   │   └── UploadImageUseCase.kt
│   └── settings
│       ├── font
│       │   ├── GetAllFontUseCase.kt
│       │   ├── GetCurrentFontUseCase.kt
│       │   └── UpdateCurrentFontUseCase.kt
│       ├── darkmode
│       │   ├── GetCurrentDarkModeUseCase.kt
│       │   └── UpdateCurrentDarkModeUseCase.kt
│       ├── theme
│       │   ├── GetAllThemeUseCase.kt
│       │   ├── GetCurrentThemeUseCase.kt
│       │   └── UpdateCurrentThemeUseCase.kt
│       ├── notification
│       │   ├── GetCurrentNotificationUseCase.kt
│       │   ├── UpdateCurrentNotificationUseCase.kt
│       │   └── NotifyUseCase.kt
│       ├── screenlock
│       │   ├── CompareCurrentLockPasswordUseCase.kt
│       │   └── UpdateCurrentLockPasswordUseCase.kt
│       ├── startdate
│       │   ├── GetCurrentStartDateUseCase.kt
│       │   └── UpdateCurrentStartDateUseCase.kt
│       └── backup
│           ├── BackupDiaryUseCase.kt
│           └── RestoreDiaryUseCase.kt
│
├── data
│   ├── db
│   │   ├── diary
│   │   │   ├── DiaryDao.kt
│   │   │   └── DiaryDataBase.kt
│   │   └── datastore
│   │       └── DataStoreManager.kt
│   ├── entity
│   │   ├── Diary.kt
│   │   ├── Theme.kt
│   │   ├── Notification.kt
│   │   ├── ScreenLock.kt
│   │   └── Startdate.kt
│   └── repository
│       ├── diary
│       │   ├── DiaryRepository
│       │   └── DiaryRepositoryImpl.kt
│       ├── upload
│       │   ├── UploadRepository
│       │   └── UploadRepositoryImpl.kt
│       └── settings
│           ├── SettingsRepository
│           └── SettingsRepositoryImpl.kt
│   
└── DiaryApplication.kt
```
