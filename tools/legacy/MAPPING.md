# v1 이모티콘 → v2 감정 키

정본은 `emoticon_ids.json`, 앱 코드는 `core/legacy/src/main/.../LegacyEmoticons.kt`. 둘이 다르면 `EmoticonTableTest`가 실패한다.

## 1. 리소스 정수 → 이름 (상태: **저장소의 릴리스 번들로 확인, Play 배포본으로는 미확인**)

v1은 `emoticonId1~3`에 `R.raw.em_*` 정수를 그대로 저장했다. 이 값은 빌드할 때 aapt가 정한다.

- v1 저장소에 실제 업로드용 번들이 커밋되어 있다.
  - `presentation/release/ai_diary_v1.aab` (versionCode 1, 1.0)
  - `presentation/release/ai_diary_v2.aab` (versionCode 2, 1.1)
- 두 번들 모두 `aapt2 dump resources` 결과가 같다: `0x7f100000 em_angry` … `0x7f10000e em_tired` (이름 알파벳순).
- 덤프 방법 (bundletool 없이):
  ```sh
  unzip ai_diary_v2.aab -d x && mkdir apk && cp x/base/manifest/AndroidManifest.xml x/base/resources.pb apk/ && cp -r x/base/res apk/
  (cd apk && zip -qr ../proto.apk .)
  aapt2 convert --output-format binary -o bin.apk proto.apk
  aapt2 dump resources bin.apk | grep raw/em_
  ```
- **출시 전 할 일**: Play Console › 앱 번들 탐색기 › versionCode 2에서 내려받은 파일로 위 명령을 다시 돌려 같은 값인지 확인하고, `emoticon_ids.json`의 `verifiedAgainstPlayDownload`를 `true`로 바꾼다.
- 표에 없는 정수(다른 빌드, 깨진 값)는 `unsure`로 옮기고 개수를 로그와 결과에 남긴다. 리소스가 바뀐 뒤에는 런타임으로 이름을 찾을 방법이 없다.

v1 DB 형태도 두 가지다. 이전 코드는 둘 다 읽는다.

| 버전 | Room version | 테이블 |
|---|---|---|
| 1.0 | 1 | `Diary` |
| 1.1 | 2 | `DiaryEntity` (컬럼 동일) |

## 2. 이름 → 감정 키

v2에는 "놀람" 감정이 없다. 기쁨/불안 어느 쪽인지 그림만으로 정할 수 없는 것은 `unsure`로 두고, 사용자가 나중에 고치게 한다.

| v1 | 그림 | v2 키 | 강도 | 이유 |
|---|---|---|---|---|
| em_smile | 잔잔한 미소 | `joy` | 2 | 설계서 §8 예시(smile·happy → 기쁨). 잔잔하므로 강도 낮게 |
| em_happy | 볼 빨간 웃음 | `joy` | 3 | 설계서 §8 예시 |
| em_laughing | 눈물 나게 웃음 | `excited` | 4 | 크게 웃는 얼굴 = 신남 |
| em_like | 하트 눈 | `love` | 4 | 하트 눈 = 사랑 |
| em_thumbsup | 엄지 척 | `proud` | 3 | "잘했다" = 뿌듯함 |
| em_angry | 찡그린 빨간 얼굴 | `angry` | 4 | 그대로 |
| em_hot | 땀 흘리는 빨간 얼굴, 혀 | `annoyed` | 3 | 더위·열받음 둘 다 "짜증"에 가깝다. 화남(angry)보다 약함 |
| em_nervous | 식은땀 | `anxious` | 3 | 긴장 = 불안 |
| em_sad | 글썽이는 눈 | `sad` | 3 | 그대로 |
| em_shocked | 입 벌림 + 느낌표 | `unsure` | 3 | 놀람 없음, 좋은 일·나쁜 일 구분 불가 |
| em_sick | 찡그린 눈, 울렁 | `tired` | 4 | 아픔 = 지침 그룹, 피곤 |
| em_surprised | 눈 크게, 입 벌림 | `unsure` | 3 | shocked와 같은 이유 |
| em_thinking | 갸웃 | `unsure` | 3 | 설계서 §8 예시 |
| em_tired | 졸린 얼굴 | `tired` | 3 | 그대로 |
| em_picture | 카메라 아이콘 | (없음) | – | 감정이 아님. 감정 목록에 넣지 않는다 |

- 한 일기에 같은 키가 두 번 나오면 하나로 합치고 강도는 큰 쪽을 쓴다. 순서는 v1 슬롯 순서(1→3)를 따른다.
- 이모티콘이 하나도 없거나 picture뿐이면 감정 없이 옮긴다.

## 3. 그 밖의 이전 규칙

- 날짜: `year/month/day` → `YYYY-MM-DD` (0 채움). 숫자가 아니거나 없는 날짜면 그 줄은 건너뛰고 개수를 센다.
- 시각: v1에 없음 → `21:00`, `timeUnknown=true`.
- ID: `UUID.nameUUIDFromBytes("v1-diary:<id>")` — 다시 돌려도 같은 ID라 중복이 생기지 않는다.
- `imageUri`: v1.1 번들에서는 쓰이지 않았다(항상 null). 값이 있어도 다른 앱 URI라 옮기지 않고 개수만 센다.
- 설정: 글자 크기·글꼴은 v2에 대응 항목이 없어 옮기지 않는다. 잠금 비밀번호(`password_settings`, 평문)는 **옮기지 않는다**.

## 4. fixture

`make_fixture.py`가 `fixture_v1.db`를 만든다 (v1.1 스키마, Room 메타 테이블 포함, 경계 사례 포함). `python3 tools/legacy/make_fixture.py`
