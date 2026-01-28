# JFR-Tail User Manual

🇧🇷 [Leia em Português](USAGE_PT.md)

## Overview
**JFR-Tail** is a lightweight observability tool that allows you to "tail" Java Flight Recorder (JFR) events in real-time. It provides a terminal-based UI (TUI) to monitor JVM internals, correlated with application metrics.

## V4 Security & Authentication
As of Version 4, JFR-Tail enforces strict security using HMAC-SHA256 JWTs.

### 1. Owner Mode (Full Access)
When you start the Agent (or attach to a JVM), you possess a **Shared Secret**. This secret gives you administrative control.

**Finding the Secret:**
- Look at the application STDOUT logs on startup:
  ```
  [SECURITY] SECRET=550e8400-e29b-41d4-a716-446655440000
  ```
- OR, set it manually via System Property:
  ```bash
  java -Djfrtail.secret=my-safe-password -javaagent:jfrtail-agent.jar ...
  ```

**Connecting as Owner:**
The CLI will automatically generate a short-lived token using the secret.
```bash
jfr-tail connect --secret "my-safe-password"
```

### 2. Guest Mode (Temporary Access)
If you want to grant temporary access to a developer/SRE without sharing the master secret:

1.  **Generate a Token (Owner):**
    ```bash
    # Generate token valid for 30 minutes (1800 seconds)
    jfr-tail token --secret "my-safe-password" --ttl 1800
    ```
    *Output:* `eyJhbGciOiJIUzI1NiJ9.eyJleHAiOjE3...`

2.  **Connect (Guest):**
    ```bash
    jfr-tail connect --token "eyJhbGciOiJIUzI1NiJ9..."
    ```
    *Note: The guest will be disconnected automatically when the token expires.*

---

## Spring Boot Integration (V3)
JFR-Tail can integrate with Spring Boot Actuator to show Health Status and HTTP Metrics alongside JVM events.

### Setup
Ensure your Spring Boot App exposes Actuator:
```properties
management.endpoints.web.exposure.include=health,metrics,threaddump,env
```

### connecting with Spring & Security
```bash
jfr-tail connect \
  --secret "my-safe-password" \
  --actuator-url "http://localhost:8080/actuator" \
  --actuator-user "admin" \  # Optional (if Actuator is protected)
  --actuator-pass "secret"
```

### TUI Navigation
- **`S` Key**: Toggle **Spring Panel**. Shows Health Status (UP/DOWN) and Top Endpoints.
- **`B` Key**: Create **Incident Bundle**. Zips current stats, logs, and trace info into a file.
- **`C` Key**: Clear current screen.
- **`Q` Key**: Quit.

---

## Web Dashboard
The agent hosts a lightweight dashboard at:
`http://localhost:8080/jfr/dashboard?token=<YOUR_TOKEN>`

You must generate a valid token (`jfr-tail token ...`) and pass it in the URL query parameter.

## Troubleshooting
**"AUTH FAILED"**:
- Check if your Token has expired.
- Verify if the Secret matches the one on the server.
- Ensure the server time is synchronized.

**"Address already in use"**:
- The agent port (7099) or web port (8080) is occupied. Use different ports via command line arguments if possible (Agent support required) or kill the conflicting process.
