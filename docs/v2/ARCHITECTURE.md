# AI 일기장 v2 설계서

작성 2026-11-21 · 개정 3 (서버 없음 · 온디바이스 AI · Android 먼저) · 기획서: [PLAN.md](PLAN.md)

---

## 1. 전체 구성: 우리 서버가 없다

```
 Android 앱 (Kotlin · Compose)
 ├ Room (기기 안 DB, 원본)  ──자동 백업/동기화──▶  사용자 본인 Google 드라이브 "앱 전용 폴더"(appDataFolder)
 ├ 기본 AI (앱에 포함, ~30MB)                        ▲
 │   ├ 감정 분류기 (ONNX int8)                       │ v2.2 웹도 같은 폴더를 읽고 씀
 │   ├ 위기 감지 규칙 + 분류기                        │
 │   ├ 태그 추출 (형태소 + 사전)               PC 웹 (나중)
 │   └ 답장·질문·회고 문구 라이브러리
 ├ 대화형 AI (선택 다운로드, ~2.6GB): Gemma 4 E2B on LiteRT-LM
 ├ 음성 인식: Android 기기 내 인식기
 ├ Play Billing (일회성 premium) · AdMob
 └ Firebase 무료 기능만: Crashlytics · Remote Config · (Analytics 최소)
```

**운영비가 생기는 곳: 없음.**
- 일기와 사진은 사용자 본인 드라이브 용량에 저장된다.
- AI는 폰 안에서 돈다.
- Firebase는 무료 제품만 쓴다.
- 모델 파일은 Hugging Face 공개 저장소나 Play Asset Delivery로 내려준다 (§5.4).

### 1.1 기술 선택과 이유

| 결정 | 선택 | 버린 대안과 이유 |
|---|---|---|
| 저장 | **Room을 원본으로 (로컬 우선)** | Firestore 단독 저장: 무료 한도를 넘으면 과금되고, 사진 저장에는 Storage(유료 요금제)가 필요함 |
| 백업·동기화 | **Google 드라이브 appDataFolder** | 우리 서버 없이 무제한으로 확장되고, "내 일기는 내 드라이브에만"이라 개인정보 측면에서도 장점. 웹도 브라우저에서 Drive API로 같은 폴더에 접근 가능. 실시간 동기화는 아니지만 일기 앱에는 충분함 |
| 생성형 AI | **Gemma 4 E2B + LiteRT-LM (선택 다운로드)** | Gemini Nano(ML Kit GenAI): 약관상 18세 미만이 쓸 수 있는 앱에서 사용 금지, Galaxy A·S24 이하 미지원. EXAONE: 비상업 라이선스. 1B 이하 모델: 한국어 공감 답장이 어색함. Gemma 4 E2B는 Apache 2.0이고 Android와 웹(LiteRT-LM Web)을 모두 지원 |
| 감정 분류 | **소형 인코더(KoELECTRA-small/KcELECTRA) 파인튜닝 → ONNX int8** | LLM으로 분류하면 느리고 저사양 폰에서 못 돌림. ONNX 파일 하나를 웹(onnxruntime-web)에서도 그대로 씀 |
| 위기 감지 | **규칙(사전·변형·초성) + 분류기, 놓치지 않는 쪽으로** | LLM에만 맡기면 놓치거나 출력이 막힘 |
| 음성 인식 | **기기 내 인식기 (`createOnDeviceSpeechRecognizer`, API 31+)**, 지원하지 않으면 시스템 인식기 | Whisper는 140MB 이상 추가. 시스템 인식기가 없는 기기가 많다고 확인되면 v2.1에 선택 다운로드로 추가 |
| 결제 확인 | **앱 안에서 Play Billing으로 확인** (퍼스널컬러와 같은 방식) | 서버 검증은 Functions(유료 요금제)가 필요. 3,300원 상품이라 위변조 위험 대비 비용이 맞지 않음 |
| 웹(나중) | React + TypeScript, Firebase Hosting(무료) 또는 GitHub Pages | 데이터 형식(JSON)과 AI 파일(ONNX·LiteRT)을 Android와 공유 |

## 2. 데이터 모델

### 2.1 Room (기기)

