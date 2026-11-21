#!/bin/sh
# Stopgap until .github/workflows/web-pages.yml is pushed: build web/ and force-push dist to the gh-pages branch
# (Pages source = gh-pages branch). Usage: web/ci/deploy-gh-pages.sh  [extra git push args, e.g. credential config]
set -e
cd "$(dirname "$0")/.."
npm ci --silent && npm test --silent && npm run build
cd dist && touch .nojekyll && rm -rf .git && git init -q -b gh-pages && git add -A && git commit -qm "web build $(git -C .. rev-parse --short HEAD)"
git push -f "${PAGES_REMOTE:-https://github.com/JungWooGeon/Diary.git}" gh-pages "$@"
rm -rf .git
