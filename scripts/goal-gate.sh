#!/usr/bin/env bash
# Goal gate. Compares the working tree and HEAD with origin/swift-java-poc (merge-base).
# In this repo the trunk is swift-java-poc: pull requests land there. master is the upstream's.
# Prints GATE PASS or GATE FAIL: <reason> as its last line.
# Flags: --no-cache  --static (checks b, c, d only)  --hook (Stop hook mode)
#
# Check a runs one suite: the WASM reader's node smoke (make -C gamutlock-wasm smoke), the
# same steps as .github/workflows/wasm-reader.yml. The repo's other suites (codec regression,
# the Panama wrapper, the Android builds) are NOT run here; their CI workflows run them.
set -u

TRUNK="swift-java-poc"
MODE_CACHE=1; MODE_STATIC=0; MODE_HOOK=0
for a in "$@"; do
  case "$a" in
    --no-cache) MODE_CACHE=0 ;;
    --static) MODE_STATIC=1 ;;
    --hook) MODE_HOOK=1 ;;
  esac
done

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT" || exit 1

STOP_ACTIVE=0
if [ "$MODE_HOOK" = 1 ]; then
  INPUT="$(cat || true)"
  if printf '%s' "$INPUT" | grep -Eq '"stop_hook_active"[[:space:]]*:[[:space:]]*true'; then STOP_ACTIVE=1; fi
  BRANCH_NOW="$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo "$TRUNK")"
  case "$BRANCH_NOW" in claude/*) ;; *) exit 0 ;; esac
fi

BRANCH="$(git rev-parse --abbrev-ref HEAD 2>/dev/null)"
GITDIR="$(git rev-parse --git-dir)"
CACHE="$GITDIR/goal-gate.cache"
WAVE0_BRANCH="claude/goal-wave0-guardrails"
GUARDRAILS="scripts/goal-gate.sh scripts/goal-baseline.json scripts/goal-protected.txt scripts/goal-allow.txt .claude/settings.json"

OUT="$(mktemp)"; LOG="$(mktemp)"; trap 'rm -f "$OUT" "$LOG"' EXIT
say() { printf '%s\n' "$*" >> "$OUT"; }
emit_and_finish() {
  local last="$1"
  if [ "$MODE_HOOK" = 1 ]; then
    if [ "$last" = "GATE PASS" ]; then exit 0; fi
    printf '%s\n' "$last" >&2
    [ "$STOP_ACTIVE" = 1 ] && exit 0
    exit 2
  fi
  cat "$OUT"
  printf '%s\n' "$last"
  [ "$last" = "GATE PASS" ] && exit 0
  exit 1
}

if ! git rev-parse --verify -q "origin/$TRUNK" >/dev/null; then
  emit_and_finish "GATE FAIL: origin/$TRUNK not found (run git fetch origin)"
fi
MB="$(git merge-base "origin/$TRUNK" HEAD)" || emit_and_finish "GATE FAIL: no merge-base with origin/$TRUNK"
say "branch: $BRANCH"
say "merge-base: $MB"

# Cache (full runs only): keyed on HEAD, its tree, the trunk, uncommitted changes and untracked files.
KEY=""
if [ "$MODE_STATIC" = 0 ] && [ "$MODE_CACHE" = 1 ]; then
  KEY="$( { git rev-parse HEAD 'HEAD^{tree}' "origin/$TRUNK"; git diff HEAD; \
            git ls-files -o --exclude-standard -z | while IFS= read -r -d '' f; do printf '%s ' "$f"; git hash-object -- "$f"; done; } \
          | sha256sum | cut -d' ' -f1)"
  if [ -f "$CACHE" ] && [ "$(head -n1 "$CACHE")" = "$KEY" ]; then
    LAST="$(tail -n1 "$CACHE")"
    if [ "$MODE_HOOK" = 1 ]; then emit_and_finish "$LAST"; fi
    echo "(cached result)"; tail -n +2 "$CACHE"
    [ "$LAST" = "GATE PASS" ] && exit 0
    exit 1
  fi
fi

fail=""
note_fail() { [ -z "$fail" ] && fail="$1"; say "FAIL: $1"; }

# Changed files vs merge-base: committed, staged, unstaged and untracked.
CHANGED="$( { git diff --name-status "$MB" ; git ls-files -o --exclude-standard | sed 's/^/A\t/'; } )"

# --- Check b: deleted test files and disabling markers ---
is_test_path() {
  case "$1" in
    test/*|tests/*|*/test/*|*/tests/*|*/androidTest/*) return 0 ;;
    *Test.java|*Tests.java|*Test.kt|*IT.java|*.test.js|*.test.mjs|*.spec.js|*_test.c|*/test_*.c) return 0 ;;
  esac
  return 1
}
while IFS= read -r p; do
  [ -z "$p" ] && continue
  if is_test_path "$p" && [ ! -e "$p" ]; then note_fail "test file deleted: $p"; fi
