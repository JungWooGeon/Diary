"""Reference inference for shared/ai/classifier (D mirrors this in Kotlin).
Deps: ai-edge-litert (shipped model.tflite) or onnxruntime (a .onnx path), tokenizers, numpy.   .venv-tfl/bin/python -I infer.py "오늘 너무 피곤했다"
Steps: tokenize (BERT WordPiece, [CLS] .. [SEP], truncate to maxLen) -> model(input_ids, attention_mask) [1,L]
(TFLite: int32, right-padded to maxLen with mask 0; ONNX: int64, unpadded)
-> logits[21] (+ embedding[256]) -> sigmoid -> lexicon boost -> unsure check -> top emotion + keys with p >= thresholds[key] -> intensity rule -> sort by p, max 3.
"""
import json, re, sys
from pathlib import Path

import numpy as np
from tokenizers import Tokenizer

SHARED = Path(__file__).resolve().parents[2] / "shared/ai/classifier"


class Classifier:
    def __init__(self, model=SHARED / "model.tflite", tokenizer=SHARED / "tokenizer.json", meta=SHARED / "meta.json"):
        if str(model).endswith(".onnx"):
            import onnxruntime as ort
            self.sess = ort.InferenceSession(str(model), providers=["CPUExecutionProvider"])
        else:
            from ai_edge_litert.interpreter import Interpreter
            self.run = Interpreter(model_path=str(model)).get_signature_runner("classify")
        self.tok = Tokenizer.from_file(str(tokenizer))
        self.meta = json.loads(Path(meta).read_text()) if Path(meta).exists() else {"maxLen": 192}
        self.tok.enable_truncation(self.meta["maxLen"]); self.tok.no_padding()
        self.labels = json.loads((SHARED / "labels.json").read_text())

    def probs(self, text):
        ids = self.tok.encode(text).ids
        if hasattr(self, "sess"):
            a = np.array([ids], dtype=np.int64)
            logits = self.sess.run(None, {"input_ids": a, "attention_mask": np.ones_like(a)})[0][0]
        else:
            logits = self._tflite(ids)["logits"][0]
        return 1 / (1 + np.exp(-logits))

    def _tflite(self, ids):
        x = np.zeros((1, self.meta["maxLen"]), np.int32); x[0, :len(ids)] = ids
        return self.run(input_ids=x, attention_mask=(x != 0).astype(np.int32))

    def embedding(self, text):
        """256-d L2-normalised sentence vector (tflite only): cosine = dot product."""
        return self._tflite(self.tok.encode(text).ids)["embedding"][0]

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
