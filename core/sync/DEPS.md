# core:sync 의존성 요청 (B → A)

- `implementation("com.google.android.gms:play-services-auth:22.0.0")` — `Identity.getAuthorizationClient`(drive.appdata 권한), `GoogleAuthUtil.clearToken`.
- (이미 있음) okhttp, work-runtime, hilt-work + androidx.hilt compiler, credentials(+play-services), googleid, serialization-json, coroutines-test.
- 앱(`app`)에서 Hilt WorkManager 초기화: `Application`이 `Configuration.Provider` 구현 + `HiltWorkerFactory` 주입, 매니페스트에서 기본 `WorkManagerInitializer` 제거.

## core:data에 필요한 것 (통합 단계, `SyncLocalStore` 구현)
- "올릴 것" 질의: `entry.updatedAt != sync_state.remoteVersion`(또는 sync_state 없음)인 일기, `driveFileId`/sync_state 없는 사진, 설정·이용권 문서.
  `SyncState(fileId, syncedUpdatedAt)` ↔ `sync_state(remoteFileId, remoteVersion)`; `localVersion`은 쓰지 않아도 됨.
- `applyRemoteEntry`: 일기+감정+태그+사진 행을 한 트랜잭션으로 덮어쓰기(삭제 표시 포함). 이때 sync_state는 엔진이 따로 갱신.
- `keepConflictLoser`: 충돌에서 진 버전을 30일 보관할 테이블(예: `entry_conflict(entryId, json, keepUntil)`), 마이 › 백업에서 "다른 기기에서 고친 내용" 보기.
- `reply`/`chat`도 드라이브 JSON에 들어간다 → `Entry.toDoc(timeUnknown, reply, chat)`에 넘겨 줄 것.
- 사진은 `PhotoStore`가 이미 장변 1600px·품질 80으로 저장한다는 전제(`photoBytes`는 그 파일 그대로).
- 삭제 표시 30일 뒤 실제 삭제(`purgeDeleted`)는 **동기화가 끝난(sync_state.remoteVersion == updatedAt) 것만**.
- 저장·삭제 후 `SyncScheduler.afterSave(context)`, 앱 포그라운드 시 `SyncScheduler.onAppOpen(context)`.