```
entry        id(UUID), date('2026-11-21'), time('22:41'), createdAt, updatedAt, deleted(bool, 동기화용 표시)
             title, body, template, source('text'|'voice'|'chat'), weather?, lightColor(계산값 캐시)
entry_emotion entryId, emotionKey('joy','calm',…,'unsure'), intensity(1..5)
entry_tag    entryId, kind('person'|'place'|'activity'|'free'), value, auto(bool)
photo        id, entryId, localPath, w, h, driveFileId?
reply        entryId, text, source('template'|'llm'), templateId?, createdAt, reported(bool)
chat_message entryId, role, text, at
emotion_def  key, name, groupKey, colorHex, order, custom(bool)     ← 사용자가 이름·색 수정 가능
collection   itemKey, unlockedAt                                    ← 빛 팔레트·소품
sync_state   entityId, localVersion, remoteVersion, remoteFileId
```

- 날짜는 문자열 `YYYY-MM-DD` 하나로 둔다. v1처럼 연·월·일을 따로 저장하지 않는다.
- 감정은 **키 문자열**로 저장한다. v1처럼 리소스 ID를 저장하지 않는다.
- 삭제는 `deleted=true`로 표시만 하고, 동기화가 끝난 뒤 30일이 지나면 실제로 지운다.

### 2.2 드라이브 파일 형식 (웹과 공유하는 약속)

```
appDataFolder/
  manifest.json                    { schema: 1, deviceIds[], updatedAt }
  entries/2026/11/<entryId>.json   { id, date, time, updatedAt, deleted, title, body, emotions[{key,intensity}],
                                     tags[], photos[{id, w, h}], reply{…}, chat[…] }
  photos/<photoId>.jpg             장변 1600px, 품질 80
  settings.json                    감정 정의, 테마, 알림 (잠금 PIN은 저장하지 않음)
  entitlement.json                 { premium: true, orderId, purchasedAt } ← 웹에서 광고·프리미엄 표시용
```

- 일기 하나가 파일 하나다. 변경분만 올리므로 빠르다.
- JSON 스키마는 `shared/schema/*.json`에 두고 Android와 웹이 같은 것을 쓴다.

## 3. 동기화 규칙

1. 저장하면 Room에 먼저 쓴다(즉시). 그다음 WorkManager가 변경분을 업로드한다. 네트워크 연결 조건, 재시도, 지수 백오프를 적용한다.
2. **업로드 검증**: 올린 뒤 파일의 `md5Checksum`과 수정 시각을 다시 읽어 확인한다. 실패하면 "백업 안 됨" 배지를 보여 주고 재시도한다. 경쟁 앱 불만 1위인 "백업이 데코용"을 막기 위한 장치다.
3. **내려받기**: 앱을 열 때와 하루 1회. `changes.list`(pageToken)로 바뀐 파일만 가져온다.
4. **충돌**: 같은 일기를 두 기기에서 고친 경우, 마지막에 저장된 쪽(`updatedAt`)을 남긴다. 진 쪽은 "다른 기기에서 고친 내용"으로 30일간 보관한다.
5. 설정 화면에 마지막 백업 시각과 "지금 백업", "백업에서 복원 테스트"를 둔다.

권한: Google 로그인(Credential Manager) + `drive.appdata` 범위.
- **확인 필요**: 이 범위가 Google OAuth 검증 대상(민감 범위)인지 출시 전에 확인한다. 민감 범위여도 검증 절차는 무료다.

## 4. 감정과 빛

### 4.1 감정 정의

5개 그룹 × 4개 + `unsure`, 총 21개 키. 기본 색은 온이 시트의 `colors.json`을 그대로 쓴다(캐릭터 작업 산출물). 사용자가 이름과 색을 바꾸면 `emotion_def`에 저장된다.

### 4.2 빛 색 섞기

- 그날(또는 그 기록)의 감정들을 **강도 가중 평균**으로 섞는다. 섞는 색 공간은 OKLab이다. 그래야 색이 탁해지지 않는다.
- 강도 합이 클수록 채도·밝기를 높인다(빛이 세짐). `unsure`만 있으면 꺼진 빛으로 처리한다.
- 하루에 여러 번 기록한 경우:
  - 캘린더 칸은 기록 색들의 그라데이션이다.
  - "하루의 빛"은 시간축 위치에 각 색을 놓고 부드럽게 잇는다.
- 순수 함수(`LightMixer`)로 만들고 단위 테스트로 고정한다. 웹에서도 같은 공식을 TypeScript로 옮긴다.

## 5. AI 설계

### 5.1 처리 순서 (저장할 때마다)

