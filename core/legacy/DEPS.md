# core:legacy 의존성 요청 (B → A)

- `testImplementation("org.robolectric:robolectric:4.18.1")` — `LegacyMigratorTest`가 실제 `android.database.sqlite`로 `tools/legacy/fixture_v1.db`를 연다.
- (이미 있음) `:core:model`, coroutines, serialization-json, coroutines-test.

## core:data에 필요한 것 (통합 단계)
- `LegacyEntrySink` 구현: `upsertLegacy(entries)`를 **한 트랜잭션**으로, `Entry.id` 기준 upsert. (`EntryDao.save`를 `@Transaction` 안에서 반복하면 됨)
- `entry.timeUnknown`(Boolean, 기본 false) 컬럼 — v1 이전 일기는 시각이 없어 `21:00`으로 넣고 이 값을 true로 둔다. 드라이브 JSON에도 같은 이름으로 있다.
- 앱 시작 시 Room을 열기 전에 `LegacyMigrator.migrateIfNeeded()` 호출(스플래시 동안). 결과의 `migrated > 0`이면 "v1 일기 N개를 옮겼어요 · 잠금은 새로 설정해 주세요" 안내.
