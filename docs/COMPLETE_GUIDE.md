# 🦅 JFR-Tail: The Complete Guide (v1.3.0)

Welcome to the comprehensive guide for **JFR-Tail**. This document covers everything from initial setup to advanced production deployment and frontend integration.

---

## 📋 Table of Contents
1. [Core Concepts](#-core-concepts)
2. [Project Integration (Maven & Spring Boot)](#-project-integration-maven--spring-boot)
3. [Configuration & Properties](#-configuration--properties)
4. [Using the CLI & TUI](#-using-the-cli--tui)
5. [Production Deployment with Consul](#-production-deployment-with-consul)
6. [Frontend Integration & CORS](#-frontend-integration--cors)
7. [Advanced Usage: Incident Bundles](#-advanced-usage-incident-bundles)
8. [Professional Observability (v1.3.0)](#-professional-observability-v130)

---

## 🧠 Core Concepts
JFR-Tail consists of two main components:
- **The Agent**: A lightweight Java Agent that resides inside your application, capturing JFR events and exposing them via TCP and HTTP.
- **The CLI**: A terminal-based tool that connects to the Agent to display real-time events and metrics.

---

## 🛠 Project Integration (Maven & Spring Boot)

### 1. GitHub Packages Authentication
JFR-Tail is hosted on GitHub Packages. You must authenticate to download it.

**In your `~/.m2/settings.xml`:**
```xml
<servers>
    <server>
        <id>github</id>
        <username>YOUR_GITHUB_USERNAME</username>
        <password>YOUR_PERSONAL_ACCESS_TOKEN_WITH_READ_PACKAGES_SCOPE</password>
    </server>
</servers>
```

### 2. Maven Configuration
Add the repository and the starter dependency to your `pom.xml`.

```xml
<repositories>
    <repository>
        <id>github</id>
        <url>https://maven.pkg.github.com/murilonerdx/jfr-tail</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>io.jfrtail</groupId>
        <artifactId>jfr-tail-spring-starter</artifactId>
            <version>1.3.0</version>
    </dependency>
</dependencies>
```

### 3. Spring Boot Configuration
The starter auto-configures the Agent. You only need to enable it in `application.yml` or `application.properties`.

---

## ⚙️ Configuration & Properties

All properties are prefixed with `jfr-tail`.

| Property | Default | Description |
| :--- | :--- | :--- |
| `jfr-tail.enabled` | `true` | Enables/Disables the agent. |
| `jfr-tail.web-port` | `8080` | Port for the embedded HTTP server (Dashboard/Stats). |
| `jfr-tail.tcp-port` | `7099` | Port for the CLI to connect (TCP stream). |
| `jfr-tail.secret` | *(Random)* | HMAC-SHA256 secret for JWT authentication. |

---

## 🖥 Using the CLI & TUI

### Standalone CLI Usage
If you just want to monitor a process without a UI, or use the CLI directly:

```bash
# Connect to a remote/local agent
java -jar jfr-tail-cli.jar connect --port 7099 --secret "my-secret"

# Attach to a local PID and start monitoring
java -jar jfr-tail-cli.jar attach -p 1234 -a jfr-tail-agent.jar
```

### Navigating the TUI
- **Live Events**: The main pane shows GC, Locks, and Exceptions.
- **Statistics**: Shows counts and Memory usage (Heap Used/Committed).
- **Shortcuts**:
    - `S`: Toggle **Spring Panel** (Requires `--actuator-url`).
    - `B`: Generate **Incident Bundle** (Zip of current state).
    - `C`: Clear events.
    - `Q`: Quit.

---

## 🚀 Production Deployment with Consul

In production, you likely use **HashiCorp Consul** or **Spring Cloud Config**.

1.  **Register the Secret**: Save the `jfr-tail.secret` in your Consul KV store under `config/application/jfr-tail/secret`.
2.  **Spring Integration**: Since we use standard Spring Properties, the starter will automatically pick up the secret from Consul if you have `spring-cloud-starter-consul-config` in your project.

**Example bootstrap.yml:**
```yaml
spring:
  cloud:
    consul:
      config:
        enabled: true
        prefix: config
```

This keeps your secrets safe and centralized.

---

## 🌐 Frontend Integration & CORS

As of v1.2.0, the embedded server supports **CORS (Cross-Origin Resource Sharing)**.

### Accessing Data from a Frontend
The Agent exposes data via:
- `GET /jfr/stats`: Returns a JSON snapshot of current metrics.
- `GET /jfr/dashboard`: Returns a simple HTML preview.
- `GET /actuator/jfrtail`: (In Spring Mode) Standard Actuator integration.

### CORS & Endpoints Setup
CORS is **enabled by default** in v1.2.0 for all origins (`*`).
As of v1.2.1, you can disable specific endpoints via properties.

**Example Fetch:**
```javascript
fetch('http://localhost:8081/jfr/stats', {
    headers: {
        'Authorization': 'Bearer ' + myJwtToken
    }
})
.then(response => response.json())
.then(data => console.log(data));
```

---

## 📦 Advanced Usage: Incident Bundles

When a performance spike occurs, press **`B`** in the CLI. JFR-Tail will:
1.  Capture the current metrics snapshot.
2.  Gather recent JFR event logs.
3.  Gather Spring Health data (if connected).
4.  Compress everything into a `.zip` file for external analysis.

---

🦅 **JFR-Tail** - Granular JVM visibility, tail-style.

---

## 🚀 Professional Observability (v1.3.0)

Version **1.3.0** introduces enterprise-grade observability features:

### 1. CLI Power Features
- **Dynamic Filtering**: Press **`F`** and type to filter events in real-time (by name or thread).
- **JSON Drill-down**: Use arrows to select an event and press **`ENTER`** to see the full raw JFR data in a modal.
- **Smart Alerts**: Visual warnings appear automatically for GC stalls (>500ms) or exception spikes.

### 2. Native Prometheus Integration
The Agent now exposes Prometheus-formatted metrics at:
`GET /jfr/metrics` (No authentication required by default).

### 3. Event History API
Consult the last 50 captured events at any time via:
`GET /jfr/history` (Requires JWT Bearer Token).

### 4. History Buffer
The Agent keeps a thread-safe circular buffer of recent events, enabling "Back-in-time" analysis even for short-lived spikes.
