# core:ai 의존성 메모 (D → A)

현재 `core/ai/build.gradle.kts`로 충분하다. 추가 요청 없음. 아래는 확인해 둔 근거와 통합 때 볼 것.

## 이미 있는 것 (그대로 유지)
- `com.google.ai.edge.litertlm:litertlm-android:0.19.1` — Google Maven. 2026-11-05 릴리스, minSdk 24.
  - AAR에 `arm64-v8a`, `x86_64`만 들어 있다. 32비트 폰은 지원하지 않는다.
    - 32비트 폰은 어차피 RAM 8GB 게이트에서 걸러진다.
  - 따라오는 의존성: gson 2.14.0, kotlin-reflect 2.4.0, kotlinx-coroutines-android 1.11.0.
- `com.microsoft.onnxruntime:onnxruntime-android:1.32.0` — 감정 분류기.
- `noCompress += onnx, litertlm`, `assets.srcDirs(shared)` — `AndroidAssets`가 `ai/...`, `library/...` 경로로 읽는다.
- `unitTests.isReturnDefaultValues = true` — JVM 테스트에서 android 스텁을 쓴다.
  - 네이티브 런타임은 쓰지 않고 가짜(`FakeLlm`, `ClassifierModel` 람다)로 대신한다.

## 앱(app) 매니페스트에 필요한 것 (A가 통합 때 추가)
- `<uses-permission android:name="android.permission.INTERNET"/>`: 모델 받기에만 쓴다.
- `<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE"/>`: Wi-Fi 권장 표시(`NET_CAPABILITY_NOT_METERED`)에 쓴다.
- LiteRT-LM GPU 백엔드를 쓰려면 `<application>` 안에 아래 두 줄을 넣는다. 없으면 `LiteRtLmRuntime`이 CPU로 자동 전환한다.
  ```xml
  <uses-native-library android:name="libvndksupport.so" android:required="false"/>
  <uses-native-library android:name="libOpenCL.so" android:required="false"/>
  ```
- 다운로드는 `ModelManager.download()` Flow로 한다.
  - 화면 밖에서도 계속 받으려면 feature/my에서 WorkManager(`work-runtime`, 카탈로그에 있음)의 foreground worker로 감싼다.
  - core:ai에는 넣지 않았다.

## LiteRT-LM Kotlin API (v0.19.1 소스 기준, `LiteRtLmRuntime`이 사용)
출처:
- https://github.com/google-ai-edge/LiteRT-LM/blob/v0.19.1/kotlin/java/com/google/ai/edge/litertlm/Config.kt
- 같은 폴더의 `Engine.kt`, `Conversation.kt`, `ResponseFormat.kt`
- https://github.com/google-ai-edge/LiteRT-LM/blob/v0.19.1/docs/api/kotlin/getting_started.md
- https://ai.google.dev/edge/litert-lm/android

API 요약:
- `Engine(EngineConfig(modelPath, backend = Backend.GPU()|Backend.CPU(), cacheDir = ...))` → `initialize()`
  - 약 10초 걸리는 블로킹 호출이다. 메인 스레드에서 부르면 안 된다.
- `engine.createConversation(ConversationConfig(systemInstruction = Contents.of(..), initialMessages = [Message.user/model], samplerConfig = SamplerConfig(topK, topP, temperature, seed), maxOutputToken, thinkingConfig = ThinkingConfig(enableThinking=false), enableResponseFormat = true))`
- `conversation.sendMessageAsync(text, responseFormat = ResponseFormat.json(schema)): Flow<Message>` — 스트리밍. `cancelProcess()`로 멈추고, `close()`로 닫는다.
- 제약 디코딩
  - JSON 스키마와 정규식만 지원한다. Kotlin에는 Lark 문법이 없다.
  - `enableResponseFormat=true`가 없으면 예외가 난다.
  - tools와 같이 쓰면 무시된다.
- 샘플러는 대화마다 고정된다. 그래서 요청마다 Conversation을 새로 만든다.

## 기기에서 확인할 것 (JVM 테스트로는 못 하는 것)
- Flow 조각이 증가분인지 누적 텍스트인지 문서에 없다. 그래서 `LiteRtLmRuntime`은 두 경우를 모두 처리한다.
- Gemma 4에서 `ResponseFormat.json`이 실제로 동작하는지, 속도는 어떤지 확인해야 한다.
  - llama.cpp에서는 문법을 강제하자 Gemma 4가 "생각 모드"로 빠졌다. 그래서 `ThinkingConfig(enableThinking=false)`를 넣었다.
- `cancelProcess()` 뒤에 대화 상태가 되돌려지지 않는다(b/450903294).
  - 요청마다 대화를 새로 만들기 때문에 영향은 없다.
