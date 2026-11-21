# core:sync — Google 드라이브 appDataFolder 백업·동기화

## 구성

| 파일 | 역할 |
|---|---|
| `DriveDocs.kt` | 드라이브 JSON 형식(`EntryDoc` 등) ↔ `core:model.Entry` 변환. 정본은 `shared/schema/*.schema.json` |
| `DriveApi.kt` | Drive REST v3 일부(OkHttp): files.create(multipart)/update/get/get?alt=media/list(spaces=appDataFolder), changes.getStartPageToken/list. 401이면 토큰 무효화 후 1회 재시도 |
| `SyncEngine.kt` | 내려받기(pageToken, 없으면 전체 목록=복원) → 올리기(md5 재확인) · 충돌(updatedAt 큰 쪽, 진 쪽 30일 보관) · 삭제 표시 · `SyncStatus` Flow |
| `DriveAuth.kt` | Credential Manager(Google 로그인) + AuthorizationClient(`drive.appdata`) |
| `SyncWorker.kt` | WorkManager(네트워크 조건, 지수 백오프 30초~), `SyncScheduler` 호출 지점 |

파일 배치: appDataFolder 안은 **폴더 없이 평평하게** 두고, 파일 `name`을 경로(`entries/2026/11/<id>.json`, `photos/<id>.jpg`, `settings.json`, `entitlement.json`, `manifest.json`)로 쓴다. 파일마다 `appProperties {entityId, kind}`를 달아 이름이 아니라 ID로 찾는다(날짜를 바꾸면 이름이 바뀜). 두 기기가 같은 폴더를 동시에 만들어 중복되는 문제를 원천적으로 피하려는 선택이며, `manifest.schema.json`에 적어 웹도 같은 규칙을 따른다.

`SyncLocalStore`(Room 쪽 구현)는 통합 단계에서 `core:data`가 제공한다 — 필요한 질의는 `DEPS.md`.

## 자동 테스트

`./gradlew :core:sync:testDebugUnitTest` — `FakeDrive`(메모리 드라이브, 변경 로그, 업로드 손상 주입)로 첫 백업, 증분·이름 변경, 빈 기기 복원(사진·설정 포함), 다른 기기 변경 수신, 충돌 양방향, 삭제 표시, 검증 실패 → 재시도, 드라이브에서 지워진 파일 재생성, 같은 일기를 가진 두 번째 기기를 확인한다.

## 실패했을 때 (`FailureScenariosTest`)

원칙: **이 폰의 Room이 원본, 드라이브는 사본.** 드라이브에서 무슨 일이 생겨도 로컬 일기는 지우지 않는다. 로컬 삭제는 우리 앱이 쓴 `deleted: true` 일기 파일(삭제 표시)로만 일어난다.

| 상황 | 동작 |
|---|---|
| 드라이브 꽉 참(403 `storageQuotaExceeded`) | 사진은 건너뛰고 일기 글은 계속 올려 봄 → 글도 거절되면 멈춤. 모두 "올릴 것"으로 남고 워커가 지수 백오프(30초~5시간) 후 재시도, 이후엔 저장·앱 열기·매일 작업이 이어 감. 마이 › 백업: "드라이브 저장 공간이 부족해요" + "공간 확보 안내"(Google One) + "사진 제외하고 일기만 백업" |
| 숨김 앱 데이터 삭제 / 파일 사라짐 | 변경 목록의 삭제·404·다시 목록 받기 → **백업 상태 확인**: sync_state에 있는데 드라이브에 없는 것은 다시 "올릴 것" → 전부 다시 올림. 매니페스트도 다시 만듦 |
| 백업 상태 확인 | 위 경우 + 7일마다(매일 작업에 얹음). 파일 1000개당 목록 호출 1번 |
| 권한 철회·비밀번호 변경(401 두 번, 인증 ApiException) | `NOT_SIGNED_IN`, 워커 중단, "다시 연결" |
| 다른 계정으로 로그인 | `backup.lastAccount`(로그아웃해도 남음)와 다르면 물어봄: 이 폰 일기를 새 계정에도 백업(합치기) / 새 계정의 일기로 바꾸기(이 폰 데이터 지우고 재시작 → 복원) |
| 429·5xx·rate limit 403 | 요청마다 3번까지 1·2·4초+지터, 그다음 워커 백오프 |
| 깨진·모르는 파일 | 건너뜀(로컬 안 덮음). 깨진 매니페스트는 다시 씀 |
| 큰 복원이 10분 제한에 끊김 | 목록 위치(`sync.listStart/listPage`) 저장 → 이어서 |
| 마지막 오류 | `sync.lastError`에 저장 → 앱을 다시 열어도 상태가 보임 |

사진은 장변 1600px·품질 80(수백 KB)이라 multipart(5MB 이하 권장)로 충분해 resumable 업로드는 넣지 않았다. 5MB가 넘는 첨부가 생기면 추가.

