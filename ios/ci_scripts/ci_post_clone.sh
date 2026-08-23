#!/bin/sh
set -eu

: "${CI_PRIMARY_REPOSITORY_PATH:?Xcode Cloud repository path is unavailable}"
: "${CI_DERIVED_DATA_PATH:?Xcode Cloud derived-data path is unavailable}"

FLUTTER_VERSION=3.47.1
FLUTTER_SDK_ROOT="$CI_DERIVED_DATA_PATH/verceltics-flutter-$FLUTTER_VERSION"
FLUTTER_EXECUTABLE="$FLUTTER_SDK_ROOT/bin/flutter"

if [ ! -x "$FLUTTER_EXECUTABLE" ]; then
  git clone \
    --depth 1 \
    --branch "$FLUTTER_VERSION" \
    https://github.com/flutter/flutter.git \
    "$FLUTTER_SDK_ROOT"
fi

"$FLUTTER_EXECUTABLE" config --no-analytics
"$FLUTTER_EXECUTABLE" precache --ios

cd "$CI_PRIMARY_REPOSITORY_PATH"
CI=true FLUTTER_BIN="$FLUTTER_EXECUTABLE" \
  ./scripts/bootstrap_flutter_ios.sh
