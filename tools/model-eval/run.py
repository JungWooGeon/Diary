"""Run every task for one model through llama-server and save raw outputs + timings.
usage: python3 -I run.py <name> <gguf path> [v2] [tasks=ABC] [data=diaries,tricky]
  v2: prompts/sampling from shared/ai/llm/prompts.json, emotion JSON constrained by its json_schema.
"""
import json, os, re, subprocess, sys, time, urllib.request
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
from prompts import V2 as P2, messages, messages_v2, sarcasm_hint, v2_cfg  # noqa: E402

name, gguf = sys.argv[1], sys.argv[2]
opt = dict(a.split("=", 1) if "=" in a else (a, "1") for a in sys.argv[3:])
V2 = "v2" in opt
TASKS = opt.get("tasks", "ABC")
PORT = 8711
out_dir = HERE / "raw"; out_dir.mkdir(exist_ok=True)
diaries = [d for f in opt.get("data", "diaries").split(",") for d in json.loads((HERE / f"data/{f}.json").read_text())]
trans = json.loads((HERE / "data/transcripts.json").read_text())
MAXTOK = {"A": 300, "B": 400, "C": 800}

log = open(out_dir / f"{name}.server.log", "w")
srv = subprocess.Popen(["/usr/bin/time", "-l", "llama-server", "-m", gguf, "--port", str(PORT),
                        "-c", "4096", "-ngl", "99", "--jinja", "-np", "1", "--no-webui"]
                       + (["--reasoning-budget", "0"] if V2 else []),
                       stdout=log, stderr=subprocess.STDOUT)


def post(path, body):
    req = urllib.request.Request(f"http://127.0.0.1:{PORT}{path}", json.dumps(body).encode(),
                                 {"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=600) as r:
        return json.load(r)


for _ in range(300):
    try:
        urllib.request.urlopen(f"http://127.0.0.1:{PORT}/health", timeout=2); break
    except Exception:
        time.sleep(1)
else:
    srv.kill(); sys.exit("server did not start")

jobs = [("A", d["id"], d["text"]) for d in diaries] + [("B", d["id"], d["text"]) for d in diaries] \
     + [("C", t["id"], t["text"]) for t in trans] \
     + [(k, d["id"], d["text"]) for k in "TS" for d in diaries] \
     + [(k, r["id"], r["text"]) for k in "RQ" for r in json.loads((HERE / "data/retro.json").read_text())]
jobs = [j for j in jobs if j[0] in TASKS]
rows = []
for task, iid, text in jobs:
    reading = None
    if V2 and task == "A" and re.search(P2["sarcasm"]["prefilter"], text):  # app flow: reading step, then reply
        cfg = v2_cfg("I")
        rr = post("/v1/chat/completions", {"messages": messages_v2("I", text), "temperature": 0, "seed": 42,
                                           "max_tokens": cfg["sampling"]["maxTokens"], "chat_template_kwargs": {"enable_thinking": False},
                                           "response_format": {"type": "json_schema", "json_schema": {"schema": cfg["schema"]}}})
        try: reading = json.loads(rr["choices"][0]["message"]["content"])
        except Exception: reading = None
    msgs = messages_v2(task, text + sarcasm_hint(reading)) if V2 else messages(task, text)
    if os.environ.get("MERGE_SYSTEM"):  # for templates that silently drop the system role (EXAONE GGUF)
        msgs = [{"role": "user", "content": msgs[0]["content"] + "\n\n" + msgs[1]["content"]}] + msgs[2:]
    t0 = time.time()
    body = {"messages": msgs, "temperature": 0.6, "seed": 42, "max_tokens": MAXTOK.get(task, 300),
            "cache_prompt": False, "chat_template_kwargs": {"enable_thinking": False}}
    if V2:
        cfg = v2_cfg(task); sp = cfg["sampling"]
        body.update(temperature=sp["temperature"], max_tokens=sp["maxTokens"])
        if "schema" in cfg:
            body["response_format"] = {"type": "json_schema", "json_schema": {"schema": cfg["schema"]}}
    r = post("/v1/chat/completions", body)
    msg = r["choices"][0]["message"]
    raw = msg.get("content") or ""
    out = re.sub(r"<think>.*?</think>|<\|eot_id\|>|<\|im_end\|>|<end_of_turn>", "", raw, flags=re.S).strip()
    tm = r.get("timings", {})
    rows.append({"task": task, "id": iid, "output": out, "reading": reading, "raw": raw, "reasoning": msg.get("reasoning_content"),
                 "finish": r["choices"][0].get("finish_reason"), "wall_s": round(time.time() - t0, 2),
                 "prompt_n": tm.get("prompt_n"), "prompt_ms": tm.get("prompt_ms"),
                 "prompt_tps": tm.get("prompt_per_second"), "gen_n": tm.get("predicted_n"),
                 "gen_tps": tm.get("predicted_per_second")})
    print(task, iid, rows[-1]["gen_tps"], out[:60].replace("\n", " "), flush=True)

srv.send_signal(2); srv.wait(); log.close()
logtxt = (out_dir / f"{name}.server.log").read_text(errors="ignore")
mem = {k: int(m.group(1)) for k, pat in [("max_rss", r"(\d+)\s+maximum resident set size"),
                                          ("peak_footprint", r"(\d+)\s+peak memory footprint")]
       if (m := re.search(pat, logtxt))}
(out_dir / f"{name}.jsonl").write_text("\n".join(json.dumps(x, ensure_ascii=False) for x in rows))
(out_dir / f"{name}.meta.json").write_text(json.dumps(
    {"name": name, "gguf_bytes": os.path.getsize(gguf), **mem}, indent=1))
print("done", name, mem)
