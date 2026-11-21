# v2 병렬 작업 약속 (모든 작업자 필독)

브랜치 `v2`. 커밋·푸시는 하지 않는다(사용자 확인 후 일괄). 기획 [PLAN.md](PLAN.md), 설계 [ARCHITECTURE.md](ARCHITECTURE.md).

## 1. 디렉터리 소유권 (남의 영역 파일은 고치지 말 것)

| 영역 | 소유 | 비고 |
|---|---|---|
| 루트 Gradle(`settings.gradle.kts`, `build.gradle.kts`, `gradle/`, `build-logic/`, `gradle.properties`), `app/`, `core/model`, `core/data`, `core/light`, `core/designsystem`, `core/security`, `feature/*`, `_v1/` | **A 앱 뼈대** | 모든 모듈의 `build.gradle.kts`도 A가 만든다 |
| `core/legacy/src`, `core/sync/src`, `tools/legacy/` | **B 이전·동기화** | Gradle 파일은 A가 만든 것을 사용. 의존성 추가가 필요하면 `core/<모듈>/DEPS.md`에 적어 두면 A(또는 통합 단계)가 반영 |
| `shared/ai/`, `shared/library/`, `tools/classifier/` | **C 기본 AI 자료** | 데이터·모델 파일과 학습 스크립트만 |
| `core/ai/src`, `tools/model-eval/` | **D AI 런타임** | C가 만든 `shared/` 파일 형식을 읽는 코드 |
| `JWG/release/일기장_화면_시안/` | **E 화면 시안** | 저장소 밖 |

- A는 처음에 `app`이 `core:legacy`, `core:sync`, `core:ai`에 **의존하지 않게** 둔다(다른 작업자 코드가 미완성이어도 A 빌드가 깨지지 않도록). 통합은 마지막 단계.
- 각 모듈은 자기 단위 테스트(`./gradlew :core:<모듈>:testDebugUnitTest`)가 통과해야 완료.

## 2. 공통 값

- 앱 ID `com.pass.diary` (유지), 패키지 루트 `com.pass.diary`, 모듈 패키지 `com.pass.diary.core.<모듈>` / `com.pass.diary.feature.<이름>`
- 앱 이름: 임시 "온이 일기" (`app_name` 문자열 하나로만 관리)
- minSdk 26, targetSdk 36, compileSdk 37(OkHttp 5·core-ktx 1.19 요구), Kotlin 2.x, Compose, Hilt(KSP), JDK 17
- 날짜 `YYYY-MM-DD` 문자열, 시각 `HH:mm`, ID는 UUID 문자열

## 3. 감정 키 (모든 코드·데이터가 이 키만 사용)

| 그룹 키 | 그룹 이름 | 빛 색(light / glow) | 감정 키 (이름) |
|---|---|---|---|
| `bright` | 밝음 | `#FFF0A8` / `#F4C23E` | `joy`(기쁨) `proud`(뿌듯함) `excited`(신남) `grateful`(감사) |
| `calm` | 평온 | `#D6F0CB` / `#7FC08F` | `comfy`(편안함) `serene`(차분함) `relieved`(안심) `light`(홀가분) |
| `flutter` | 설렘 | `#FFD6DF` / `#EE8BA5` | `flutter`(설렘) `love`(사랑) `hope`(기대) `miss`(그리움) |
| `low` | 가라앉음 | `#D3E2F6` / `#7F9FD1` | `sad`(슬픔) `down`(우울) `lonely`(외로움) `hurt`(서운함) |
| `worn` | 지침 | `#E6DDF4` / `#A595CF` | `tired`(피곤) `anxious`(불안) `annoyed`(짜증) `angry`(화남) |
| — | 잘 모르겠음 | 꺼짐 `#D6D0C9` / `#A39A91` | `unsure` |

- 강도 `intensity`: 정수 1~5.
- 1탭 기분(대표 5개) = 각 그룹의 첫 감정(`joy`, `comfy`, `flutter`, `sad`, `tired`), 강도 3.
- 원본: `shared/emotions.json` (A가 이 표대로 생성, 모두가 읽음).

## 4. 공유 파일 형식 (C가 만들고 D가 읽음)

```
shared/ai/classifier/model.tflite      # LiteRT int8, 시그니처 classify: input_ids·attention_mask int32 [1,192](오른쪽 0 패딩) → 21개 감정 키 로짓(sigmoid 다중 라벨)
shared/ai/classifier/tokenizer.json    # HF tokenizers 형식 (또는 vocab.txt + 설정)
shared/ai/classifier/labels.json       # ["joy","proud",...,"unsure"] 출력 순서
shared/ai/classifier/meta.json         # {maxLen, thresholds{key:float}, intensityRule, version}
shared/ai/crisis.json                  # {version, patterns:[{id, regex, weight, note}], threshold, testCases:{positive[], negative[]}}
shared/ai/tags.json                    # {people:[...], places:[...], activities:[...]} 관계어/장소/활동 사전
shared/library/replies.json            # [{id, group, minIntensity, maxIntensity, tags:[...]?, text}]  text 슬롯: {person} {place} {activity}
shared/library/questions.json          # [{id, group?, tags?, text}]
shared/library/retro.json              # 회고 문장 템플릿 [{id, kind:'weekly'|'monthly', condition, text}]
shared/ai/llm/prompts.json             # D가 확정: {version, reply:{system, fewshot[]}, emotions:{...}, tidy:{...}, chat:{...}, title:{...}}
shared/ai/llm/models.json              # D가 확정: [{id:'e2b'|'e4b', url, sha256, sizeBytes, minRamGb, premium}]
```

말투: 온이는 다정한 반말, 진단·훈계·조언형 끝맺음 금지, 일기 속 구체적 디테일 1개 언급, 질문은 최대 1개 (`JWG/release/일기장_캐릭터_시안/README.md` 참고).

## 5. 데이터 모델 (A가 Room으로 구현, B·D가 사용)

ARCHITECTURE.md §2.1 그대로. 드라이브 JSON 형식은 §2.2 — B가 `shared/schema/entry.schema.json`으로 확정.
