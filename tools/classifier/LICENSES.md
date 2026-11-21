# 라이선스 확인 (2026-11-21)

| 자료 | 출처 | 라이선스 (확인한 원문) | 상업 사용 | 사용 |
|---|---|---|---|---|
| KOTE 데이터셋 (train/val/test.tsv, raw.json) | github.com/searle-j/KOTE, huggingface.co/datasets/searle-j/kote | **MIT** — 저장소 `LICENSE` 원문 확인 (Copyright (c) 2022 Jeon Duyoung) | 가능 (저작권 고지 포함) | ✅ 학습 데이터 |
| monologg/koelectra-small-v3-discriminator | github.com/monologg/KoELECTRA | **Apache-2.0** — 저장소 `LICENSE` 원문 확인 (HF 카드에는 라이선스 표기 없음) | 가능 (고지·변경 표시) | ✅ 인코더 (14M 파라미터, int8 14.7MB) |
| beomi/KcELECTRA-base(-v2022) | huggingface.co/beomi | MIT (HF 태그) | 가능 | ❌ 110M 파라미터 → int8 ~110MB, 앱 30MB 목표 초과 |
| klue/roberta-small, klue/bert-base | github.com/KLUE-benchmark/KLUE | **CC BY-SA 4.0** (README) | 가능하나 파생 가중치에 동일조건(SA) 의무 | ❌ 의무 부담으로 제외 |
| AI Hub 감성 대화 등 | aihub.or.kr | 약관 미확인 | — | ❌ 사용 안 함 |
| diary_train.jsonl, eval/diary_val.jsonl | 직접 작성 | 자체 | — | ✅ |
| Gemma 4 E4B (google, GGUF) 생성 일기 + 감정 라벨 `data_src/distill.jsonl` | ai.google.dev/gemma, huggingface.co/google | **Apache-2.0** (Gemma 4) — 출력물 사용 제한 없음 | 가능 | ✅ 증류 학습 데이터 (교사 모델 출력, 2026-11-21 생성). EXAONE·HyperCLOVA 출력은 쓰지 않음 |
| tools/model-eval/data/diaries.json (30편) | 직접 작성 | 자체 | — | ✅ 검증셋에 포함 (m01–m30) |

앱 고지(오픈소스 라이선스 화면)에 넣을 것:
- KOTE: MIT 전문 + "Copyright (c) 2022 Jeon Duyoung"
- KoELECTRA: Apache-2.0 전문 + "Copyright 2020 Jangwon Park (monologg)", 미세조정했다는 변경 표시
