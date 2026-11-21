"""KOTE(soft labels from 5 raters) + diary_train.jsonl -> 21-key multi-label KoELECTRA-small.

Run: .venv/bin/python -I train.py [epochs]   (cwd = tools/classifier)
Output: out/model (HF format) used by evaluate.py / export.py
"""
import json, os, random, sys, time
from pathlib import Path

import torch
from transformers import AutoModelForSequenceClassification, AutoTokenizer

HERE = Path(__file__).resolve().parent
LABELS = ["joy", "proud", "excited", "grateful", "comfy", "serene", "relieved", "light",
          "flutter", "love", "hope", "miss", "sad", "down", "lonely", "hurt",
          "tired", "anxious", "annoyed", "angry", "unsure"]
GROUP = {k: g for g, ks in {"bright": LABELS[0:4], "calm": LABELS[4:8], "flutter": LABELS[8:12],
                            "low": LABELS[12:16], "worn": LABELS[16:20], "unsure": ["unsure"]}.items() for k in ks}
IDX = {k: i for i, k in enumerate(LABELS)}
BASE = HERE / "data/models/koelectra-small"
OUT = HERE / "out/model"
MAXLEN = 192
DIARY_REPEAT = 6  # diary-style lines are few; upweight


def kote_split(name):
    return {line.split("\t")[0] for line in (HERE / f"data/kote/repo/{name}.tsv").read_text().splitlines()}


def load_kote(ids):
    kmap = json.loads((HERE / "kote_map.json").read_text())
    raw = json.loads((HERE / "data/kote/raw.json").read_text())
    rows = []
    for i in ids:
        r = raw[i]
        y = [0.0] * len(LABELS)
        raters = list(r["labels"].values())
        for keys in raters:
            hit = {k for lab in keys for k in kmap[lab]}
            for k in hit:
                y[IDX[k]] += 1 / len(raters)
        # ponytail: one-rater votes are noise in KOTE; keep >=2/5 agreement only
        y = [v if v >= 0.4 else 0.0 for v in y]
        rows.append((r["text"], y))
    return rows


def load_diary(path):
    rows = []
    if not Path(path).exists():
        print("WARN: no", path); return rows
    for line in Path(path).read_text().splitlines():
        if line.strip():
            d = json.loads(line)
            y = [0.0] * len(LABELS)
            for j, k in enumerate(d["labels"]):
                y[IDX[k]] = 1.0 if j == 0 else 0.8
            for k, v in d.get("soft", {}).items():  # distilled rows (data_src/distill.jsonl): teacher soft targets
                y[IDX[k]] = v
            rows.append((d["text"], y))
    return rows


def main():
    epochs = int(sys.argv[1]) if len(sys.argv) > 1 else 4
    diary_only = "--diary-only" in sys.argv
    stage2 = "--stage2" in sys.argv  # continue from out/model on diary data + small KOTE replay
    random.seed(0); torch.manual_seed(0)
    dev = "mps" if torch.backends.mps.is_available() else "cpu"
    kote = load_kote(kote_split("train"))
    diary = load_diary(HERE / "data_src/diary_train.jsonl")
    distill = load_diary(HERE / "data_src/distill.jsonl") if "--distill" in sys.argv else []
    train = (diary * 2 + distill + random.sample(kote, 3000) if stage2 else diary if diary_only
             else kote + diary * DIARY_REPEAT + distill * 2)
    random.shuffle(train)
    arg = lambda k, d: Path(sys.argv[sys.argv.index(k) + 1]) if k in sys.argv else d
    src, out = arg("--src", OUT if stage2 else BASE), arg("--out", OUT)
    tok = AutoTokenizer.from_pretrained(src)
    model = AutoModelForSequenceClassification.from_pretrained(
        src, num_labels=len(LABELS), problem_type="multi_label_classification").to(dev)
    lr = 5e-5 if stage2 else 2e-4
    opt = torch.optim.AdamW(model.parameters(), lr=lr, weight_decay=0.01)
    bs = 64
    steps = epochs * (len(train) // bs)
    sched = torch.optim.lr_scheduler.OneCycleLR(opt, max_lr=lr, total_steps=steps, pct_start=0.06)
    lossf = torch.nn.BCEWithLogitsLoss()
    model.train(); t0 = time.time(); step = 0
    for ep in range(epochs):
        random.shuffle(train)
        for b in range(0, len(train) - bs + 1, bs):
            batch = train[b:b + bs]
            enc = tok([t for t, _ in batch], truncation=True, max_length=MAXLEN, padding=True, return_tensors="pt").to(dev)
            y = torch.tensor([y for _, y in batch], device=dev)
            loss = lossf(model(**enc).logits, y)
            loss.backward(); opt.step(); sched.step(); opt.zero_grad(); step += 1
            if step % 200 == 0:
                print(f"ep{ep} step{step}/{steps} loss {loss.item():.4f} {time.time()-t0:.0f}s", flush=True)
    model.config.id2label = dict(enumerate(LABELS)); model.config.label2id = IDX
    out.mkdir(parents=True, exist_ok=True)
    model.save_pretrained(out); tok.save_pretrained(out)
    print("saved", out)


if __name__ == "__main__":
    main()
