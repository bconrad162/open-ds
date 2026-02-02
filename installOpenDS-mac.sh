#!/usr/bin/env bash
set -euo pipefail

CACHE_DIR="$HOME/Library/Caches/OpenDS"
INSTALL_DIR="$HOME/Applications/OpenDS"
JDK_DIR="$INSTALL_DIR/jdk-21.jdk"
APP_BUNDLE="$HOME/Applications/OpenDS.app"
JAR_URL="https://github.com/bconrad162/open-ds/releases/download/4.0.0/OpenDS.jar"
ICON_URL="https://github.com/bconrad162/open-ds/releases/download/4.0.0/opends-driver-station.png"

mkdir -p "$CACHE_DIR"

arch="$(uname -m)"
case "$arch" in
  arm64)
    JDK_URL="https://download.oracle.com/java/21/latest/jdk-21_macos-aarch64_bin.tar.gz"
    ;;
  x86_64)
    JDK_URL="https://download.oracle.com/java/21/latest/jdk-21_macos-x64_bin.tar.gz"
    ;;
  *)
    echo "Unsupported architecture: $arch" >&2
    exit 1
    ;;
 esac

JDK_ARCHIVE="$CACHE_DIR/jdk-21-macos.tar.gz"
JAR_PATH="$INSTALL_DIR/OpenDS.jar"
ICON_PATH="$INSTALL_DIR/opends-driver-station.png"

printf "Downloading JDK 21 for %s...\n" "$arch"
curl -fL --retry 3 --retry-delay 2 "$JDK_URL" -o "$JDK_ARCHIVE"

printf "Installing JDK to %s...\n" "$JDK_DIR"
rm -rf "$JDK_DIR"
mkdir -p "$INSTALL_DIR"
tar -xzf "$JDK_ARCHIVE" -C "$INSTALL_DIR"
extracted_jdk="$(find "$INSTALL_DIR" -maxdepth 1 -type d -name 'jdk-21*' -print | head -n 1)"
if [[ -z "${extracted_jdk}" ]]; then
  echo "Failed to locate extracted JDK directory." >&2
  exit 1
fi
mv "$extracted_jdk" "$JDK_DIR"

printf "Downloading OpenDS.jar...\n"
curl -fL --retry 3 --retry-delay 2 "$JAR_URL" -o "$JAR_PATH"

file_type="$(file -b "$JAR_PATH" || true)"
if [[ "$file_type" != *"Zip archive data"* ]]; then
  echo "Downloaded file is not a valid JAR/ZIP: $file_type" >&2
  exit 1
fi

printf "Downloading icon...\n"
curl -fL --retry 3 --retry-delay 2 "$ICON_URL" -o "$ICON_PATH"

printf "Creating app bundle at %s...\n" "$APP_BUNDLE"
rm -rf "$APP_BUNDLE"
mkdir -p "$APP_BUNDLE/Contents/MacOS" "$APP_BUNDLE/Contents/Resources"

cat > "$APP_BUNDLE/Contents/MacOS/OpenDS" <<'LAUNCHER'
#!/usr/bin/env bash
set -euo pipefail
INSTALL_DIR="$HOME/Applications/OpenDS"
JDK="$INSTALL_DIR/jdk-21.jdk/Contents/Home/bin/java"
cd "$INSTALL_DIR"
exec "$JDK" -jar "$INSTALL_DIR/OpenDS.jar"
LAUNCHER
chmod +x "$APP_BUNDLE/Contents/MacOS/OpenDS"

cp "$ICON_PATH" "$APP_BUNDLE/Contents/Resources/opends-driver-station.png"

cat > "$APP_BUNDLE/Contents/Info.plist" <<'PLIST'
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>CFBundleName</key>
  <string>OpenDS</string>
  <key>CFBundleDisplayName</key>
  <string>OpenDS</string>
  <key>CFBundleIdentifier</key>
  <string>com.bconrad.opendS</string>
  <key>CFBundleExecutable</key>
  <string>OpenDS</string>
  <key>CFBundleShortVersionString</key>
  <string>4.0.0</string>
  <key>CFBundleVersion</key>
  <string>4.0.0</string>
</dict>
</plist>
PLIST

xattr -dr com.apple.quarantine "$APP_BUNDLE" "$JDK_DIR" "$JAR_PATH" "$ICON_PATH" 2>/dev/null || true

printf "\nInstall complete.\n"
printf "App bundle: %s\n" "$APP_BUNDLE"
printf "Run with: open %s\n" "$APP_BUNDLE"
printf "You can pin OpenDS to the Dock or launch via Spotlight.\n"
