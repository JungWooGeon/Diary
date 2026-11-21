"""print outputs side by side. usage: python3 -I show.py <task> [id ...]"""
import json, sys
from pathlib import Path
HERE = Path(__file__).resolve().parent
ORDER = ["gemma4-e2b", "gemma4-e4b", "kanana15-2.1b", "hcx-seed-1.5b", "qwen3-1.7b", "qwen3-4b-2507",
         "qwen3.5-2b", "qwen3.5-4b", "gemma3-4b", "exaone4-1.2b"]
task, ids = sys.argv[1], sys.argv[2:]
src = {**{d["id"]: d["text"] for d in json.loads((HERE / "data/diaries.json").read_text())},
       **{t["id"]: t["text"] for t in json.loads((HERE / "data/transcripts.json").read_text())}}
data = {m: {r["id"]: r for r in map(json.loads, (HERE / f"raw/{m}.jsonl").read_text().splitlines()) if r["task"] == task}
        for m in ORDER}
for i in ids or sorted(data[ORDER[0]]):
    print(f"######## {task} {i}: {src[i]}")
    for m in ORDER:
        r = data[m][i]
        print(f"--- {m} [{r['finish']}] {r['output']}")
