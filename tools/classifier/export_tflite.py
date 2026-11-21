"""out/model -> shared/ai/classifier/model.tflite (int8 dynamic-range weights); checks it against PyTorch and the int8 ONNX.
Separate venv (litert-torch pins torch<2.15):  .venv-tfl/bin/python -I export_tflite.py
Graph (signature "classify"): int32 input_ids [1,192] + attention_mask [1,192] (0 = padding) -> logits [1,21] + embedding [1,256].
embedding = masked mean of hidden states of layers EMB_LAYERS, L2-normalised (same weights, no extra params; see embed_check.py).
One fixed length: each extra signature (e.g. 64 for short diaries) costs +1.5MB of per-subgraph quant params.
export.py still writes the ONNX to out/ (kept for a future web build: onnxruntime-web) and meta.json.
Also rewrites core/ai's classifier_reference.json (Kotlin tokenizer + decision parity test) from the new model.
"""
import json, sys
from pathlib import Path

import numpy as np
import torch
import litert_torch
from ai_edge_quantizer import quantizer, recipe
from transformers import AutoModelForSequenceClassification

sys.path.insert(0, str(Path(__file__).resolve().parent))
from train import MAXLEN, OUT  # noqa: E402
from evaluate import apply_lexicon, load_val, predict_group, probs_file, probs_torch  # noqa: E402
from infer import Classifier  # noqa: E402

HERE = Path(__file__).resolve().parent
DST = HERE.parents[1] / "shared/ai/classifier"
ONNX = HERE / "out/model_int8.onnx"  # export.py (web build); the app ships model.tflite only
REF = HERE.parents[1] / "core/ai/src/test/resources/classifier_reference.json"


EMB_LAYERS = (4, 5, 6)  # mid layers: the last ones only know the feeling (embed_check.py: L12 hardAuc 0.50, L4-6 0.78)


class Wrap(torch.nn.Module):
    def __init__(self, m): super().__init__(); self.m = m
    def forward(self, input_ids, attention_mask):
        o = self.m(input_ids=input_ids.long(), attention_mask=attention_mask.long(), output_hidden_states=True)
        mask = attention_mask.float().unsqueeze(-1)
        h = sum(o.hidden_states[i] for i in EMB_LAYERS)
        v = (h * mask).sum(1) / mask.sum(1).clamp(min=1.0)
        return {"logits": o.logits, "embedding": v / v.norm(dim=-1, keepdim=True).clamp(min=1e-6)}


def main():
    w = Wrap(AutoModelForSequenceClassification.from_pretrained(OUT).eval()).eval()
    z = {"input_ids": torch.zeros(1, MAXLEN, dtype=torch.int32), "attention_mask": torch.ones(1, MAXLEN, dtype=torch.int32)}
    fp32 = HERE / "out/model_fp32.tflite"
    litert_torch.signature("classify", w, sample_kwargs=z).convert().export(str(fp32))
    q = quantizer.Quantizer(str(fp32))
    q.load_quantization_recipe(recipe.dynamic_wi8_afp32())  # per-channel int8 weights (MatMul + embedding Gather), fp32 activations
    q.quantize().export_model(str(DST / "model.tflite"), overwrite=True)

    rows = load_val(); texts = [r["text"] for r in rows]
    pt, p32, p8 = probs_torch(texts), probs_file(texts, fp32), probs_file(texts, DST / "model.tflite")
    print(f"n={len(rows)}  max|torch-tfl32| {np.abs(pt - p32).max():.2e}  max|torch-tfl8| {np.abs(pt - p8).max():.3f}")
    floor = json.loads((DST / "meta.json").read_text())["unsureFloor"]
    g = lambda P: [predict_group(p, floor) for p in apply_lexicon(P, texts, 0.3)]
    g8 = g(p8)
    for name, P in (("torch", pt), ("onnx-int8", probs_file(texts, ONNX) if ONNX.exists() else None)):
        if P is not None: print(f"top-1 group agreement tflite-int8 vs {name}: {np.mean([a == b for a, b in zip(g8, g(P))]):.3f}")
    print("size MB", round((DST / "model.tflite").stat().st_size / 1e6, 1))
    assert np.abs(pt - p32).max() < 1e-3, "fp32 TFLite must match PyTorch"
    # Kotlin RulesTest replays these: same ids from WordPieceTokenizer, same decision from these probs
    c = Classifier()
    ref = [{"text": t, "ids": c.tok.encode(t).ids, "probs": [round(float(x), 6) for x in c.probs(t)], "predict": c.predict(t)}
           for t in (r["text"] for r in json.loads(REF.read_text()))]
    REF.write_text(json.dumps(ref, ensure_ascii=False))


if __name__ == "__main__":
    main()
