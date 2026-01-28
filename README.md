# 🦅 JFR-Tail
> **"Tail -f" for your JVM Flight Recorder events.**

🇧🇷 [Leia em Português](README_PT.md)

![Build Status](https://img.shields.io/badge/build-passing-brightgreen) ![License](https://img.shields.io/badge/license-MIT-blue) ![Version](https://img.shields.io/badge/version-4.0-purple)

**JFR-Tail** brings visibility to your JVM in real-time without the heavy bloat of full APMs. It attaches to your running Java process, streams JFR events (GC, Locks, Exceptions), and presents them in a beautiful Terminal UI (TUI).

---

## 🚀 Key Features

*   **Real-Time TUI**: View Garbage Collections, Thread Locks, and Exceptions as they happen.
*   **V4 Security 🔒**: Zero-Dependency Authentication using HMAC-SHA256 JWTs.
*   **Spring Boot V3 Integration 🌱**: Correlate JVM events with Actuator Health & Metrics.
*   **Incident Bundles 📦**: Press 'B' to instantly snapshot the system state for debugging.
*   **Lightweight**: Minimal overhead (< 1% CPU), zero external dependencies for the Agent.

---

## 📦 Installation

1.  **Build the project**:
    ```bash
    ./gradlew assemble
    ```
    *Output:* `cli/build/libs/cli-1.0-SNAPSHOT.jar` and `agent/build/libs/agent-1.0-SNAPSHOT.jar`.

---

## 🛠 Usage

### 1. Attach to a Running Process (Simplest)
```bash
# Find your PID (e.g., using jps)
jps -l

# Attach and monitor
java -jar cli.jar attach -p <PID> -a agent.jar
```

### 2. Secure Connection (Recommended)
**Server Side (App):**
```bash
java -Djfrtail.secret="my-secret-key" -javaagent:agent.jar -jar my-app.jar
```

**Client Side (You):**
```bash
java -jar cli.jar connect --secret "my-secret-key"
```

### 3. Granting Temporary Access
Don't share your master secret! Generate a temporary token:
```bash
# Generate a token valid for 1 hour
java -jar cli.jar token --secret "my-secret-key" --ttl 3600
```
Give the output string to your developer. They connect using:
```bash
java -jar cli.jar connect --token "eyJhbGciOiJIUzI1Ni..."
```

---

## 🌱 Spring Boot Integration
Launch the CLI with Actuator details to enable the **Spring Panel**:

```bash
java -jar cli.jar connect \
  --secret "my-secret-key" \
  --actuator-url "http://localhost:8080/actuator"
```
**Inside the TUI:**
*   Press **`S`** to toggle the Spring View (Health + Top Requests).
*   Press **`B`** to export an Incident Bundle.

---

## 📚 Documentation
For detailed commands, security details, and configuration options, see the [User Manual](docs/USAGE.md).

---

## 📄 License
MIT License.
