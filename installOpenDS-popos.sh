#!/usr/bin/env bash
set -euo pipefail

CACHE_DIR="$HOME/.cache/OpenDS"
INSTALL_DIR="$HOME/.local/share/OpenDS"
JDK_DIR="$INSTALL_DIR/jdk-21"
JAR_URL="https://github.com/bconrad162/open-ds/releases/download/4.0.0/OpenDS.jar"
ICON_URL="https://github.com/bconrad162/open-ds/releases/download/4.0.0/opends-driver-station.png"

mkdir -p "$CACHE_DIR" "$INSTALL_DIR"

arch="$(uname -m)"
case "$arch" in
  x86_64)
    JDK_URL="https://download.oracle.com/java/21/latest/jdk-21_linux-x64_bin.tar.gz"
    ;;
  aarch64)
    JDK_URL="https://download.oracle.com/java/21/latest/jdk-21_linux-aarch64_bin.tar.gz"
    ;;
  *)
    echo "Unsupported architecture: $arch" >&2
    exit 1
    ;;
 esac

JDK_ARCHIVE="$CACHE_DIR/jdk-21-linux.tar.gz"
JAR_PATH="$INSTALL_DIR/OpenDS.jar"
ICON_PATH="$INSTALL_DIR/opends-driver-station.png"
WRAPPER="$INSTALL_DIR/run-opends.sh"
DESKTOP_FILE="$HOME/.local/share/applications/opends.desktop"

printf "Downloading JDK 21 for %s...\n" "$arch"
curl -fL --retry 3 --retry-delay 2 "$JDK_URL" -o "$JDK_ARCHIVE"

printf "Installing JDK to %s...\n" "$JDK_DIR"
rm -rf "$JDK_DIR"
tar -xzf "$JDK_ARCHIVE" -C "$INSTALL_DIR"
extracted_jdk="$(find "$INSTALL_DIR" -maxdepth 1 -type d -name 'jdk-21*' -print | head -n 1)"
if [[ -z "${extracted_jdk}" ]]; then
  echo "Failed to locate extracted JDK directory." >&2
  exit 1
fi
mv "$extracted_jdk" "$JDK_DIR"

if [[ ! -x "$JDK_DIR/bin/java" ]]; then
  echo "Java executable not found at $JDK_DIR/bin/java" >&2
  exit 1
fi

printf "Downloading OpenDS.jar...\n"
curl -fL --retry 3 --retry-delay 2 "$JAR_URL" -o "$JAR_PATH"

file_type="$(file -b "$JAR_PATH" || true)"
if [[ "$file_type" != *"Zip archive data"* ]]; then
  echo "Downloaded file is not a valid JAR/ZIP: $file_type" >&2
  exit 1
fi

printf "Downloading icon...\n"
curl -fL --retry 3 --retry-delay 2 "$ICON_URL" -o "$ICON_PATH"

printf "Creating launcher script...\n"
cat > "$WRAPPER" <<'LAUNCHER'
#!/usr/bin/env bash
set -euo pipefail
INSTALL_DIR="$HOME/.local/share/OpenDS"
JDK="$INSTALL_DIR/jdk-21/bin/java"
cd "$INSTALL_DIR"
exec "$JDK" -jar "$INSTALL_DIR/OpenDS.jar"
LAUNCHER
chmod +x "$WRAPPER"

printf "Creating desktop entry...\n"
mkdir -p "$(dirname "$DESKTOP_FILE")"
cat > "$DESKTOP_FILE" <<EOF_DESKTOP
[Desktop Entry]
Type=Application
Name=OpenDS
Exec=$WRAPPER
Icon=$ICON_PATH
Terminal=false
Categories=Development;Education;
EOF_DESKTOP

if command -v update-desktop-database >/dev/null 2>&1; then
  update-desktop-database "$HOME/.local/share/applications" || true
fi
if command -v gtk-update-icon-cache >/dev/null 2>&1; then
  gtk-update-icon-cache || true
fi

printf "\nInstall complete.\n"
printf "Launch from Activities search: OpenDS\n"
printf "To favorite, right-click OpenDS and choose Add to Favorites.\n"
printf "Run directly: %s\n" "$WRAPPER"
