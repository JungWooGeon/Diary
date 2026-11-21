#!/usr/bin/env python3
"""온이 문장 라이브러리 린트 (replies.json / questions.json / retro.json).

사용: python3 lint_library.py shared/library/replies.json [다른 json ...]
문제가 하나라도 있으면 종료 코드 1.
"""
import difflib
import json, os
import re
import sys
from collections import Counter

SLOTS = ("person", "place", "activity")
HONORIFIC = [
    (re.compile(r"요[.?!~]*$"), "존댓말 ~요"),
    (re.compile(r"습니다|습니까"), "존댓말 ~습니다"),
    (re.compile(r"세요"), "존댓말 ~세요"),
    (re.compile(r"시[어었]|셨"), "존댓말 ~시/셨"),
    (re.compile(r"[아어여해] ?봐[.!~]*$"), "권유형 ~어 봐"),
]
BANNED = [
    "해 봐", "해봐", "해 보는 건 어때", "해보는 건 어때", "해보자", "해 보자",
    "하는 게 중요", "해야 해", "하는 게 좋아", "하길 바라", "힘내", "파이팅", "화이팅",
    "긍정적으로", "너무 걱정하지 마", "잘 될 거야", "잘될 거야", "당신",
    "우울증", "번아웃", "트라우마", "스트레스 관리", "감정 조절",
    "당연한 일이야", "자연스러워", "충분히 그럴 수 있어",
]
# 받침에 따라 바뀌는 조사가 슬롯 바로 뒤에 붙은 경우 ({x}(이)랑 같은 괄호형은 통과)
BAD_PARTICLE = re.compile(r"\{(?:%s)\}(?:으로|로|은|는|이|가|을|를|와|과|랑|라니|라서|라고)" % "|".join(SLOTS))
RETRO_SLOTS = {"count", "streak", "topGroupName", "topEmotionName", "bestDay", "period"}
SLOT_RE = re.compile(r"\{(\w+)\}")
SENT_SPLIT = re.compile(r"(?<=[.?!])\s+")


def lint(path):
    items = json.load(open(path, encoding="utf-8"))
    kind = os.path.basename(path).split(".")[0]  # replies | questions | retro
    lo, hi = (15, 60) if kind == "questions" else (30, 130)
    slots = set(SLOTS) | (RETRO_SLOTS if kind == "retro" else set())
    flags = []
    texts = []
    for it in items:
        iid, t = it.get("id", "?"), it.get("text", "")
        texts.append((iid, t))
        f = lambda msg: flags.append(f"{iid}: {msg} | {t}")
        for s in SENT_SPLIT.split(t.strip()):
            for rx, msg in HONORIFIC:
                if rx.search(s.strip()):
                    f(msg)
        for b in BANNED:
            if b in t:
                f(f"금지 표현 '{b}'")
        if t.count("?") > 1:
            f("물음표 2개 이상")
        if not lo <= len(t) <= hi:
            f(f"길이 {len(t)}")
        if BAD_PARTICLE.search(t):
            f("슬롯 뒤 받침 의존 조사")
        used = set(SLOT_RE.findall(t))
        unknown = used - slots
        if unknown:
            f(f"알 수 없는 슬롯 {sorted(unknown)}")
        if kind == "retro":
            need = {"person": ("brightestPerson", "topPerson"), "place": ("topPlace",), "activity": ("topActivity",)}
            for slot in used & set(SLOTS):
                if not any(c in it.get("condition", "") for c in need[slot]):
                    f(f"조건 없이 슬롯 {{{slot}}} 사용")
        elif "tags" in it or used & set(SLOTS):
            tags = set(it.get("tags") or [])
            for tag in tags - used:
                f(f"태그 {tag}인데 슬롯 없음")
            for slot in (used & set(SLOTS)) - tags:
                f(f"태그 없이 슬롯 {{{slot}}} 사용")
    # 같은 두 어절 시작 과다 (5% 초과, 최소 3개)
    limit = max(3, int(len(texts) * 0.05))
    for k, n in Counter(" ".join(t.split()[:2]) for _, t in texts).items():
        if n > limit:
            flags.append(f"시작 '{k}' {n}회 (> {limit})")
    # 유사 중복 (ratio > 0.8). ponytail: O(n²) 비교, 수천 줄 넘으면 n-gram 버킷으로
    norm = [(i, SLOT_RE.sub("#", t)) for i, t in texts]
    for a in range(len(norm)):
        for b in range(a + 1, len(norm)):
            sm = difflib.SequenceMatcher(None, norm[a][1], norm[b][1])
            if sm.real_quick_ratio() > 0.8 and sm.quick_ratio() > 0.8 and sm.ratio() > 0.8:
                flags.append(f"유사 중복 {norm[a][0]} ~ {norm[b][0]} ({sm.ratio():.2f})")
    return flags


def main(paths):
    total = 0
    for p in paths:
        flags = lint(p)
        total += len(flags)
        print(f"== {p}: {len(flags)}건")
        for fl in flags:
            print("  " + fl)
    return 1 if total else 0


if __name__ == "__main__":
    if len(sys.argv) < 2:
        sys.exit("usage: lint_library.py FILE.json [...]")
    sys.exit(main(sys.argv[1:]))
