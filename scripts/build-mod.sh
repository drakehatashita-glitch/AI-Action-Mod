#!/usr/bin/env bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
MOD_DIR="$PROJECT_DIR/minecraft-mod"
LOG_FILE="/tmp/aimod-build.log"

export JAVA_HOME=$(readlink -f $(which java) | sed 's|/bin/java||')
echo "[build-mod] JAVA_HOME=$JAVA_HOME"
echo "[build-mod] Building in $MOD_DIR"
echo "[build-mod] Log: $LOG_FILE"

cd "$MOD_DIR"

# Clear any stale lock files from previous interrupted builds
find "$HOME/.gradle/caches/fabric-loom" -name "*.lock" -delete 2>/dev/null || true

./gradlew build --no-daemon --info 2>&1 | tee "$LOG_FILE"

JAR=$(ls build/libs/aimod-*.jar 2>/dev/null | grep -v sources | head -1)
if [ -n "$JAR" ]; then
  cp "$JAR" "$PROJECT_DIR/aimod-1.0.0.jar"
  echo ""
  echo "========================================"
  echo "BUILD SUCCESS"
  echo "Jar: $PROJECT_DIR/aimod-1.0.0.jar"
  echo "========================================"
else
  echo "BUILD FAILED — no jar produced"
  exit 1
fi
