# core:ai 의존성 메모 (D → A)

현재 `core/ai/build.gradle.kts`로 충분하다. 추가 요청 없음.

## 이미 있는 것 (그대로 유지)
- `com.google.ai.edge.litert:litert:1.4.2` — 감정 분류기(`model.tflite`)를 TFLite `Interpreter`로 실행한다. arm64 `libtensorflowlite_jni.so` 4.5MB.
  - 예전 `onnxruntime-android` 1.32.0은 arm64 36MB였다(2026-11-21 교체).
  - `litert` 2.x(`libLiteRt.so` 5.6~5.9MB)는 더 크고, 이 모델엔 1.4.2 `Interpreter`로 충분하다.
  - 모델이 길이 192 고정이라 짧은 일기도 192 토큰만큼 계산한다(노트20 약 75ms).
- `assets.srcDirs(shared)` (`core:data`) — `AndroidAssets`가 `ai/...`, `library/...` 경로로 읽는다.
- `unitTests.isReturnDefaultValues = true` — JVM 테스트에서 android 스텁을 쓴다.
  - 네이티브 런타임은 쓰지 않고 가짜(`ClassifierModel` 람다)로 대신한다.
  - `classifier_reference.json`(토크나이저·결정 규칙 대조)은 `tools/classifier/export_tflite.py`가 다시 만든다.

## 없앤 것
- 온디바이스 LLM(Gemma 4 + LiteRT-LM, 약 2.6GB 선택 다운로드)은 2026-11-22에 뺐다. 수 GB 다운로드는 하지 않는다.
  답장·대화로 쓰기는 `shared/library`의 준비된 문장(`TemplateReplyEngine`)만 쓴다.