```
본문 저장
 → ① 위기 감지 (규칙 → 분류기)  ── 걸리면: 답장 생성 중단, 안내 카드(109 · 1577-0199) 표시, 끝
 → ② 감정 분류기: 감정·강도 추천 (사용자가 직접 고른 감정이 있으면 그것을 우선)
 → ③ 태그 추출: 사람·장소·활동 후보 → 칩으로 제안
 → ④ 답장
      ├ 대화형 AI가 있고 기기 여유가 있으면 → LLM 답장 (§5.3) → 후처리 검사 실패 시 ↓
      └ 문구 라이브러리 답장 (§5.2)
```

모든 단계는 기기 안에서만 처리한다. 네트워크를 쓰지 않는다.

### 5.2 기본 AI (모든 폰, 앱에 포함)

| 구성 | 방법 | 크기 |
|---|---|---|
| 감정 분류기 | KOTE(43개 감정 라벨)로 소형 인코더 파인튜닝 → 우리 20개 감정으로 매핑, 다중 라벨 확률을 강도로 사용. 직접 만든 일기체 검증셋 300건으로 정확도 측정 | 15~30MB (int8) |
| 위기 감지 | 표현 사전(변형·띄어쓰기·초성 포함) + 분류기 점수. **애매하면 안내 쪽으로** | <1MB |
| 태그 추출 | 형태소 분석기(Kiwi 등) + 관계어 사전("엄마", "팀장님", "카페") + 사용자가 쓴 태그 학습 | ~10MB |
| 답장 라이브러리 | 감정 그룹 × 강도 × 상황 태그별 답장 **400개 이상**, 질문 200개, 회고 문장 템플릿. 슬롯(`{사람}`, `{활동}`)에 일기 속 단어를 채움. 같은 문구는 30일 안에 반복하지 않음 | <1MB |

- 답장 라이브러리는 개발할 때 한 번 대량으로 쓰고, 사람이 직접 검수해서 앱에 넣는다. 운영비는 0원이다. 말투 규칙은 온이 README를 따른다(다정한 반말, 진단·충고 금지).
- 라이선스 확인 후 사용: KOTE 데이터, 인코더 모델, 형태소 분석기(LGPL이면 동적 링크 조건 확인). AI Hub 데이터는 약관을 확인하기 전에는 쓰지 않는다.

### 5.3 대화형 AI ("온이 깨우기", 선택)

- **모델** (한국어 비교 실험 결과: `tools/model-eval/REPORT.md`, 10개 모델·일기 30편)
  - **기본**: Gemma 4 E2B (Apache 2.0)
    - 2B급 중 한국어 자연스러움 1위(4.73/5), 답장 4.40/5
    - 폰 예상 속도는 답장 하나에 3~5초
  - **고급 온이**: Gemma 4 E4B (Apache 2.0)
    - 전 과제 1위, 답장 4.70/5
    - RAM 12GB 이상 플래그십용, 약 5GB, 답장 하나에 5~9초(스트리밍)
  - 탈락한 모델
    - 한국어 특화 Kanana 1.5: 훈계형 답장, 상황 오독
    - HCX SEED: 자체 라이선스 의무, JSON이 약함
    - EXAONE: 비상업 라이선스
    - 소형 Qwen: 외국어가 섞임
- **조건**: 기본 모델은 RAM 8GB 이상, 고급 모델은 12GB 이상이 필요하다. 여유 공간도 확인한다. 조건이 안 맞는 폰에는 메뉴를 보여 주지 않고 기본 AI만 쓴다.
- **받기**: 마이 → 온이 깨우기 → "약 2.6GB, Wi-Fi 권장". 진행률, 이어받기, 저장 공간 사전 확인을 둔다. 받는 동안에도 앱은 정상 동작한다. 지우기 버튼도 둔다.
- **쓰는 곳**: 답장, 말로 쓰기 정리, 대화로 쓰기, 개인화 질문, 회고 글, 요약 제목
- **긴 기간 회고**: 일기를 저장할 때 한 줄 요약을 만들어 두고, 회고 때는 그 요약들만 넣는다. 컨텍스트 한도와 속도 때문이다.
- **품질 장치**:
  - 프롬프트 설정: 시스템 지시 + 좋은 답장 예시 5개, 낮은 temperature, 출력 길이 제한
    - 실험에서 드러난 약점 세 가지(반어 오독, 조언형 끝맺음, 주어 혼동)에 대한 규칙과 예시를 넣는다
  - 감정 JSON: 문법을 강제해 디코딩하고, 목록에 없는 감정은 바꿔 준다(걱정→불안, 감동→감사)
  - 후처리 검사: 존댓말 섞임, 반복, 일기에 없는 사람 이름, 진단·충고 표현, 길이를 확인한다. 하나라도 걸리면 라이브러리 답장으로 바꾼다.
  - 출시 전에 실제 일기 100건 이상으로 답장을 직접 검수한다. 기준에 못 미치면 v2.0에서는 대화형 AI를 "베타"로 표시한다.
  - 여유가 되면 반말 공감 말투로 LoRA 미세조정을 한다(Apache 2.0이라 허용).
