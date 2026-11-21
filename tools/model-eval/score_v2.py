"""v2 auto metrics: post-filter hits per run, sarcasm-reader accuracy, emotion JSON, manual grade averages.
usage: python3 -I score_v2.py run1 [run2 ...]   (run = raw/<run>.jsonl stem, e.g. gemma4-e2b.v4)
Manual grades: grades2/*.txt lines 'model id nat emp fact instr' (model = e2b|e4b, file name = run tag)."""
import json, statistics as st, sys
from collections import Counter
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
from filters import check  # noqa: E402
from score import parse_json, schema_ok  # noqa: E402

data = {d["id"]: d for f in ("diaries", "tricky", "irony_holdout", "transcripts", "retro") for d in json.loads((HERE / f"data/{f}.json").read_text())}
IRONY = {i for i, d in data.items() if d.get("irony")} | {"d21", "d22"}
SINCERE = {i for i, d in data.items() if i.startswith("h") and not d.get("irony")}
NEG = {"슬픔", "우울", "외로움", "서운함", "피곤", "불안", "짜증", "화남"}
TASK = {"A": "reply", "C": "tidy", "T": "title", "S": "summary", "R": "retro", "Q": "question"}

for run in sys.argv[1:]:
    rows = [json.loads(l) for l in (HERE / f"raw/{run}.jsonl").read_text().splitlines() if l.strip()]
    print(f"== {run}")
    for t in "ACTSRQ":
        rs = [r for r in rows if r["task"] == t]
        if not rs:
            continue
        hits = Counter(f for r in rs for f in check(TASK[t], r["output"], data.get(r["id"], {}).get("text", "")))
        clean = sum(not check(TASK[t], r["output"], data.get(r["id"], {}).get("text", "")) for r in rs)
        print(f"  {t} n={len(rs)} pass={clean} hits={dict(hits)} tps={st.median(r['gen_tps'] for r in rs):.0f}")
    a = [r for r in rows if r["task"] == "A" and "reading" in r]
    if a:
        flag = {r["id"] for r in a if r.get("reading") and r["reading"].get("sarcasm") and r["reading"].get("bad")}
        ids = {r["id"] for r in a}
        print(f"  sarcasm hint: TP {len(flag & IRONY)}/{len(IRONY & ids)} FP {sorted(flag - IRONY)} "
              f"(sincere-praise held-out FP {len(flag & SINCERE)}/{len(SINCERE & ids)}) "
              f"reader calls {sum(1 for r in a if r.get('reading') is not None)}/{len(a)}")
    b = [r for r in rows if r["task"] == "B" and r["id"] in data and "gold" in data[r["id"]]]
    if b:
        objs = [parse_json(r["output"]) for r in b]
        valid = sum(ok and schema_ok(o) for ok, o in objs)
        agree = sum(schema_ok(o) and o["emotions"][0]["name"] in data[r["id"]]["gold"] for r, (_, o) in zip(b, objs))
        irn = sum(schema_ok(o) and o["emotions"][0]["name"] in NEG for r, (_, o) in zip(b, objs) if r["id"] in IRONY)
        print(f"  B n={len(b)} strict-valid={valid} top1-agree={agree} irony->negative={irn}/{len(IRONY & {r['id'] for r in b})}")

grades = {}
for f in sorted((HERE / "grades2").glob("*.txt")):
    for line in f.read_text().splitlines():
        p = line.split()
        if p and not p[0].startswith("#"):
            grades.setdefault((f.stem, p[0]), {})[p[1]] = list(map(int, p[2:6]))
for (tag, m), g in grades.items():
    for name, ids in (("d30", [f"d{i:02d}" for i in range(1, 31)]), ("x20", [f"x{i:02d}" for i in range(1, 21)]),
                      ("h12", [f"h{i:02d}" for i in range(1, 13)])):
        ids = [i for i in ids if i in g]
        if ids:
            cols = [round(st.mean(c), 2) for c in zip(*(g[i] for i in ids))]
            print(f"grades {tag} {m} {name}: nat/emp/fact/instr {cols} avg {round(st.mean(cols), 2)}")
