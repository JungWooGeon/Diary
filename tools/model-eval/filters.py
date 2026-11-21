"""Reference implementation of shared/ai/llm/filters.json (Kotlin twin: core/ai OutputFilter).
usage: python3 -I filters.py            # runs the spec's testCases
       from filters import check; check("reply", text, input) -> [failed rule names]
"""
import json, re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SPEC = json.loads((ROOT / "shared/ai/llm/filters.json").read_text())
R = SPEC["rules"]
PEOPLE = [[e["tag"]] + e.get("aliases", []) for e in json.loads((ROOT / "shared/ai/tags.json").read_text())["people"]]
PROMPTS = json.loads((ROOT / "shared/ai/llm/prompts.json").read_text())
QUOTES = re.compile(r"'[^']*'|\"[^\"]*\"|‘[^’]*’|“[^”]*”|「[^」]*」")
SINO = {c: i for i, c in enumerate("영일이삼사오육칠팔구")} | {"공": 0}
UNIT = {"십": 10, "백": 100, "천": 1000}


def unquote(t):
    return QUOTES.sub("", t)


def sentences(t):
    return [s.strip() for s in re.split(r"(?<=[.!?…\n])", unquote(t)) if re.search(r"[가-힣A-Za-z0-9]", s)]


def sino_value(run):
    run = run.replace(" ", "")
    intp, _, frac = run.partition("점")
    if any(c in UNIT or c == "만" for c in intp):
        total = section = cur = 0
        for c in intp:
            if c in SINO: cur = SINO[c]
            elif c in UNIT: section += (cur or 1) * UNIT[c]; cur = 0
            elif c == "만": total += (section + cur or 1) * 10000; section = cur = 0
        n = str(total + section + cur)
    else:
        n = "".join(str(SINO[c]) for c in intp if c in SINO).lstrip("0") or "0"
    return n + ("." + "".join(str(SINO[c]) for c in frac if c in SINO) if frac else "")


def input_numbers(inp):
    e = R["entities"]
    nums = set(re.findall(r"[0-9]+(?:\.[0-9]+)?", inp))
    for m in re.finditer(e["sinoNumeral"], inp):
        v = sino_value(m.group(0)); nums |= {v, v.split(".")[0]}
    nums |= {str(v) for w, v in e["nativeNumbers"].items() if re.search(w + e["nativeUnit"], inp)}
    return {n.lstrip("0") or "0" for n in nums}


def entities_ok(r, text, inp, people):
    flat = re.sub(r"\s", "", inp)
    nums = input_numbers(inp)
    if any((n.lstrip("0") or "0") not in nums for n in re.findall(r"[0-9]+(?:\.[0-9]+)?", text)):
        return False
    words = {w for g in people for w in g}
    for p in r["namePatterns"]:
        for m in re.finditer(p, text):
            stem = m.group(1)
            if len(stem) > 2 and stem.endswith("들"): stem = stem[:-1]
            if stem not in r["nameStop"] and stem not in words and stem not in flat:
                return False
    for g in people:
        if g[0] in r["peopleGeneric"]: continue
        for w in g:
            if len(w) < 2 or w in r["peopleIgnore"]: continue
            if not re.search("(?<![가-힣])" + re.escape(w) + "(?:" + r["peopleJosa"] + ")(?![가-힣])", text): continue
            stem = next((w[:-len(h)] for h in r["honorifics"] if w.endswith(h) and len(w) - len(h) >= 2), None)
            if not any(x in flat for x in g) and not (stem and stem in flat):
                return False
    return True


def rule_ok(name, text, inp="", people=PEOPLE):
    r = R[name]; k = r["kind"]
    if k == "sentenceEndForbid":
        for s in sentences(text):
            end = re.sub(r"[\s.!?…~,ㅎㅋㅠㅜ^]+$", "", s)
            if re.search(r["pattern"], end) and not re.search(r["allow"], end):
                return False
        return True
    if k == "charForbid":
        if re.search(r["forbid"], text):
            return False
        low = inp.lower()
        return all(w.lower() in low or w.lower() in r["latinAllow"] for w in re.findall(r["latin"], text))
    if k == "sentenceCount":
        return r["min"] <= len(sentences(text)) <= r["max"]
    if k == "questionCount":
        q = unquote(text).count("?"); ss = sentences(text)
        return r["min"] <= q <= r["max"] and (q == 0 or (ss and "?" in ss[-1] and all("?" not in s for s in ss[:-1])))
    if k == "length":
        n = len(text.strip())
        return r["min"] <= n <= r["max"] and not re.search(r.get("forbid", "$^"), text.strip())
    if k == "regexForbid":
        if any(re.search(p, text) for p in r["anywhere"]):
            return False
        ss = sentences(text)
        last = re.sub(r"[\s.!?…~,]+$", "", ss[-1]) if ss else ""
        return not any(re.search(p, last) for p in r["lastSentence"])
    if k == "entitiesInInput":
        return entities_ok(r, text, inp, people)
    raise ValueError(k)


def check(task, text, inp="", people=PEOPLE):
    """task = prompts.json key ('reply', 'tidy', ...) or 'chat.ask'. Returns failed rule names."""
    cfg = PROMPTS
    for part in task.split("."):
        cfg = cfg[part]
    return [n for n in cfg.get("filters", []) if n in R and not rule_ok(n, text, inp, people)]


if __name__ == "__main__":
    bad = [c for c in SPEC["testCases"] if rule_ok(c["rule"], c["text"], c.get("input", "")) != c["pass"]]
    for c in bad:
        print("FAIL", c)
    assert sino_value("사 점 이") == "4.2" and sino_value("이천이십육") == "2026" and sino_value("십") == "10"
    print(f"{len(SPEC['testCases']) - len(bad)}/{len(SPEC['testCases'])} pass")
    assert not bad