done < <(git diff --name-only --diff-filter=DR "$MB" --)
# Added lines (merge-base to working tree) plus untracked files. The guardrail files are
# skipped: they name the markers on purpose.
MARKERS='@Disabled|@Ignore|assumeTrue\(false|assumeFalse\(true|Assumptions\.abort|\.skip\(|\.todo\(|\.only\(|\bxit\(|\bxdescribe\(|\bxtest\(|it\.skip|test\.skip|describe\.skip|\{ *skip *:|\{ *todo *:|-x +test\b|--exclude-task +test\b|excludeTestsMatching|excludeTags|-DskipTests|-Dmaven\.test\.skip'
HITS="$(
  { git diff "$MB" -U0 --no-color -- . ':(exclude)scripts/goal-gate.sh' ':(exclude)scripts/goal-protected.txt' ':(exclude)scripts/goal-allow.txt' ':(exclude)scripts/goal-baseline.json' \
      | awk '/^\+\+\+ /{file=substr($0,7); next} /^\+/{print file ": " substr($0,2)}'
    git ls-files -o --exclude-standard -z | while IFS= read -r -d '' f; do
      case "$f" in scripts/goal-*) continue ;; esac
      [ -f "$f" ] && grep -I -H -n -E "$MARKERS" -- "$f" 2>/dev/null
    done
  } | grep -E "$MARKERS" || true
)"
if [ -n "$HITS" ]; then
  printf '%s\n' "$HITS" | head -20 | sed 's/^/    /' >> "$OUT"
  note_fail "disabling marker added ($(printf '%s\n' "$HITS" | wc -l) line(s))"
fi
say "check b: deleted tests and disabling markers"

# --- Check c: protected paths ---
# The lists are read from the merge-base when it has them, so a branch cannot loosen its own rules.
ALLOW_SRC=""
if git cat-file -e "$MB:scripts/goal-allow.txt" 2>/dev/null; then
  ALLOW_SRC="$(git show "$MB:scripts/goal-allow.txt")"
elif [ "$BRANCH" = "$WAVE0_BRANCH" ] && [ -f scripts/goal-allow.txt ]; then
  ALLOW_SRC="$(cat scripts/goal-allow.txt)"
fi
PROT_SRC=""
if git cat-file -e "$MB:scripts/goal-protected.txt" 2>/dev/null; then
  PROT_SRC="$(git show "$MB:scripts/goal-protected.txt")"
elif [ -f scripts/goal-protected.txt ]; then
  PROT_SRC="$(cat scripts/goal-protected.txt)"
fi
# The five guardrail files are always protected, whatever the list says.
PROT_SRC="$(printf '%s\n%s\n' "$PROT_SRC" "$(printf '%s\n' $GUARDRAILS)")"

is_guardrail() { local g; for g in $GUARDRAILS; do [ "$g" = "$1" ] && return 0; done; return 1; }
glob_in() { # $1 path, stdin list of globs; "**/" also matches at the top level
  local g
  while IFS= read -r g; do
    g="${g%%#*}"; g="$(printf '%s' "$g" | sed 's/[[:space:]]*$//; s/^[[:space:]]*//')"
    [ -z "$g" ] && continue
    # shellcheck disable=SC2053
    [[ "$1" == $g ]] && return 0
    if [[ "$g" == '**/'* ]]; then
      # shellcheck disable=SC2053
      [[ "$1" == ${g#\*\*/} ]] && return 0
    fi
  done
  return 1
}
while IFS=$'\t' read -r st path rest; do
  [ -z "${st:-}" ] && continue
  case "$st" in R*|C*) path="${rest:-$path}" ;; esac
  glob_in "$path" <<<"$PROT_SRC" || continue
  glob_in "$path" <<<"$ALLOW_SRC" && continue
  if is_guardrail "$path" && [ "$BRANCH" = "$WAVE0_BRANCH" ] && ! git cat-file -e "$MB:$path" 2>/dev/null; then continue; fi
  note_fail "protected path changed: $path"
done <<<"$CHANGED"
say "check c: protected paths"

# --- Check d: commit messages ---
if git log "$MB..HEAD" --format=%B | grep -Eq 'Claude-Session|Generated with Claude Code'; then
  note_fail "a commit message contains Claude-Session or Generated with Claude Code"
fi
say "check d: commit messages"

# --- Check a: the WASM reader's node smoke ---
if [ "$MODE_STATIC" = 0 ]; then
  BASE="$(python3 -c 'import json;print(json.load(open("scripts/goal-baseline.json"))["suites"]["wasm_smoke"]["tests"])' 2>/dev/null || echo none)"
  if ! command -v emcc >/dev/null 2>&1 && [ -f "$HOME/emsdk/emsdk_env.sh" ]; then
    # shellcheck disable=SC1091
    EMSDK_QUIET=1 source "$HOME/emsdk/emsdk_env.sh" >/dev/null 2>&1
  fi
  if ! command -v emcc >/dev/null 2>&1; then
    note_fail "emcc missing (install emsdk at ~/emsdk; see .github/workflows/wasm-reader.yml)"
  else
    say "emcc: $(emcc --version 2>/dev/null | head -n1)"
    ( make -C src/jabcode build/libjabcode.a && make -C gamutlock-wasm smoke ) > "$LOG" 2>&1
    rc=$?
    passed="$(grep -c '^PASS:' "$LOG")"
    failed="$(grep -c '^FAIL' "$LOG")"
    say "suite wasm_smoke: exit $rc, passed=$passed failed=$failed baseline=$BASE"
    if [ "$rc" != 0 ]; then tail -n 15 "$LOG" | sed 's/^/    /' >> "$OUT"; note_fail "wasm_smoke: exited $rc"; fi
    [ "$failed" != 0 ] && note_fail "wasm_smoke: $failed failed check(s)"
    if [ "$BASE" = none ]; then note_fail "wasm_smoke: no baseline count recorded"
    elif [ "$passed" -lt "$BASE" ]; then note_fail "wasm_smoke: $passed passed, below baseline $BASE"; fi
  fi
fi

if [ -n "$fail" ]; then RESULT="GATE FAIL: $fail"; else RESULT="GATE PASS"; fi
if [ -n "$KEY" ]; then { printf '%s\n' "$KEY"; cat "$OUT"; printf '%s\n' "$RESULT"; } > "$CACHE"; fi
emit_and_finish "$RESULT"
