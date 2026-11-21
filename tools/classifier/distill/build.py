"""gen.jsonl -> dedup + val-overlap filter -> corpus.jsonl ;  with label passes -> distill.jsonl
python -I build.py corpus        |  python -I build.py distill PASS1.jsonl [PASS2.jsonl]
"""
import json, re, sys
from pathlib import Path

import numpy as np
from sklearn.feature_extraction.text import TfidfVectorizer

REPO = Path(__file__).resolve().parents[1]
HERE = Path(__file__).resolve().parents[1] / "data/distill"  # gen.jsonl, corpus.jsonl, label passes (gitignored)
sys.path.insert(0, str(REPO))
from train import GROUP  # noqa: E402

RANK_W = [1.0, 0.7, 0.5]


def clean(t):
    t = re.sub(r"^(\d+\.|-|\*)\s*", "", t.strip()).strip().strip('"“”').strip()
    return t


def corpus():
    gen = [clean(json.loads(l)["text"]) for l in open(HERE / "gen.jsonl")]
    gen = [t for t in gen if 3 <= len(t) <= 450 and re.search(r"[가-힣]", t) and "조건" not in t and "소재:" not in t]
    val = [json.loads(l)["text"] for l in open(REPO / "eval/diary_val.jsonl")]
    vec = TfidfVectorizer(analyzer="char_wb", ngram_range=(2, 4), sublinear_tf=True).fit(gen + val)
    G, V = vec.transform(gen), vec.transform(val)
    keep, sims = [], (G @ G.T).tocsr()
    kept = np.zeros(len(gen), bool)
    for i in range(len(gen)):  # ponytail: greedy O(n * nnz) near-dup scan, fine for ~10k rows
        row = sims.getrow(i); js = row.indices[(row.data > 0.6)]
        if not kept[js[js < i]].any(): kept[i] = True
    vmax = np.asarray((G @ V.T).max(axis=1).todense()).ravel()
    ok = kept & (vmax < 0.5)
    out = [gen[i] for i in np.where(ok)[0]]
    print(f"gen {len(gen)} -> dedup {kept.sum()} -> no-val-overlap {len(out)} (max val sim kept {vmax[ok].max():.2f})")
    with open(HERE / "corpus.jsonl", "w") as f:
        for t in out: f.write(json.dumps({"text": t}, ensure_ascii=False) + "\n")


def soft(emo):
    y = {}
    for j, (k, _) in enumerate(emo): y[k] = max(y.get(k, 0), RANK_W[j])
    return y


def distill(paths):
    passes = [{(d := json.loads(l))["text"]: d for l in open(p)} for p in paths]
    rows, drop = [], 0
    for t, a in passes[0].items():
        ls = [p.get(t) for p in passes]
        if any(x is None or "err" in x for x in ls): drop += 1; continue
        if len({GROUP[x["emo"][0][0]] for x in ls}) > 1: drop += 1; continue
        ys = [soft(x["emo"]) for x in ls]
        y = {k: round(sum(s.get(k, 0) for s in ys) / len(ys), 2) for s in ys for k in s}
        rows.append({"text": t, "labels": [], "soft": y, "intensity": {k: i for k, i in a["emo"]}, "sarcasm": a["sarcasm"]})
    print(f"distill rows {len(rows)} dropped {drop}")
    with open(REPO / "data_src/distill.jsonl", "w") as f:
        for r in rows: f.write(json.dumps(r, ensure_ascii=False) + "\n")


if __name__ == "__main__":
    corpus() if sys.argv[1] == "corpus" else distill(sys.argv[2:])
