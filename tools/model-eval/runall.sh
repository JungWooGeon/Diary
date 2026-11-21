#!/bin/zsh
# usage: ./runall.sh name1 name2 ...   (models in $M/<name>.gguf)
M=/private/tmp/claude-501/-Users-jwg-Desktop------JWG/ae3ce7f4-6099-40b4-b3c7-8867ae8906fd/scratchpad/models
cd "${0:A:h}"
while pgrep -f "curl -sfL -o" >/dev/null; do sleep 10; done
for n in "$@"; do python3 -I run.py "$n" "$M/$n.gguf" > "raw/$n.progress.log" 2>&1 && echo "OK $n" || echo "FAIL $n"; done
