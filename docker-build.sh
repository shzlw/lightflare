#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
IMAGE_NAME="${LIGHTFLARE_IMAGE:-shzlwio/lightflare}"
RELEASE_VERSION="${1:-${LIGHTFLARE_VERSION:-}}"

if [[ -n "$RELEASE_VERSION" ]]; then
  echo "Syncing release version..."
  node "$ROOT_DIR/scripts/sync-version.mjs" "$RELEASE_VERSION"
fi

VERSION="$(< "$ROOT_DIR/VERSION")"

echo "Building Docker image ${IMAGE_NAME}:${VERSION}..."
docker build -t "${IMAGE_NAME}:${VERSION}" "$ROOT_DIR"