- **포그라운드 전용**: 저장 직후 답장 화면에서 생성하고, 생성 중에는 온이가 "답장 쓰는 중" 애니메이션을 보인다. 목표는 중급기 기준 10초 안팎이며, 실측해야 한다.

### 5.4 모델 배포

- **1순위**: Hugging Face LiteRT Community의 공개 파일을 앱이 직접 받는다. 우리 호스팅 비용이 없다.
  - 위험: 파일 경로가 바뀔 수 있다. 그래서 URL과 체크섬을 Remote Config로 관리한다.
- **2순위**: Play Asset Delivery on-demand 팩. 팩 하나 1.5GB 한도라 2개로 나눈다.
  - Play가 배포하므로 안정적이다. 다만 앱 업데이트와 묶이는 점을 검토해야 한다.
- 분류기와 사전 같은 기본 AI는 앱에 포함한다(+30MB 안팎).

## 6. 음성 인식

- `SpeechRecognizer.isOnDeviceRecognitionAvailable` 또는 `checkRecognitionSupport("ko-KR")`로 지원 여부를 확인한다.
  - 지원하면 기기 내 인식을 쓴다.
  - 지원하지 않으면 시스템 기본 인식기를 쓰고, 이 경우 "인식에 인터넷을 쓸 수 있어요"라고 고지한다.
- 연속 받아쓰기: 인식 세션을 이어 붙이고 중간 결과를 표시한다.
- 녹음 파일은 저장하지 않는다(선택 옵션은 v2.1).

## 7. 결제·광고

- **상품**: 소모되지 않는 일회성 상품 `premium` 하나, 3,300원.
- **확인**:
  - 앱이 열릴 때 `queryPurchasesAsync`로 확인하고, 구매 확정(acknowledge)을 처리한다.
  - 같은 Google 계정이면 새 폰에서도 자동 복원된다. 앱이 하는 일이 아니라 Play가 계정 단위로 관리하기 때문이다.
  - 결제 화면에 "폰을 바꿔도 그대로"를 크게 표시한다.
- **웹(나중)**: Android가 드라이브에 `entitlement.json`을 써 두면 웹이 그것을 읽어 프리미엄 기능을 연다. 웹에는 광고가 없다.
- **광고**: AdMob 배너(캘린더·통계 하단)와 네이티브(타임라인 중간)를 쓰고, UMP 동의를 받는다. 보상형은 "특별 빛 팔레트 하루 써 보기"에만 쓴다.
  - 작성·저장·답장·잠금 화면에는 광고를 넣지 않는다(코드에서 화면 허용 목록으로 강제).
- **재사용**: 퍼스널컬러 v2의 `core/ads`(AdMob + UMP + Billing 일회성 `premium` + 구매 복원)를 그대로 가져온다.

## 8. v1 → v2 이전

1. 출시 전에 Play Console에서 v1.1 APK를 받아 `aapt2 dump resources`로 "이모티콘 리소스 정수 → `em_smile` 등 이름" 매핑표를 만든다.
2. 첫 실행 때 기존 `databases/diary`(Room v2, 테이블 `DiaryEntity`)를 SQLite로 직접 읽어 새 DB로 옮긴다.
   - v1 이모티콘 15종은 새 감정으로 매핑한다. 예: smile·happy → 기쁨, sad → 슬픔, angry → 화남, tired → 피곤, thinking → 잘 모르겠음.
   - 매핑할 수 없으면 `unsure`로 둔다.
3. 옮긴 뒤 원본 파일은 `diary.migrated`로 이름만 바꾸고 지우지 않는다.
4. v1 잠금 비밀번호는 평문 저장이었으므로 옮기지 않는다. 대신 새로 설정하도록 안내한다.
5. **전제**: 같은 앱 ID(`com.pass.diary`)로 업데이트하려면 v1 업로드 키가 필요하다. 미확인 상태다.

## 9. 웹 준비 (v2.2를 위해 지금 지켜 둘 것)

