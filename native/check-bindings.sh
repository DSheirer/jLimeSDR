#!/usr/bin/env bash
# Compares the functions in LimeSuite.h with the bindings in Native.java, so a
# new upstream function fails the build rather than being silently unavailable.
#
# Usage: native/check-bindings.sh <path-to-LimeSuite.h>
set -euo pipefail
# sort and comm must agree on collation order whatever the caller's locale.
export LC_ALL=C

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
HEADER="$1"
BINDINGS="$ROOT/src/main/java/io/github/dsheirer/limesdr/internal/Native.java"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

# Declarations look like: API_EXPORT int CALL_CONV LMS_Name(
# The name can be on the line after API_EXPORT.
awk '/^API_EXPORT/ {pending = 1}
     pending && match($0, /LMS_[A-Za-z0-9_]+[ ]*\(/) {
         name = substr($0, RSTART, RLENGTH); sub(/[ ]*\(/, "", name); print name; pending = 0
     }' "$HEADER" | sort -u > "$WORK/header"
grep -oE '"LMS_[A-Za-z0-9_]+"' "$BINDINGS" | tr -d '"' | sort -u > "$WORK/bindings"

MISSING="$(comm -23 "$WORK/header" "$WORK/bindings")"
STALE="$(comm -13 "$WORK/header" "$WORK/bindings")"
if [ -n "$MISSING" ] || [ -n "$STALE" ]; then
    [ -z "$MISSING" ] || printf 'Functions in LimeSuite.h without a binding:\n%s\n' "$MISSING" >&2
    [ -z "$STALE" ] || printf 'Bindings not in LimeSuite.h:\n%s\n' "$STALE" >&2
    exit 1
fi
echo "Bindings match LimeSuite.h ($(wc -l < "$WORK/header" | tr -d ' ') functions)"
