"""Label texts with E4B using shared/ai/llm/prompts.json 'emotions' (schema-constrained).
python -I label.py IN.jsonl OUT.jsonl TEMP SEED
"""
import json, sys, urllib.request
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

P = json.loads((Path(__file__).resolve().parents[3] / "shared/ai/llm/prompts.json").read_text())["emotions"]
N2K = P["nameToKey"]
# ponytail: entity fields are unused for labels; cap them at 0 items to save decode time (emotions are decoded before them)
SCHEMA = json.loads(json.dumps(P["schema"]))
for f in ("people", "places", "activities"): SCHEMA["properties"][f]["maxItems"] = 0


def lab(args):
    text, temp, seed = args
    body = {"messages": [{"role": "system", "content": P["system"]}, {"role": "user", "content": text}],
            "temperature": temp, "seed": seed, "max_tokens": P["sampling"]["maxTokens"],
            "chat_template_kwargs": {"enable_thinking": False},
            "response_format": {"type": "json_schema", "json_schema": {"schema": SCHEMA}}}
    req = urllib.request.Request("http://127.0.0.1:8720/v1/chat/completions", json.dumps(body).encode(), {"Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=600) as r:
            j = json.loads(json.load(r)["choices"][0]["message"]["content"])
        return {"emo": [[N2K[e["name"]], e["intensity"]] for e in j["emotions"]], "sarcasm": j["sarcasm"]}
    except Exception as e:
        return {"err": str(e)}


def main():
    src, dst, temp, seed = sys.argv[1], sys.argv[2], float(sys.argv[3]), int(sys.argv[4])
    rows = [json.loads(l) for l in open(src) if l.strip()]
    done = {json.loads(l)["text"] for l in open(dst)} if Path(dst).exists() else set()
    rows = [r for r in rows if r["text"] not in done]
    with open(dst, "a") as f, ThreadPoolExecutor(10) as ex:
        for i, (r, o) in enumerate(zip(rows, ex.map(lab, [(r["text"], temp, seed) for r in rows]))):
            f.write(json.dumps({"text": r["text"], **o}, ensure_ascii=False) + "\n"); f.flush()
            if i % 1000 == 0: print(i, flush=True)


if __name__ == "__main__":
    main()
