#!/bin/sh
set -e
BASE_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
GRADLE_VERSION=8.13
DIST="$HOME/.gradle/wrapper/dists/gradle-$GRADLE_VERSION-bin"
INSTALL="$DIST/gradle-$GRADLE_VERSION"
if [ ! -x "$INSTALL/bin/gradle" ]; then
  mkdir -p "$DIST"
  ZIP="$DIST/gradle-$GRADLE_VERSION-bin.zip"
  if [ ! -f "$ZIP" ]; then
    echo "Downloading Gradle $GRADLE_VERSION..."
    curl -fL --retry 3 -o "$ZIP" "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip"
  fi
  rm -rf "$DIST/gradle-$GRADLE_VERSION"
  unzip -q "$ZIP" -d "$DIST"
fi
exec "$INSTALL/bin/gradle" "$@"