## 드라이브 범위 확인 결과

`https://www.googleapis.com/auth/drive.appdata`는 Google 문서의 **"Non-sensitive scopes"(권장)** 표에 있다.
→ 민감·제한 범위가 아니므로 **OAuth 앱 검증(보안 평가 포함)은 필요 없다.**
출처: https://developers.google.com/workspace/drive/api/guides/api-specific-auth ("Non-sensitive scopes … provide the smallest scope of authorization", 표에 `drive.appdata` 포함, 2026-11-21 확인)

다만,
- OAuth 동의 화면을 **"테스트" 상태로 두면** 등록한 테스트 사용자(최대 100명)만 쓸 수 있고 권한이 7일 뒤 만료된다. 출시 전에 **"프로덕션으로 게시"**를 눌러야 한다. 비민감 범위만 쓰면 이때 검증 심사가 없다.
- 동의 화면에 앱 로고를 넣으면 브랜드 확인(무료)을 요구할 수 있다. 로고 없이 이름만 두면 피할 수 있다.

## 사장님이 해야 할 일 (Google Cloud Console)

1. Firebase/Google Cloud 프로젝트(퍼스널컬러와 별도 권장) 선택 → **API 및 서비스 › 라이브러리 › Google Drive API 사용**.
2. **OAuth 동의 화면**(Google 인증 플랫폼): 외부, 앱 이름 "온이 일기", 지원 이메일, 개인정보처리방침 URL, 범위에 `.../auth/drive.appdata` 추가. 개발 중에는 테스트 사용자에 본인 계정 추가.
3. **사용자 인증 정보 › OAuth 클라이언트 ID**
   - Android 유형, 패키지 `com.pass.diary`, SHA-1 **3개 각각**: ① 디버그 키(`./gradlew signingReport`) ② 업로드 키 ③ Play 앱 서명 키(Play Console › 설정 › 앱 서명). 하나라도 빠지면 그 빌드에서 로그인/권한이 `DEVELOPER_ERROR(10)`로 실패한다.
   - **웹 애플리케이션** 유형 1개 — 이 클라이언트 ID를 `DriveAuth.signIn(activity, webClientId)`에 넘긴다(Credential Manager가 요구). 앱에서는 문자열 리소스/BuildConfig로 둔다.
4. 출시 전 동의 화면을 **프로덕션으로 게시**.

## 수동 테스트 (실기기 2대, 실제 OAuth)

1. 기기 A: 마이 › 백업·계정 › 로그인 → 계정 선택 → 드라이브 권한 동의 화면 확인 → 일기 3개(사진 1개 포함) 저장.
2. 1분 안에 "마지막 백업" 시각이 갱신되고 "올릴 것 0"인지 확인. 비행기 모드로 저장 → "백업 안 됨/대기 1" → 비행기 모드 해제 → 자동으로 0.
3. 드라이브 웹 › 설정 › 앱 관리에서 "온이 일기"가 숨김 데이터 크기를 가진 것을 확인(내용은 사용자에게 안 보이는 것이 정상).
4. 기기 B(또는 앱 삭제 후 재설치): 같은 계정으로 로그인 → 일기 3개·사진·설정이 복원되는지.
5. 충돌: A·B 둘 다 비행기 모드 → 같은 일기를 각각 고침(B를 나중에) → A 온라인, 그다음 B 온라인 → 양쪽 모두 B 내용, "다른 기기에서 고친 내용"에 A 내용이 남는지.
6. 삭제: A에서 지움 → B를 열면 사라지는지.
7. 권한 철회: myaccount.google.com › 보안 › 서드 파티 연결에서 앱 접근 삭제 → 앱에서 "지금 백업" → "드라이브 연결이 끊겼어요" + "다시 연결" → 다시 동의하면 이어서 올라가는지.
8. 계정 바꾸기: 로그아웃 후 다른 계정 로그인 → "다른 계정이에요" 선택창 → 각 선택지 결과 확인.
9. 숨김 데이터 삭제: 드라이브 웹 › 설정 › 앱 관리 › 온이 일기 › 숨겨진 앱 데이터 삭제 → 앱에서 "지금 백업" → 일기 그대로, 전부 다시 올라가는지.

## 알려진 한계 (일부러 단순하게 둔 것)

- 내려받기와 올리기 사이 몇 초 안에 다른 기기가 같은 일기를 올리면 충돌 감지 없이 덮어쓴다(일기 앱 사용 패턴상 드묾). 필요하면 올리기 직전 `appProperties.updatedAt` 비교를 추가.
- 삭제 표시 파일은 드라이브에서 지우지 않는다(수 KB). 오래 꺼져 있던 기기도 삭제를 받을 수 있게 하려는 것.
- 진 쪽 보관은 일기 단위 전체 교체(필드 병합 없음).
