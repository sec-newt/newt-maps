#!/usr/bin/env bash
# Turns a fresh copy of newt-android-template into a named app, then deletes itself.
# Usage: scripts/new-app.sh "Newt Notes" notes
#   first argument:  the name people see ("Newt Notes")
#   second argument: one lowercase word for the package (io.github.secnewt.notes)
set -euo pipefail

name="${1:-}"
word="${2:-}"
if [[ -z "$name" || ! "$word" =~ ^[a-z][a-z0-9]*$ ]]; then
  echo "Usage: $0 \"Newt Notes\" notes"
  echo "The second argument must be one lowercase word (letters and digits)."
  exit 1
fi
cd "$(git rev-parse --show-toplevel)"
if [[ ! -d app/src/main/java/io/github/secnewt/template ]]; then
  echo "This copy was already renamed."
  exit 1
fi

slug="$(echo "$name" | tr '[:upper:]' '[:lower:]' | tr -cs 'a-z0-9' '-' | sed 's/^-//;s/-$//')"   # newt-notes
project="$(echo "$name" | tr -cd 'A-Za-z0-9')"                                                    # NewtNotes

# Move the Kotlin source folders to the new package.
for set in main test; do
  git mv "app/src/$set/java/io/github/secnewt/template" "app/src/$set/java/io/github/secnewt/$word"
done

# Rename everywhere in text files (the fonts and Gradle jar are left alone).
git ls-files | grep -vE '\.(ttf|jar)$|^LICENSE$|^scripts/new-app\.sh$' | while read -r f; do
  sed -i \
    -e "s/io\.github\.secnewt\.template/io.github.secnewt.$word/g" \
    -e "s/Newt Template/$name/g" \
    -e "s/newt-template/$slug/g" \
    -e "s/NewtTemplate/$project/g" \
    "$f"
done

# A short README for the new app; the template's own instructions don't apply any more.
cat > README.md <<README
# $name

A private Android app, part of the Newt family (built from
[newt-android-template](https://github.com/sec-newt/newt-android-template)).
It follows the [house style](NEWT_STYLE.md).

> **A personal project, in early development.**

## Install

Releases are built and signed by GitHub Actions. Install and update with
Obtainium from this repository's Releases page.
README

git rm -q scripts/new-app.sh
echo "Renamed to $name (io.github.secnewt.$word). Review with 'git status', then commit."
