#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WEBUI_DIR="$ROOT_DIR/webui"
SERVER_DIR="$ROOT_DIR/server"
STATIC_DIR="$ROOT_DIR/server/lightflare-app/src/main/resources/static"
RELEASE_VERSION="${1:-${LIGHTFLARE_VERSION:-}}"

if [[ -n "$RELEASE_VERSION" ]]; then
  echo "Syncing release version..."
  node "$ROOT_DIR/scripts/sync-version.mjs" "$RELEASE_VERSION"
fi

cd "$WEBUI_DIR"
echo "Installing webui dependencies..."
npm ci --no-audit --no-fund --progress=false

echo "Building webui..."
npm run build

echo "Copying webui build into server resources..."
rm -rf "$STATIC_DIR"
mkdir -p "$STATIC_DIR"
cp -R "$WEBUI_DIR/dist/." "$STATIC_DIR/"

echo "Building server jar..."
cd "$SERVER_DIR"
BACKEND_BUILD_START_SECONDS="$SECONDS"
mvn -q -pl lightflare-app -am package
BACKEND_BUILD_DURATION_SECONDS="$((SECONDS - BACKEND_BUILD_START_SECONDS))"
printf 'Backend build finished in %02d:%02d\n' "$((BACKEND_BUILD_DURATION_SECONDS / 60))" "$((BACKEND_BUILD_DURATION_SECONDS % 60))"
