# 감정 분류기 (C)

```
python3.11 -m venv .venv && .venv/bin/pip install torch transformers onnx onnxruntime tokenizers scikit-learn
# data/ (gitignore): kote/repo (git clone searle-j/KOTE), kote/raw.json (HF), models/koelectra-small (HF 파일 4개)
.venv/bin/python -I train.py 4      # 1단계: KOTE + 일기체 -> out/model
cp -r out/model out/model_stage1
.venv/bin/python -I train.py 6 --stage2 --src out/model_stage1   # 2단계: 일기체 x2 + KOTE 3000줄 -> out/model
.venv/bin/python -I export.py && cp ../../shared/ai/classifier/model.onnx out/model_int8.onnx
.venv/bin/python -I evaluate.py out/model_int8.onnx   # 실제 배포 int8로 임계값·강도 규칙 조정 -> out/tuned.json
.venv/bin/python -I export.py       # meta.json에 조정값 반영
.venv/bin/python -I infer.py "오늘 너무 피곤했다"   # 참조 추론 (Kotlin이 따라 할 것)
python3 test_crisis.py              # 위기 감지 참조 구현 + 테스트
```

## KOTE 44 → 우리 21 키 (`kote_map.json`)

라벨은 평가자 5명 각각의 선택을 매핑한 뒤 **비율(소프트 라벨)**로 쓰고, 2/5 미만은 버린다.

| 우리 키 | KOTE 라벨 |
|---|---|
| joy | 기쁨, 행복, 환영/호의, 흐뭇함(귀여움/예쁨) |
| proud | 뿌듯함 |
| excited | 즐거움/신남, 신기함/관심 |
| grateful | 고마움, 감동/감탄, 존경 |
| comfy | 편안/쾌적 |
| relieved | 안심/신뢰 |
| love | 아껴주는, 흐뭇함(귀여움/예쁨) |
| hope | 기대감 |
| sad | 슬픔, 불쌍함/연민, 서러움 |
| down | 절망, 패배/자기혐오, 죄책감 |
| hurt | 안타까움/실망, 서러움 |
| tired | 힘듦/지침, 귀찮음, 지긋지긋 |
| anxious | 불안/걱정, 공포/무서움, 당황/난처, 부담/안_내킴 |
| annoyed | 짜증, 불평/불만, 어이없음, 한심함, 역겨움/징그러움, 지긋지긋 |
| angry | 화남/분노, 증오/혐오 |
| unsure | 없음 |
| (버림) | 우쭐댐/무시함, 비장함, 의심/불신, 부끄러움, 깨달음, 경악, 재미없음, 놀람 |

KOTE에 없는 **serene, light, flutter, miss, lonely**는 직접 쓴 일기체 학습셋 `data_src/diary_train.jsonl`이 맡는다 (검증셋과 따로 작성).

## 감정 단어 사전 (`lexicon.json`)

일기에 감정 이름이 직접 나오면("홀가분", "보고 싶다") 해당 키 확률에 +0.3을 더한다(바로 앞이 안/않/못/없이면 무시). 검증셋 top-1 53.3% → 55.8%. 사전은 meta.json에 그대로 복사되므로 Kotlin은 meta만 읽으면 된다.
