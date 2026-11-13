#!/usr/bin/env bash
set -euo pipefail

for command in git zip; do
  command -v "$command" >/dev/null || {
    printf 'Required command not found: %s\n' "$command" >&2
    exit 1
  }
done

cd "$(dirname "${BASH_SOURCE[0]}")/.."
project_root=$(pwd)
archive="$(dirname "$project_root")/$(basename "$project_root").zip"

if [[ -e "$archive" ]]; then
  printf 'Archive already exists; move or remove it first: %s\n' "$archive" >&2
  exit 1
fi

# Include current edits and new files, respecting the repository's ignore rules.
files=()
while IFS= read -r -d '' file; do
  [[ -f "$file" ]] || continue
  git check-ignore --no-index -q -- "$file" && continue
  case "$file" in
    *.zip) continue ;;
  esac
  files+=("$file")
done < <(git ls-files --cached --others --exclude-standard -z)

zip -q "$archive" -- "${files[@]}"
printf 'Created %s\n' "$archive"