- 데이터는 드라이브 JSON 스키마(§2.2)를 단일 기준으로 삼는다. Android 전용 형식을 만들지 않는다.
- 빛 섞기 공식과 감정 정의는 플랫폼 중립 명세로 두고, 테스트 벡터 JSON을 함께 둔다.
- 감정 분류기는 ONNX로 만든다(onnxruntime-web 호환).
- 대화형 AI는 LiteRT-LM 형식을 쓴다(LiteRT-LM Web은 WebGPU로 Gemma 4 E2B를 지원하고, 아직 미리보기 단계).
- 답장·질문 라이브러리는 JSON 리소스로 둔다. Kotlin 코드 안에 넣지 않는다.
- 참고: Chrome 내장 AI는 한국어를 지원하지 않아 쓰지 않는다.

## 10. Android 모듈 구조

퍼스널컬러 v2와 같은 방식(멀티 모듈 + build-logic 컨벤션)을 쓴다.

```
app/              셸(하단 탭), 내비게이션, 잠금 게이트, 강제 업데이트, 위젯 바인딩
core/model        Entry, Emotion, Light, Reply …
core/data         Room, 설정(DataStore), 레포지토리
core/sync         Drive appData 업·다운로드, WorkManager, 검증
core/ai           위기 감지, 감정 분류기(ONNX), 태그, 답장 라이브러리, LLM 런타임(LiteRT-LM), 모델 다운로드
core/light        LightMixer(OKLab), 감정 정의, 색 토큰
core/designsystem 테마, 온이 캐릭터(Compose 렌더 + 애니메이션), 컴포넌트
core/billing-ads  퍼스널컬러 core/ads 이식
core/security     PIN 해시 + 생체 인증, 재잠금
core/legacy       v1 데이터 이전
feature/          today, write(쓰기·말하기·대화·질문), calendar, report, letters(편지함), my, onboarding, widget
shared/           schema/*.json, light-test-vectors.json, reply-library/*.json  ← 웹과 공유
tools/            classifier/(학습·변환 스크립트), reply-library/(작성·검수 도구)
```

- 기술 스택: Kotlin 2.x, Compose, Hilt(KSP), Room, WorkManager, ONNX Runtime Android, LiteRT-LM, Credential Manager, Google Drive REST, AdMob, Play Billing, targetSdk 36, edge-to-edge.

## 11. 개인정보·안전

- 일기는 기기와 사용자 본인 드라이브에만 있다. AI도 기기 안에서 처리한다.
  - 그래서 개인정보 국외 이전·처리위탁 이슈가 클라우드 AI 방식보다 훨씬 작다. Google 드라이브는 사용자 본인 계정이다.
- Play 데이터 보안 양식: 수집 없음에 가깝다. 다만 광고 SDK(AdMob)가 기기 식별자를 수집하므로 그 부분은 신고한다.
- 만 14세 이상만 가입하도록 온보딩에서 확인한다.
- AI 결과 표시, 신고 버튼, 위기 안내, 의료 아님 고지를 둔다.

## 12. 개발 순서 (병렬)

| 줄기 | 작업 | 확인 기준 |
|---|---|---|
| A. 앱 뼈대 | 새 코드베이스(같은 앱 ID), Room, 기록·캘린더·잠금 | 오프라인으로 쓰고 다시 열어도 그대로 |
| B. 이전·백업 | v1 이전, 드라이브 동기화 + 검증 | 실제 v1.1 기기에서 업데이트 후 데이터 보존, 폰 2대 동기화 |
| C. 기본 AI | 분류기 학습·변환, 위기 사전, 답장 라이브러리 400개 | 일기체 검증셋 정확도, 위기 문장 테스트 100% 감지 |
| D. 대화형 AI | LiteRT-LM 통합, 다운로드, 프롬프트, 후처리 | **개발폰(갤럭시 노트20, RAM 8GB)에서 속도·품질 실측** |
| E. 캐릭터·디자인 | 온이 최종안, 화면 시안 → 구현 | 폰 스크린샷 직접 검수 |
| F. 수익 | 결제·광고 이식 | 테스트 카드 구매, 재설치 후 복원 |

## 13. 출시 전 확인 목록

- [ ] v1 업로드 키 보유 여부
- [ ] `drive.appdata` 범위가 OAuth 검증 대상인지
- [ ] KOTE 데이터, 인코더 모델, 형태소 분석기 라이선스 원문
- [ ] Gemma 4 E2B 중급기 속도와 메모리 (노트20, 가능하면 A 시리즈)
- [ ] 한국어 기기 내 음성 인식 지원 비율 (삼성 기기)
- [ ] Play AI 생성 콘텐츠 정책 요건 (신고 기능 등)
