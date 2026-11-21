"""entities filter over the saved E2B/E4B outputs (raw/gemma4-e[24]b*.jsonl: reply A, summary S, tidy C).
usage: python3 -I entities_eval.py
  rejected = false rejects (every saved output was checked by hand: none invents a person or a number)
  mutated  = the same outputs with a real name/number swapped for an invented one; all should be caught
"""
import json, glob, sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
from filters import rule_ok  # noqa: E402

inputs = {}
for f in ["diaries", "tricky", "irony_holdout"]:
    for d in json.loads((HERE / f"data/{f}.json").read_text()): inputs[("D", d["id"])] = d["text"]
for t in json.loads((HERE / "data/transcripts.json").read_text()): inputs[("C", t["id"])] = t["text"]
TASK = {"A": "D", "S": "D", "C": "C"}
rows = []
for f in sorted(glob.glob(str(HERE / "raw/gemma4-e[24]b*.jsonl"))):
    for line in open(f):
        r = json.loads(line)
        inp = inputs.get((TASK.get(r["task"]), r["id"]))
        if inp and r.get("output"): rows.append((Path(f).name, r["id"], r["output"].strip(), inp))

bad = [x for x in rows if not rule_ok("entities", x[2], x[3])]
print(f"outputs {len(rows)}, rejected {len(bad)} ({len(bad) / len(rows):.1%})")
for x in bad: print("  rejected:", x[0], x[1], x[2][:70])

SWAP = [("서현이", "수빈이"), ("하은이", "예린이"), ("민준이", "도윤이"), ("지민이", "서연이"), ("박 팀장님", "김 부장님"),
        ("팀장님", "과장님"), ("엄마", "이모"), ("30분", "40분"), ("3년", "5년"), ("2주", "3주")]
mut = []
for (fn, i, out, inp) in rows:
    for real, fake in SWAP:
        if real in out and fake.split()[-1] not in inp:
            mut.append((fn, i, out.replace(real, fake, 1), inp)); break
missed = [m for m in mut if rule_ok("entities", m[2], m[3])]
print(f"mutated {len(mut)}, caught {len(mut) - len(missed)}")
for m in missed: print("  missed:", m[0], m[1], m[2][:70])
