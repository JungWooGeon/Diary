"""Append compact grades to grades/<model>.tsv.
stdin lines: '<task> <id> <10 codes>' in ORDER; code = 4 chars nat emp fact instr ('-' = n/a), optional '# note'.
usage: python3 -I grade_add.py < batch.txt"""
import sys
from pathlib import Path
HERE = Path(__file__).resolve().parent
ORDER = ["gemma4-e2b", "gemma4-e4b", "kanana15-2.1b", "hcx-seed-1.5b", "qwen3-1.7b", "qwen3-4b-2507",
         "qwen3.5-2b", "qwen3.5-4b", "gemma3-4b", "exaone4-1.2b"]  # same as show.py
(HERE / "grades").mkdir(exist_ok=True)
for line in sys.stdin:
    line = line.split("#")[0].split()
    if not line:
        continue
    task, iid, codes = line[0], line[1], line[2:]
    assert len(codes) == len(ORDER) and all(len(c) == 4 for c in codes), line
    for m, c in zip(ORDER, codes):
        with open(HERE / f"grades/{m}.tsv", "a") as f:
            f.write("\t".join([task, iid, *c]) + "\n")
