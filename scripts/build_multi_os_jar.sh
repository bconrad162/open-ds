#!/usr/bin/env bash
set -euo pipefail

rebuild=false
if [[ "${1:-}" == "--rebuild" ]]; then
  rebuild=true
  shift
fi

root_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$root_dir"

image_name="opends-linux-build"

if $rebuild; then
  docker build --no-cache -t "$image_name" .
else
  docker build -t "$image_name" .
fi

docker run --rm -v "$PWD":/opends "$image_name" sh -c "mvn -B -DskipTests package"

jar_path="$(ls -t target/*-jar-with-dependencies.jar 2>/dev/null | head -n 1)"
if [[ -z "$jar_path" ]]; then
  echo "Jar not found in target/. Build may have failed."
  exit 1
fi

mkdir -p dist
cp "$jar_path" "dist/OpenDS.jar"

cat > "dist/run.sh" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
exec java -jar "$DIR/OpenDS.jar"
EOF
chmod +x "dist/run.sh"

cat > "dist/run.bat" <<'EOF'
@echo off
java -jar "%~dp0OpenDS.jar"
EOF

cat > "dist/README.txt" <<'EOF'
OpenDS (Java)
Requires Java 8+ installed.

Run on macOS/Linux:
  ./run.sh

Run on Windows:
  run.bat
EOF

echo "Built dist/OpenDS.jar using Docker image: $image_name"
