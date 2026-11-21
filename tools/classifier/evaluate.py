"""Evaluate out/model on eval/diary_val.jsonl, tune thresholds + intensity rule, write out/tuned.json.

Run: .venv/bin/python -I evaluate.py [onnx_path]   (a .tflite path needs .venv-tfl)
Metrics are 2-fold (tune on one half, score on the other) so they are not tuned-on-test.
"""
import json, re, sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent))
from train import GROUP, IDX, LABELS, MAXLEN, OUT  # noqa: E402

HERE = Path(__file__).resolve().parent
GROUPS = ["bright", "calm", "flutter", "low", "worn", "unsure"]
EMO = LABELS[:20]
BOOST = r"너무|진짜|정말|완전|엄청|개빡|개좋|개웃|미친|미쳤|최악|최고|죽겠|ㅠㅠ|ㅜㅜ|!!|펑펑|미치겠"
SOFTEN = r"조금|좀|살짝|약간|그럭저럭|나름|괜찮았"
GRID = [round(x, 2) for x in np.arange(0.1, 0.71, 0.05)]


def probs_torch(texts, path=None):
    import torch
    from transformers import AutoModelForSequenceClassification, AutoTokenizer
    tok = AutoTokenizer.from_pretrained(path or OUT); m = AutoModelForSequenceClassification.from_pretrained(path or OUT).eval()
    out = []
    with torch.no_grad():
        for i in range(0, len(texts), 32):
            enc = tok(texts[i:i + 32], truncation=True, max_length=MAXLEN, padding=True, return_tensors="pt")
            out.append(torch.sigmoid(m(**enc).logits).numpy())
    return np.concatenate(out)


def probs_file(texts, path):
    """shipped .tflite (needs .venv-tfl) or .onnx, through infer.py like the app."""
    sys.path.insert(0, str(HERE))
    from infer import Classifier
    c = Classifier(path, HERE / "out/model/tokenizer.json")
    return np.stack([c.probs(t) for t in texts])


LEXICON = json.loads((HERE / "lexicon.json").read_text())
NEG = r"(안|않|못|없)"


def apply_lexicon(P, texts, boost):
    """p[key] += boost when the diary names the feeling (not right after 안/못), capped at 0.99."""
    P = P.copy()
    for i, t in enumerate(texts):
        for k, rx in LEXICON.items():
            m = re.search(rx, t)
            if m and not re.search(NEG + r"\s?$", t[max(0, m.start() - 2):m.start()]):
                P[i, IDX[k]] = min(0.99, P[i, IDX[k]] + boost)
    return P


def load_val():
    rows = [json.loads(l) for l in (HERE / "eval/diary_val.jsonl").read_text().splitlines() if l.strip()]
    return rows


def predict_group(p, floor):
    j = int(np.argmax(p[:20]))
    return "unsure" if p[j] < floor or p[IDX["unsure"]] > p[j] else GROUP[EMO[j]]


def f1(tp, fp, fn):
    return 2 * tp / (2 * tp + fp + fn) if tp else 0.0


def tune_thresholds(P, Y):
    th = {}
    for k in LABELS:
        j = IDX[k]
        best = max(GRID, key=lambda t: (f1(((P[:, j] >= t) & Y[:, j]).sum(), ((P[:, j] >= t) & ~Y[:, j]).sum(),
                                           ((P[:, j] < t) & Y[:, j]).sum()), -abs(t - 0.35)))
        th[k] = round(0.5 * best + 0.5 * 0.35, 3)  # ponytail: shrink toward global — ~15 positives/label is too few to trust per-label F1 peaks
    return th


def tune_floor(P, rows):
    return max(GRID, key=lambda f: sum(predict_group(p, f) == r["group"] for p, r in zip(P, rows)))


def group_sets(p, th):
    s = {GROUP[k] for k in EMO if p[IDX[k]] >= th[k]}
    return s or {"unsure"}


def intensity(p, t, text, rule):
    lo, hi = rule["pLow"], rule["pHigh"]
    x = min(1.0, max(0.0, (p - lo) / (hi - lo)))
    v = rule["base"] + round(rule["span"] * x)
    if re.search(rule["boost"], text): v += 1
    if re.search(rule["soften"], text): v -= 1
    return max(1, min(5, v))


