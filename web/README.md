# 온이 일기 웹

React + TypeScript + Vite PWA. `v2` 브랜치에 push하면 GitHub Pages(https://jungwoogeon.github.io/Diary/)로 배포된다(`.github/workflows/web-pages.yml`).

```
npm ci && npm test && npm run build    # 로컬 확인: npm run dev  /  빌드 결과: npx vite preview
```

## 감정 AI (W1)

- `shared/ai/classifier/model.tflite`(앱과 같은 파일)를 **LiteRT.js(`@litertjs/core`)**로 Web Worker(`src/emotion.worker.ts`)에서 돌린다. 토크나이저·판정 규칙은 `src/emotion.ts`(Kotlin `EmotionClassifier.kt` 1:1 이식).
- `test/emotion.test.ts`가 `core/ai/src/test/resources/classifier_reference.json` 14문장으로 토큰 id 완전 일치, 확률 오차 < 0.02(실측 0.016), 감정 키 일치를 확인한다(Node에서 같은 wasm으로 실행, 문장당 약 90ms).
- 처음 쓸 때만 받고 Cache Storage `oni-ai`에 둔다(파일명에 해시 → 모델이 바뀌면 새로 받고 옛 파일은 지움). 서비스워커는 이 파일들을 따로 캐시하지 않는다.
- 끄기: 마이 → 감정 AI. 데이터 절약 모드(`saveData`)면 기본으로 꺼져 있다.
- 임베딩(E4 비슷했던 날, E5 뜻으로 검색, E7 이번 달의 한 줄): 모델에 문장 임베딩 출력(“classify”의 두 번째 출력이나 “embed” 시그니처)이 생기면 자동으로 켜지고, 없으면 화면에 나오지 않는다.

### 런타임 비교 (2026-11-22, 실측)

| 방식 | 런타임 wasm (gzip) | 모델 | 비고 |
|---|---|---|---|
| **LiteRT.js 2.6.0 (선택)** | 11.1MB (3.1MB) | 같은 `.tflite` 15.7MB | Google 현행 런타임, 변환 없음, 참조 테스트 통과 |
| @tensorflow/tfjs-tflite 0.0.1-alpha.10 | 3.7MB (1.2MB) + tfjs-core | 같은 `.tflite` | 2023년 이후 갱신 없는 알파 |
| onnxruntime-web 1.31.1 | 14.8MB (3.8MB) | 별도 ONNX 변환 필요 | 앱과 다른 파일 |

첫 로드(감정 AI를 처음 쓸 때): 모델 15.7MB(int8이라 거의 안 줄어듦) + wasm 약 3.1MB(gzip) + 로더 JS 0.28MB + 토크나이저 0.25MB(gzip) ≈ **19MB**. 그 뒤는 캐시에서 바로 읽는다. 앱 첫 화면 번들에는 들어가지 않는다(워커 52KB, 필요할 때만).

## 온이 AI 답장 (W2) · 크롬 내장 AI

`src/webAi.ts`. 확인일 2026-11-22.

- **API**: `LanguageModel.availability(options)` → `unavailable | downloadable | downloading | available`, `LanguageModel.create({ initialPrompts, expectedInputs, expectedOutputs })`, `session.prompt()`. 웹 페이지용은 Chrome 148부터, Web Worker에서는 못 쓴다.
  출처: https://developer.chrome.com/docs/ai/prompt-api , https://developer.chrome.com/docs/ai/get-started
- **다운로드**: 모델이 없으면 `create()`가 다운로드를 시작한다(사용자 조작 필요, 22GB 여유 공간 등 PC 조건). → 우리는 `availability()`가 `available`일 때만 `create()`를 부르고, 다른 값이면 아예 쓰지 않는다(테스트로 고정).
- **언어**: 문서상 지원 언어는 en, ja, es, de, fr. **한국어(ko)는 없다.** 우리는 `ko`로 가용성을 묻기 때문에 지금 크롬(실측: Chrome 158, macOS)에서는 `unavailable`이 오고, 답장은 온이의 한마디로 대신한다. 크롬이 한국어를 지원하면 배포 없이 켜진다.
- **정책**: Prompt API 문서는 쓰기 전에 Google Generative AI Prohibited Uses Policy(https://policies.google.com/terms/generative-ai/use-policy) 확인을 요구한다. 이 정책에 연령 조건은 없고, 금지 항목(자해 조장, 사칭, AI 생성물을 사람이 쓴 것처럼 속이기 등)은 우리 사용 방식과 충돌하지 않는다(위기 감지 우선, "온이(AI)" 표시). Chrome 내장 AI 문서들에도 연령 제한 조항은 찾지 못했다. (참고: Android ML Kit GenAI의 Gemini Nano는 약관상 18세 미만 이용 앱에서 금지 — 이것은 Android용 SDK 약관이고, 웹 Prompt API에는 같은 조항이 없다.) 기업 관리자는 `BuiltInAIAPIsEnabled` 정책으로 막을 수 있다.
- **흐름**: 저장 → 위기 감지(걸리면 AI 호출 없이 도움 안내) → 크롬 내장 AI 답장(반말, 2~3문장, 공감 + 질문 1개) → `checkReply`(존댓말·훈계·진단·외국 문자·일기에 없는 숫자/영단어·위기 표현·문장 수·질문 위치) → 실패하면 한 번 더, 또 실패하면 온이의 한마디.
- **저장**: 일기 JSON `reply`에 `source: "llm"`, `engine: "chrome-builtin"`(새 선택 필드, `shared/schema/entry.schema.json`). Android는 `ignoreUnknownKeys`라 `engine`을 무시하고, `llm`은 이미 아는 값이다.
- **표시·신고**: 모델이 쓴 답장만 "온이(AI)", 라이브러리 답장은 "온이의 한마디". 신고는 답장을 숨기고(`reported: true`), 신고 기록은 이 브라우저 localStorage(`replyReports`)에만 남긴다.

## 공유 카드 (W3)

오늘 탭의 "오늘의 빛 카드", 리포트의 "빛 캘린더 카드" → 1080×1920 PNG. 일기 본문은 넣지 않고 색·날짜·감정 이름만. 아래에 작게 "온이 일기 · jungwoogeon.github.io/Diary"(프리미엄은 없음).
