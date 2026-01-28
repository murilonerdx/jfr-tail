#!/bin/bash
set -e

echo -e "\033[0;36m=== JFR-TAIL Library Mode Test ===\033[0m"

# 1. Build Phase
echo -e "\n\033[0;33m[1/4] Building project...\033[0m"
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

if [ -f "$ROOT_DIR/gradlew" ]; then
    "$ROOT_DIR/gradlew" -p "$ROOT_DIR" build -x test
else
    gradle -p "$ROOT_DIR" build -x test
fi

# Paths
SAMPLE_JAR="$ROOT_DIR/sample/build/libs/sample-1.0-SNAPSHOT.jar"
CLI_JAR="$ROOT_DIR/cli/build/libs/cli-1.0-SNAPSHOT.jar"

# 2. Start Sample App
echo -e "\n\033[0;33m[2/4] Starting Sample App (Library Mode)...\033[0m"
java -Djfrtail.start=true -jar "$SAMPLE_JAR" &
SAMPLE_PID=$!
echo -e "\033[0;32m-> Sample App running with PID: $SAMPLE_PID\033[0m"

sleep 5

# 3. Connect JFR Tail CLI
echo -e "\n\033[0;33m[3/4] Connecting JFR Tail CLI...\033[0m"
java -jar "$CLI_JAR" connect --port 7099 || echo "CLI exited with error (expected if killed)"

# 4. Cleanup
echo -e "\n\033[0;33m[4/4] Cleaning up...\033[0m"
kill $SAMPLE_PID || true

echo -e "\n\033[0;36mTest Complete.\033[0m"
