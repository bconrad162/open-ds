#!/usr/bin/env bash
set -euo pipefail

rebuild=false
if [[ "${1:-}" == "--rebuild" ]]; then
  rebuild=true
  shift
fi

if [[ $# -ne 1 ]]; then
  echo "Usage: $0 [--rebuild] /absolute/or/relative/target-dir" >&2
  exit 1
fi

target_dir="$1"
mkdir -p "$target_dir"

image_name="opends-linux-build"

if $rebuild; then
  docker build --no-cache -t "$image_name" .
else
  docker build -t "$image_name" .
fi

docker run --rm -v "$PWD":/opends "$image_name" sh -c "mvn -B package"
docker run --rm -v "$PWD":/opends -e ARCH_TYPE=amd64 -e OS_TYPE=linux \
  "$image_name" sh -c "make native-linux"

jar_path="$(ls -1 target/*-jar-with-dependencies.jar | head -n 1)"
cp -f "$jar_path" "$target_dir/"

echo "Jar copied to: $target_dir/$(basename "$jar_path")"
echo "Linux amd64 native lib at: src/main/resources/opends-lib-linux-amd64.so"
