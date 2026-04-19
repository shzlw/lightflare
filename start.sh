#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAR_PATH="$ROOT_DIR/server/lightflare-app/target/lightflare-app-0.1.jar"
REQUIRED_JAVA_VERSION=25

if [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/java" ]]; then
  JAVA_BIN="$JAVA_HOME/bin/java"
elif [[ -x "/opt/homebrew/opt/openjdk/bin/java" ]]; then
  JAVA_BIN="/opt/homebrew/opt/openjdk/bin/java"
else
  JAVA_BIN="java"
fi

if [[ ! -f "$JAR_PATH" ]]; then
  echo "Jar not found: $JAR_PATH" >&2
  echo "Run ./build.sh first." >&2
  exit 1
fi

JAVA_VERSION_OUTPUT="$("$JAVA_BIN" -version 2>&1 | head -n 1)"
JAVA_VERSION="$(sed -E 's/.*version "([0-9]+).*/\1/' <<< "$JAVA_VERSION_OUTPUT")"

if [[ ! "$JAVA_VERSION" =~ ^[0-9]+$ || "$JAVA_VERSION" -lt "$REQUIRED_JAVA_VERSION" ]]; then
  echo "Java $REQUIRED_JAVA_VERSION or newer is required to run this jar." >&2
  echo "Selected Java: $JAVA_BIN" >&2
  echo "Version: $JAVA_VERSION_OUTPUT" >&2
  echo "Set JAVA_HOME to a Java $REQUIRED_JAVA_VERSION+ install, or install OpenJDK $REQUIRED_JAVA_VERSION." >&2
  exit 1
fi

JAVA_ARGS=()
case "${LIGHTFLARE_JSON_LOGS:-false}" in
  true|TRUE|1|yes|YES)
  if [[ -n "${SPRING_PROFILES_ACTIVE:-}" ]]; then
    JAVA_ARGS+=("-Dspring.profiles.active=${SPRING_PROFILES_ACTIVE},json-logs")
  else
    JAVA_ARGS+=("-Dspring.profiles.active=json-logs")
  fi
  ;;
esac

exec "$JAVA_BIN" ${JAVA_ARGS[@]+"${JAVA_ARGS[@]}"} -jar "$JAR_PATH" "$@"
