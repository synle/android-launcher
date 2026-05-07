#!/usr/bin/env bash
# Format Kotlin sources with ktlint.
set -euo pipefail
cd "$(dirname "$0")"
if ! command -v ktlint >/dev/null 2>&1; then
  echo "ktlint not found. Install with: brew install ktlint" >&2
  exit 1
fi
ktlint --format \
  'app/src/**/*.kt' \
  'app/src/**/*.kts' \
  '*.kts'
