"""Reference crisis detector (D mirrors this in Kotlin) + assertions on shared/ai/crisis.json testCases.
Run: python3 tools/classifier/test_crisis.py"""
import json, re, sys
from pathlib import Path

CFG = json.loads((Path(__file__).resolve().parents[2] / "shared/ai/crisis.json").read_text())


def score(text, cfg=CFG):
    t = re.sub(cfg["normalize"]["removeRegex"], "", text.lower())
    for e in cfg["excludes"]:
        t = re.sub(e["regex"], "", t)
    hits = [p for p in cfg["patterns"] if re.search(p["regex"], t)]
    return sum(p["weight"] for p in hits), [p["id"] for p in hits]


def is_crisis(text, cfg=CFG):
    return score(text, cfg)[0] >= cfg["threshold"]


if __name__ == "__main__":
    pos, neg = CFG["testCases"]["positive"], CFG["testCases"]["negative"]
    missed = [t for t in pos if not is_crisis(t)]
    fps = [(t, score(t)) for t in neg if is_crisis(t)]
    rec, spec = 1 - len(missed) / len(pos), 1 - len(fps) / len(neg)
    print(f"positive {len(pos)} recall {rec:.1%} | negative {len(neg)} specificity {spec:.1%}")
    for t in missed: print("  MISS", t, score(t))
    for t, s in fps: print("  FP  ", t, s)
    assert len(pos) >= 80 and len(neg) >= 80
    assert rec == 1.0, "positive recall must be 100%"
    assert spec >= 0.95, "negative specificity must be >= 95%"
