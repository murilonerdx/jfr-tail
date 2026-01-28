#!/bin/bash
set -e

echo -e "\033[0;36m=== JFR-TAIL Actuator Test ===\033[0m"

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SCRIPT_DIR="$(dirname "${BASH_SOURCE[0]}")"
MOCK_SRC="$ROOT_DIR/scripts/windows/MockActuator.java"
MOCK_CLASS="$ROOT_DIR/scripts/windows/MockActuator.class"

# 1. Start Mock Actuator
echo -e "\n\033[0;33m[1/3] Starting Mock Actuator (Java)...\033[0m"
if [ ! -f "$MOCK_CLASS" ]; then
    javac "$MOCK_SRC"
fi

# Run MockActuator with classpath set to the directory containing the class file (scripts/windows)
java -cp "$ROOT_DIR/scripts/windows" MockActuator &
MOCK_PID=$!
echo -e "\033[0;32m-> Mock Actuator PID: $MOCK_PID\033[0m"

sleep 2

# 2. Run CLI
echo -e "\n\033[0;33m[2/3] Running CLI (Connect to Mock)...\033[0m"
echo -e "\033[0;35mExpect to see: 'Requests: 120 | Total Time: 5.00s'\033[0m"
echo -e "\033[0;37mPress 'q' to exit the CLI manually.\033[0m"

CLI_JAR="$ROOT_DIR/cli/build/libs/cli-1.0-SNAPSHOT.jar"
java -jar "$CLI_JAR" connect --port 9999 --actuator-url "http://localhost:8081" || true

# 3. Cleanup
echo -e "\n\033[0;32m[3/3] Cleanup Done.\033[0m"
kill $MOCK_PID || true
