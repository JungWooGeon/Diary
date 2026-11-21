"""out/model -> shared/ai/classifier/{tokenizer.json, labels.json, meta.json} + out/model_int8.onnx; checks ONNX == PyTorch.
Run after evaluate.py:  .venv/bin/python -I export.py   (then .venv-tfl/bin/python -I export_tflite.py for the app's model.tflite)
The ONNX is not shipped in the app (LiteRT runs model.tflite); it is kept for the future web app (onnxruntime-web).
"""
import json, shutil, sys
from pathlib import Path

import numpy as np
import torch
from onnxruntime.quantization import QuantType, quantize_dynamic
from transformers import AutoModelForSequenceClassification, AutoTokenizer

sys.path.insert(0, str(Path(__file__).resolve().parent))
from train import LABELS, MAXLEN, OUT  # noqa: E402

HERE = Path(__file__).resolve().parent
DST = HERE.parents[1] / "shared/ai/classifier"


class Wrap(torch.nn.Module):
    def __init__(self, m): super().__init__(); self.m = m
    def forward(self, input_ids, attention_mask): return self.m(input_ids=input_ids, attention_mask=attention_mask).logits


def main():
    DST.mkdir(parents=True, exist_ok=True)
    tok = AutoTokenizer.from_pretrained(OUT); m = AutoModelForSequenceClassification.from_pretrained(OUT).eval()
    tok.backend_tokenizer.save(str(OUT / "tokenizer.json"))
    fp32 = HERE / "out/model_fp32.onnx"
    enc = tok(["오늘 너무 좋았다"], return_tensors="pt")
    torch.onnx.export(Wrap(m).eval(), (enc["input_ids"], enc["attention_mask"]), str(fp32), input_names=["input_ids", "attention_mask"],
                      output_names=["logits"], dynamic_axes={"input_ids": {0: "b", 1: "L"}, "attention_mask": {0: "b", 1: "L"}, "logits": {0: "b"}},
                      opset_version=17, dynamo=False)
    # per-channel scales: per-tensor int8 drifted top-1 on 8/100 diaries, per-channel on 4/100 (14.9MB)
    int8 = HERE / "out/model_int8.onnx"
    quantize_dynamic(str(fp32), str(int8), weight_type=QuantType.QInt8, per_channel=True, op_types_to_quantize=["MatMul", "Gather"])
    shutil.copy(OUT / "tokenizer.json", DST / "tokenizer.json")
    (DST / "labels.json").write_text(json.dumps(LABELS))
    tuned = json.loads((HERE / "out/tuned.json").read_text())
    meta = {"version": 1, "base": "monologg/koelectra-small-v3-discriminator (Apache-2.0)", "train": "KOTE (MIT) + diary_train (self-written) + distill (Gemma 4 E4B generated+labeled, Apache-2.0)",
            "maxLen": MAXLEN, "inputs": ["input_ids", "attention_mask"], "output": "logits[21] -> sigmoid",
            "thresholds": tuned["thresholds"], "unsureFloor": tuned["unsureFloor"],
            "lexicon": json.loads((HERE / "lexicon.json").read_text()), "lexiconBoost": tuned["lexiconBoost"],
            "lexiconRule": "p[key] = min(0.99, p[key] + lexiconBoost) when re.search(lexicon[key], text) and the 2 chars before the match do not end with 안|않|못|없 (+ optional space)",
            "intensityRule": {**tuned["intensityRule"], "formula": "i = base + round(span*clamp((p-pLow)/(pHigh-pLow),0,1)) + (boost matches) - (soften matches), clamp 1..5"},
            "decision": "after lexicon boost: if max emotion p < unsureFloor or p(unsure) > max emotion p -> [unsure]; else the top emotion plus every other key with p >= thresholds[key], sorted by p, max 3",
            "eval": {k: tuned[k] for k in ("n", "top1GroupAcc_2fold", "groupF1_2fold", "intensityMAE_2fold", "intensityWithin1_2fold")}}
    (DST / "meta.json").write_text(json.dumps(meta, ensure_ascii=False, indent=1))

    from infer import Classifier
    c32, c8 = Classifier(fp32, OUT / "tokenizer.json", DST / "meta.json"), Classifier(int8, DST / "tokenizer.json", DST / "meta.json")
    rows = [json.loads(l)["text"] for l in (HERE / "eval/diary_val.jsonl").read_text().splitlines()[:100]]
    d32 = d8 = agree = 0
    with torch.no_grad():
        for t in rows:
            e = tok(t, truncation=True, max_length=MAXLEN, return_tensors="pt")
            pt = torch.sigmoid(m(**e).logits)[0].numpy()
            p32, p8 = c32.probs(t), c8.probs(t)
            d32 = max(d32, np.abs(pt - p32).max()); d8 = max(d8, np.abs(pt - p8).max()); agree += pt.argmax() == p8.argmax()
    print(f"max|torch-onnx32| {d32:.2e}  max|torch-int8| {d8:.3f}  top1 agree int8 {agree}/{len(rows)}")
    print("size MB", round(int8.stat().st_size / 1e6, 1))
    assert d32 < 1e-3, "fp32 ONNX must match PyTorch"


if __name__ == "__main__":
    main()
