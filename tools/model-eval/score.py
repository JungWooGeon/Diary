"""Auto metrics + merge of manual grades -> results/summary.json
usage: python3 -I score.py
Manual grades: grades/<model>.tsv  lines: task<TAB>id<TAB>nat<TAB>emp<TAB>fact<TAB>instr<TAB>note  ('-' = n/a)
"""
import json, re, statistics, sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
from prompts import EMOTIONS  # noqa: E402

gold = {d["id"]: d for f in ("diaries", "tricky") for d in json.loads((HERE / f"data/{f}.json").read_text())}


def parse_json(s):
    """-> (strict_ok, obj). strict = whole output is one JSON object; else try to pull one out."""
    try:
        return True, json.loads(s)
    except Exception:
        pass
    m = re.search(r"\{.*\}", s, re.S)
    try:
        return False, json.loads(m.group(0)) if m else None
    except Exception:
        return False, None


def schema_ok(o):
    if not isinstance(o, dict) or not isinstance(o.get("emotions"), list) or not o["emotions"]:
        return False
    for e in o["emotions"]:
        if not isinstance(e, dict) or e.get("name") not in EMOTIONS or e.get("intensity") not in (1, 2, 3, 4, 5):
            return False
    return all(isinstance(o.get(k), list) for k in ("people", "places", "activities"))


def b_auto_grades(r):
    """B is graded automatically: instr = format, fact = people/places that really appear in the diary."""
    ok, o = parse_json(r["output"])
    instr = 5 if ok and schema_ok(o) else 4 if schema_ok(o) else 2 if isinstance(o, dict) else 1
    if not isinstance(o, dict):
        return instr, 1
    diary = gold[r["id"]]["text"].replace(" ", "")
    items = [x for k in ("people", "places") for x in (o.get(k) or []) if isinstance(x, str) and x.strip()]
    bad = sum(1 for x in items if x.replace(" ", "").rstrip("님") not in diary and x != "나")
    return instr, max(1, 5 - bad)


def b_metrics(rows):
    strict = schema = agree = 0
    for r in rows:
        ok, o = parse_json(r["output"])
        strict += ok and schema_ok(o)
        schema += schema_ok(o)
        if schema_ok(o) and o["emotions"][0]["name"] in gold[r["id"]]["gold"]:
            agree += 1
    n = len(rows)
    return {"json_strict_valid_pct": round(100 * strict / n), "json_extractable_valid_pct": round(100 * schema / n),
            "emotion_agree_pct": round(100 * agree / n)}


def main():
    out = {}
    for f in sorted((HERE / "raw").glob("*.jsonl")):
        name = f.stem
        rows = [json.loads(l) for l in f.read_text().splitlines() if l.strip()]
        meta = json.loads((HERE / f"raw/{name}.meta.json").read_text())
        res = {"gguf_gb": round(meta["gguf_bytes"] / 1e9, 2),
               "peak_mem_gb": round(meta["max_rss"] / 1e9, 2),
               "gen_tps": round(statistics.median(r["gen_tps"] for r in rows), 1),
               "prompt_tps": round(statistics.median(r["prompt_tps"] for r in rows), 1),
               "ttft_A_s": round(statistics.median(r["prompt_ms"] for r in rows if r["task"] == "A") / 1000, 2),
               **b_metrics([r for r in rows if r["task"] == "B"])}
        g = HERE / f"grades/{name}.tsv"
        if g.exists():
            cols = {}
            for line in g.read_text().splitlines():
                p = line.split("\t")
                if len(p) < 6 or p[0] not in "ABC":
                    continue
                for k, v in zip(("nat", "emp", "fact", "instr"), p[2:6]):
                    if v != "-":
                        cols.setdefault(f"{p[0]}_{k}", []).append(int(v))
            for r in rows:
                if r["task"] == "B":
                    bi, bf = b_auto_grades(r)
                    cols.setdefault("B_instr", []).append(bi); cols.setdefault("B_fact", []).append(bf)
            res.update({k: round(statistics.mean(v), 2) for k, v in sorted(cols.items())})
            for t in "ABC":
                vals = [statistics.mean(v) for k, v in cols.items() if k.startswith(t + "_")]
                if vals:
                    res[f"{t}_avg"] = round(statistics.mean(vals), 2)
        out[name] = res
    (HERE / "results").mkdir(exist_ok=True)
    (HERE / "results/summary.json").write_text(json.dumps(out, ensure_ascii=False, indent=1))
    for k, v in out.items():
        print(k, v)


if __name__ == "__main__":
    assert parse_json('{"emotions":[{"name":"기쁨","intensity":3}],"people":[],"places":[],"activities":[]}')[0]
    assert schema_ok(parse_json('```json\n{"emotions":[{"name":"기쁨","intensity":3}],"people":[],"places":[],"activities":[]}\n```')[1])
    assert not schema_ok({"emotions": [{"name": "행복", "intensity": 3}], "people": [], "places": [], "activities": []})
    main()
