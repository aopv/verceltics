#!/bin/sh
set -eu

REPOSITORY_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
SHARED_UI_PATH="$REPOSITORY_ROOT/shared_ui"

if [ -n "${FLUTTER_BIN:-}" ]; then
  FLUTTER_EXECUTABLE="$FLUTTER_BIN"
elif command -v flutter >/dev/null 2>&1; then
  FLUTTER_EXECUTABLE=$(command -v flutter)
else
  echo "Set FLUTTER_BIN to a Flutter 3.44+ executable before bootstrapping iOS." >&2
  exit 1
fi

cd "$SHARED_UI_PATH"
"$FLUTTER_EXECUTABLE" pub get
DART_EXECUTABLE="$(dirname -- "$FLUTTER_EXECUTABLE")/dart"
"$DART_EXECUTABLE" run pigeon \
  --input pigeons/verceltics_bridge.dart
"$DART_EXECUTABLE" format \
  lib/src/bridge/generated/verceltics_bridge.g.dart

set -- build swift-package --platform ios
if [ -n "${FLUTTER_CODESIGN_IDENTITY:-}" ]; then
  set -- "$@" --codesign-identity "$FLUTTER_CODESIGN_IDENTITY"
elif [ "${CI:-}" = "true" ]; then
  set -- "$@" --no-codesign
fi
"$FLUTTER_EXECUTABLE" "$@"
