"""Reference inference for shared/ai/classifier (D mirrors this in Kotlin).
Deps: onnxruntime, tokenizers, numpy.   python3 infer.py "오늘 너무 피곤했다"
Steps: tokenize (BERT WordPiece, [CLS] .. [SEP], truncate to maxLen) -> model(input_ids, attention_mask) int64 [1,L]
-> logits[21] -> sigmoid -> lexicon boost -> unsure check -> top emotion + keys with p >= thresholds[key] -> intensity rule -> sort by p, max 3.
"""
import json, re, sys
from pathlib import Path

import numpy as np
import onnxruntime as ort
from tokenizers import Tokenizer

SHARED = Path(__file__).resolve().parents[2] / "shared/ai/classifier"


class Classifier:
    def __init__(self, model=SHARED / "model.onnx", tokenizer=SHARED / "tokenizer.json", meta=SHARED / "meta.json"):
        self.sess = ort.InferenceSession(str(model), providers=["CPUExecutionProvider"])
        self.tok = Tokenizer.from_file(str(tokenizer))
        self.meta = json.loads(Path(meta).read_text()) if Path(meta).exists() else {"maxLen": 192}
        self.tok.enable_truncation(self.meta["maxLen"]); self.tok.no_padding()
        self.labels = json.loads((Path(model).parent / "labels.json").read_text()) if (Path(model).parent / "labels.json").exists() else None

    def probs(self, text):
        ids = np.array([self.tok.encode(text).ids], dtype=np.int64)
        logits = self.sess.run(None, {"input_ids": ids, "attention_mask": np.ones_like(ids)})[0][0]
        return 1 / (1 + np.exp(-logits))

    def boosted(self, text):
        p, m = self.probs(text).copy(), self.meta
        for k, rx in m.get("lexicon", {}).items():
            hit = re.search(rx, text)
            if hit and not re.search(r"(안|않|못|없)\s?$", text[max(0, hit.start() - 2):hit.start()]):
                i = self.labels.index(k); p[i] = min(0.99, p[i] + m["lexiconBoost"])
        return p

    def predict(self, text):
        m, p = self.meta, self.boosted(text)
        r = m["intensityRule"]
        top = max((v, k) for k, v in zip(self.labels, p) if k != "unsure")
        if top[0] < m["unsureFloor"] or p[self.labels.index("unsure")] > top[0]:
            return [{"key": "unsure", "p": round(float(p[self.labels.index("unsure")]), 3), "intensity": 3}]
        out = []
        for k, v in zip(self.labels, p):
            if k != "unsure" and (v >= m["thresholds"][k] or k == top[1]):
                x = min(1.0, max(0.0, (v - r["pLow"]) / (r["pHigh"] - r["pLow"])))
                i = r["base"] + round(r["span"] * x) + bool(re.search(r["boost"], text)) - bool(re.search(r["soften"], text))
                out.append({"key": k, "p": round(float(v), 3), "intensity": max(1, min(5, i))})
        out.sort(key=lambda e: -e["p"])
        return out[:3]


if __name__ == "__main__":
    print(json.dumps(Classifier().predict(" ".join(sys.argv[1:]) or "오늘 너무 피곤했다"), ensure_ascii=False))
