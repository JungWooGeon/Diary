"""Try upload-key password candidates from keystore_candidates.txt (one per line) without printing them.

    python3 tools/check_keystore_pw.py

On a match: writes it into keystore.properties (store = key password; PKCS12) and deletes the candidates file.
Prints only line numbers, never the passwords.
"""
import os, subprocess

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
PROPS, CANDS = os.path.join(ROOT, "keystore.properties"), os.path.join(ROOT, "keystore_candidates.txt")
lines = open(PROPS, encoding="utf-8").read().splitlines()
p = dict(l.split("=", 1) for l in lines if "=" in l and not l.startswith("#"))
store, alias = p["storeFile"], p.get("keyAlias", "key0")

cands = open(CANDS, encoding="utf-8").read().split("\n")
for i, pw in enumerate(cands, 1):
    if not pw:
        continue
    r = subprocess.run(["keytool", "-list", "-v", "-keystore", store, "-storepass:env", "SP", "-alias", alias],
                       capture_output=True, text=True, env=dict(os.environ, SP=pw))
    if r.returncode == 0:
        print(f"{i}번째 줄이 맞아요.", *[l.strip() for l in r.stdout.splitlines() if "SHA1" in l])
        out = [l for l in lines if not l.startswith(("storePassword=", "keyPassword="))] + [f"storePassword={pw}", f"keyPassword={pw}"]
        open(PROPS, "w", encoding="utf-8").write("\n".join(out) + "\n")
        os.remove(CANDS)
        print("keystore.properties에 저장하고 후보 파일은 지웠어요.")
        break
    print(f"{i}번째 줄: 틀렸어요")
else:
    print("맞는 후보가 없어요.")
