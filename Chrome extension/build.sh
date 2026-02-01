#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
HELPER_DIR="$ROOT_DIR/helper"
OUT_DIR="$HELPER_DIR/dist"

mkdir -p "$OUT_DIR"

docker run --rm -v "$HELPER_DIR":/src -w /src golang:1.25 \
  bash -c "go mod tidy && \
    GOOS=linux GOARCH=amd64 go build -o /src/dist/opends-bridge-linux-amd64 ./cmd/opends-bridge && \
    GOOS=darwin GOARCH=amd64 go build -o /src/dist/opends-bridge-macos-amd64 ./cmd/opends-bridge && \
    GOOS=darwin GOARCH=arm64 go build -o /src/dist/opends-bridge-macos-arm64 ./cmd/opends-bridge && \
    GOOS=windows GOARCH=amd64 go build -o /src/dist/opends-bridge-windows-amd64.exe ./cmd/opends-bridge"

echo "Build complete. Binaries in: $OUT_DIR"
