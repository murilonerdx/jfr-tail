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

## Integration

### Spring Boot Starter (Recommended)
Add the dependency to your `pom.xml` or `build.gradle` to enable auto-configuration:

#### Gradle
```kotlin
repositories {
    maven {
        url = uri("https://maven.pkg.github.com/murilonerdx/jfr-tail")
        credentials {
            username = "GITHUB_USERNAME"
            password = "GITHUB_TOKEN"
        }
    }
}

dependencies {
    implementation("io.jfrtail:jfr-tail-spring-starter:1.0.1")
}
```

#### Maven
```xml
<repositories>
    <repository>
        <id>github</id>
        <url>https://maven.pkg.github.com/murilonerdx/jfr-tail</url>
        <snapshots><enabled>true</enabled></snapshots>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>io.jfrtail</groupId>
        <artifactId>jfr-tail-spring-starter</artifactId>
        <version>1.0.1</version>
    </dependency>
</dependencies>
```

#### Configuration (`application.yml`)
The starter automatically initializes the JFR-Tail monitor. You can customize it:

```yaml
jfr-tail:
  enabled: true       # Default true
  web-port: 8081      # Default 8080 (Change if Boot uses 8080)
  tcp-port: 7099      # Default 7099
  secret: "your-shared-secret"
```

### Manual Setup (Non-Spring)
If you are not using Spring Boot, you can still import the `agent` library:

```xml
<dependency>
    <groupId>io.jfrtail</groupId>
    <artifactId>agent</artifactId>
    <version>1.0.1</version>
</dependency>
```
And start it manually: `JfrTailMonitor.getInstance().start(webPort, tcpPort);`

---

## Spring Boot Actuator Insight
JFR-Tail can correlate its data with Spring Boot Actuator metrics. Ensure your app exposes the endpoints:
```properties
management.endpoints.web.exposure.include=health,metrics,threaddump,env
```

### Connecting CLI with Actuator
```bash
jfr-tail connect \
  --secret "your-shared-secret" \
  --actuator-url "http://localhost:8080/actuator"
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

---

## Production Readiness

### 1. Configuration Management (Secrets)
For production environments, avoid passing secrets via command line args. JFR-Tail honors the standard Spring Boot configuration hierarchy. You can set the secret via:

**Environment Variables:**
```bash
export JFR_TAIL_SECRET="production-secure-secret-v1"
java -jar my-app.jar
```

**Spring Cloud Consul / Config Server:**
If your application uses Spring Cloud, simply add the property to your shared configuration:
```yaml
jfr-tail:
  secret: "production-secure-secret-v1"
  enabled: true
```

### 2. Reliable Connectivity
The CLI Tool (`jfr-tail-cli.jar`) is designed for long-running monitoring sessions.
- **Auto-Reconnect**: If the application restarts or the network drops, the CLI will automatically attempt to reconnect every 5 seconds.
- **Persistent View**: You can keep the CLI running on a dedicated admin console without needing to restart it manually.