def score(P, rows, th, floor, rule):
    acc = np.mean([predict_group(p, floor) == r["group"] for p, r in zip(P, rows)])
    per = {}
    for g in GROUPS:
        tp = fp = fn = 0
        for p, r in zip(P, rows):
            pred, gold = group_sets(p, th), {GROUP[k] for k in r["labels"]}
            tp += g in pred and g in gold; fp += g in pred and g not in gold; fn += g not in pred and g in gold
        per[g] = round(f1(tp, fp, fn), 3)
    errs = []
    for p, r in zip(P, rows):
        k = r["labels"][0]
        if k != "unsure" and "intensity" in r:
            errs.append(intensity(p[IDX[k]], th[k], r["text"], rule) - r["intensity"][k])
    return acc, per, (float(np.mean(np.abs(errs))) if errs else None, float(np.mean(np.abs(errs) <= 1)) if errs else None)


def best_rule(P, rows, th):
    cands = [{"pLow": lo, "pHigh": hi, "base": b, "span": sp, "boost": BOOST, "soften": SOFTEN}
             for lo in (0.05, 0.1, 0.2) for hi in (0.5, 0.6, 0.8) for b, sp in ((1, 4), (2, 3), (2, 2), (3, 2), (3, 1), (3, 0))]
    return min(cands, key=lambda r: score(P, rows, th, 0.3, r)[2][0])


def main():
    rows = load_val()
    texts = [r["text"] for r in rows]
    arg = next((x for x in sys.argv[1:] if not x.startswith("--")), None)
    P = probs_file(texts, arg) if arg and arg.endswith((".onnx", ".tflite")) else probs_torch(texts, arg)
    lexBoost = float(next((x.split("=")[1] for x in sys.argv if x.startswith("--lex=")), 0.3))
    P = apply_lexicon(P, texts, lexBoost)
    Y = np.array([[k in r["labels"] for k in LABELS] for r in rows])
    halves = [np.arange(len(rows)) % 2 == i for i in (0, 1)]
    res, style2 = [], {}
    for a, b in (halves, halves[::-1]):
        ra, rb = [r for r, m in zip(rows, a) if m], [r for r, m in zip(rows, b) if m]
        th = tune_thresholds(P[a], Y[a]); fl = tune_floor(P[a], ra); rule = best_rule(P[a], ra, th)
        res.append(score(P[b], rb, th, fl, rule))
        for p, r in zip(P[b], rb):
            for s in r.get("style", []) or ["(none)"]:
                style2.setdefault(s, []).append(predict_group(p, fl) == r["group"])
    acc = np.mean([r[0] for r in res])
    per = {g: round(np.mean([r[1][g] for r in res]), 3) for g in GROUPS}
    mae = np.mean([r[2][0] for r in res]); w1 = np.mean([r[2][1] for r in res])
    # final (shipped) params: tuned on all of val
    th = tune_thresholds(P, Y); fl = tune_floor(P, rows); rule = best_rule(P, rows, th)
    by_style = {}
    for p, r in zip(P, rows):
        for s in r.get("style", []) or ["(none)"]:
            by_style.setdefault(s, []).append(predict_group(p, fl) == r["group"])
    report = {"n": len(rows), "top1GroupAcc_2fold": round(float(acc), 3), "groupF1_2fold": per,
              "intensityMAE_2fold": round(float(mae), 2), "intensityWithin1_2fold": round(float(w1), 3),
              "top1GroupAcc_byStyle_2fold": {s: (round(float(np.mean(v)), 3), len(v)) for s, v in sorted(style2.items())},
              "top1GroupAcc_byStyle_fullfit": {s: (round(float(np.mean(v)), 3), len(v)) for s, v in sorted(by_style.items())},
              "thresholds": th, "unsureFloor": fl, "lexiconBoost": lexBoost, "intensityRule": rule}
    print(json.dumps(report, ensure_ascii=False, indent=1))
    (HERE / "out").mkdir(exist_ok=True)
    if (arg and not arg.endswith((".onnx", ".tflite"))) or "--no-save" in sys.argv: return  # experiment run: don't overwrite shipped tuning
    (HERE / "out/tuned.json").write_text(json.dumps(report, ensure_ascii=False, indent=1))


if __name__ == "__main__":
    main()
