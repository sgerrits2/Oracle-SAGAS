#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
SOURCE_PARENT="$REPO_ROOT/l2-provision/files/oracle-saga-cloudbank-src"
SOURCE_DIR="$SOURCE_PARENT/oracle-saga-cloudbank"
ARCHIVE="$REPO_ROOT/l2-provision/files/oracle-saga-cloudbank.zip"

for command_name in cp mktemp mv zip; do
  command -v "$command_name" >/dev/null 2>&1 || {
    echo "ERROR: $command_name is required." >&2
    exit 1
  }
done

test -d "$SOURCE_DIR" || {
  echo "ERROR: CloudBank source directory is missing: $SOURCE_DIR" >&2
  exit 1
}

TEMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TEMP_DIR"' EXIT

cp -pR "$SOURCE_DIR" "$TEMP_DIR/oracle-saga-cloudbank"

(
  cd "$TEMP_DIR"
  zip -q -X -r oracle-saga-cloudbank.zip oracle-saga-cloudbank
)

mv "$TEMP_DIR/oracle-saga-cloudbank.zip" "$ARCHIVE"
"$SCRIPT_DIR/validate-cloudbank-archive.sh" "$ARCHIVE"

echo "CloudBank archive rebuilt: $ARCHIVE"
