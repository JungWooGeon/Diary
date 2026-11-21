"""Sentence-embedding sanity check on data_src/embed_check.json (pairs AUC + query->diary top-3 over tools/model-eval 30 diaries).

  .venv-tfl/bin/python -I embed_check.py              # PyTorch out/model, every layer x {mean, cls} + char n-gram baseline
  .venv-tfl/bin/python -I embed_check.py tflite       # shipped model.tflite "embedding" output (what the app runs), alone and + bigram overlap
AUC = P(cos(similar pair) > cos(different pair)); hardAUC uses only same-feeling/different-situation negatives.
"""
import json, sys
from collections import Counter
from pathlib import Path

import numpy as np

HERE = Path(__file__).resolve().parent
D = json.loads((HERE / "data_src/embed_check.json").read_text())
DIARIES = json.loads((HERE.parents[0] / "model-eval/data/diaries.json").read_text())


def auc(pos, neg):
    return float(np.mean([(p > n) + 0.5 * (p == n) for p in pos for n in neg]))


def bigrams(t):
    return {w[i:i + 2] for w in t.split() for i in range(max(1, len(w) - 1))}


def overlap(q, d):
    """Share of the query's char bigrams found in the diary: EmbeddingIndex.search's lexical half (Kotlin mirrors this)."""
    b = bigrams(q); return len(b & bigrams(d)) / max(1, len(b))


def score(embed, name, lex=0.0):
    """embed(list[str]) -> L2-normalised [n, d]. lex > 0: search score = (1-lex)*cos + lex*overlap."""
    pairs = D["pairs"]
    A, B = embed([p["a"] for p in pairs]), embed([p["b"] for p in pairs])
    cos = (A * B).sum(1)
    pos = cos[[p["sim"] == 1 for p in pairs]]
    neg = cos[[p["sim"] == 0 for p in pairs]]
    hard = cos[[p.get("hard", 0) == 1 for p in pairs]]
    docs = embed([d["text"] for d in DIARIES]); ids = [d["id"] for d in DIARIES]
    Q = embed([q["q"] for q in D["queries"]])
    hit3, rr, tops, para = [], [], [], []
    for q, v in zip(D["queries"], Q):
        s = (1 - lex) * (docs @ v) + lex * np.array([overlap(q["q"], d["text"]) for d in DIARIES])
        order = [ids[i] for i in np.argsort(-s)]
        hit3.append(any(h in order[:3] for h in q["hit"])); para.append(q.get("para", 0) == 1)
        rr.append(1 / (1 + min(order.index(h) for h in q["hit"])))
        tops.append((q["q"], order[:3]))
    r = {"name": name, "auc": round(auc(pos, neg), 3), "hardAuc": round(auc(pos, hard), 3),
         "simMean": round(float(pos.mean()), 3), "diffMean": round(float(neg.mean()), 3),
         "hit@3": round(float(np.mean(hit3)), 3), "mrr": round(float(np.mean(rr)), 3),
         "hit@3 lexical q": round(float(np.mean([h for h, p in zip(hit3, para) if not p])), 3),
         "hit@3 paraphrase q": round(float(np.mean([h for h, p in zip(hit3, para) if p])), 3)}
    return r, tops


def ngram_embed(texts):
    """Lexical baseline: char 2-3gram TF-IDF (what a pure-Kotlin search could do without a model)."""
    grams = lambda t: Counter(g for n in (2, 3) for w in t.split() for g in (w[i:i + n] for i in range(max(1, len(w) - n + 1))))
    allt = [d["text"] for d in DIARIES] + [q["q"] for q in D["queries"]] + [p[k] for p in D["pairs"] for k in "ab"]
    df = Counter(g for t in allt for g in set(grams(t)))
    vocab = {g: i for i, g in enumerate(df)}
    X = np.zeros((len(texts), len(vocab)))
    for r, t in enumerate(texts):
        for g, c in grams(t).items():
            X[r, vocab[g]] = (1 + np.log(c)) * np.log(len(allt) / df[g])
    return X / np.maximum(np.linalg.norm(X, axis=1, keepdims=True), 1e-9)


def torch_embedders(path):
    import torch
    from transformers import AutoModel, AutoTokenizer
    tok, m = AutoTokenizer.from_pretrained(path), AutoModel.from_pretrained(path, output_hidden_states=True).eval()
    cache = {}

    def hs(texts):
        key = tuple(texts)
        if key not in cache:
            with torch.no_grad():
                enc = tok(texts, truncation=True, max_length=192, padding=True, return_tensors="pt")
                cache[key] = ([h.numpy() for h in m(**enc).hidden_states], enc["attention_mask"].numpy()[..., None])
        return cache[key]

    def make(layer, pool):
        def f(texts):
            H, M = hs(texts); h = H[layer]
            v = h[:, 0] if pool == "cls" else (h * M).sum(1) / M.sum(1)
            return v / np.linalg.norm(v, axis=1, keepdims=True)
        return f
    return make, m.config.num_hidden_layers


_clf = []
def tflite_embed(texts):
    from infer import Classifier
    if not _clf: _clf.append(Classifier())
    return np.stack([_clf[0].embedding(t) for t in texts])


def main():
    sys.path.insert(0, str(HERE))
    rows = []
    if sys.argv[1:2] == ["tflite"]:
        cache = {}
        emb = lambda ts: np.stack([cache.setdefault(t, tflite_embed([t])[0]) for t in ts])
        rows.append(score(emb, "tflite-int8 embedding"))
        for lex in (0.3, 0.4, 0.5):
            rows.append(score(emb, f"tflite-int8 embedding + bigram overlap {lex}", lex))
        rows.append(score(lambda ts: np.zeros((len(ts), 1)), "bigram overlap only", 1.0))
    else:
        rows.append(score(ngram_embed, "char-ngram tfidf"))
        for path, tag in ((HERE / "out/model", "ft"), (HERE / "data/models/koelectra-small", "base")):
            make, n = torch_embedders(str(path))
            for layer in range(1, n + 1):
                for pool in ("mean", "cls"):
                    rows.append(score(make(layer, pool), f"{tag} L{layer} {pool}"))
    for r, _ in rows: print(json.dumps(r, ensure_ascii=False))
    best = max(rows, key=lambda r: r[0]["hit@3"] + r[0]["auc"])
    print("best:", best[0]["name"])
    for q, top in best[1]: print(f"  {q} -> {top}")


if __name__ == "__main__":
    main()
