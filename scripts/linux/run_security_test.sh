#!/bin/bash
set -e

echo -e "\033[0;36m=== JFR-TAIL V4 (Security) Test ===\033[0m"

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SAMPLE_JAR="$ROOT_DIR/sample/build/libs/sample-1.0-SNAPSHOT.jar"
CLI_JAR="$ROOT_DIR/cli/build/libs/cli-1.0-SNAPSHOT.jar"

# 1. Start Sample App
echo -e "\n\033[0;33m[1/3] Starting Secure Agent...\033[0m"
export JFRTAIL_SECRET="supersecret123"
java -Djfrtail.start=true -Djfrtail.secret="supersecret123" -jar "$SAMPLE_JAR" &
SAMPLE_PID=$!
echo -e "\033[0;32m-> Sample App PID: $SAMPLE_PID\033[0m"

sleep 5

# 2. Generate Token
echo -e "\n\033[0;33m[2/3] Generating Token...\033[0m"
TOKEN=$(java -jar "$CLI_JAR" token --secret "supersecret123" --ttl 60)
echo -e "\033[0;37m-> Generated Token: $TOKEN\033[0m"

# 3. Connect
echo -e "\n\033[0;33m[3/3] Connecting with Token...\033[0m"
java -jar "$CLI_JAR" connect --port 7099 --token "$TOKEN" --actuator-url "http://localhost:8080/jfr" || true

# Cleanup
kill $SAMPLE_PID || true
