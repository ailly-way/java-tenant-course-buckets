#!/usr/bin/env sh
set -eu

BUILD_DIR="${TMPDIR:-/tmp}/tenant-course-marketplace-classes"
mkdir -p "$BUILD_DIR"
find src/main/java -name '*.java' -print > "$BUILD_DIR/sources.txt"
javac -d "$BUILD_DIR" @"$BUILD_DIR/sources.txt"
java -cp "$BUILD_DIR" education.marketplace.web.MarketplaceServer
