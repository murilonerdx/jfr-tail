package io.jfrtail.agent.api;

import io.jfrtail.agent.server.EmbeddedServer;
import io.jfrtail.common.CollectorProfile;
import io.jfrtail.common.JfrEvent;
import io.jfrtail.common.JsonUtils;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingStream;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class JfrTailMonitor {
    private static JfrTailMonitor instance;
    private final StatsManager statsManager = new StatsManager();
    private AlertManager alertManager;

    private EmbeddedServer webServer;
    private RecordingStream recordingStream;
    private final Set<PrintWriter> tcpClients = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private CollectorProfile profile = CollectorProfile.BALANCED;

    public static synchronized JfrTailMonitor getInstance() {
        if (instance == null) {
            instance = new JfrTailMonitor();
        }
        return instance;
    }

    private String secret;

    public void start(int webPort, int tcpPort) throws IOException {
        start(webPort, tcpPort, null, true, true);
    }

    public void start(int webPort, int tcpPort, String secret) throws IOException {
        start(webPort, tcpPort, secret, true, true);
    }

    public void start(int webPort, int tcpPort, String secret, boolean statsEnabled, boolean dashboardEnabled)
            throws IOException {
        start(webPort, tcpPort, secret, statsEnabled, dashboardEnabled, CollectorProfile.BALANCED);
    }

    public void start(int webPort, int tcpPort, String secret, boolean statsEnabled, boolean dashboardEnabled,
            CollectorProfile profile)
            throws IOException {
        this.profile = profile != null ? profile : CollectorProfile.BALANCED;

        // 0. Security Setup
        if (secret == null || secret.isEmpty()) {
            // Check System Prop
            secret = System.getProperty("jfrtail.secret");
        }
        if (secret == null || secret.isEmpty()) {
            // Generate Random
            secret = java.util.UUID.randomUUID().toString();
            System.out.println("\n[SECURITY] WARNING: No secret provided. Generated ephemeral secret:");
            System.out.println("[SECURITY] SECRET=" + secret);
            System.out.println("[SECURITY] Use this secret to connect via CLI --secret\n");
        }
        this.secret = secret;

        // 1. Start Web Server
        webServer = new EmbeddedServer(webPort, statsManager, secret, statsEnabled, dashboardEnabled);
        webServer.start();

        // 2. Start TCP Server (for converting CLI)
        startTcpServer(tcpPort);

        // 3. Start JFR Recording
        startRecording();
    }

    private void startTcpServer(int port) {
        executor.submit(() -> {
            try (ServerSocket serverSocket = new ServerSocket(port)) {
                System.out.println("[JfrTail] TCP Server started on port " + port);
                while (true) {
                    Socket client = serverSocket.accept();
                    executor.submit(() -> handleTcpClient(client));
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    private void handleTcpClient(Socket client) {
        PrintWriter writer = null;
        try {
            client.setSoTimeout(5000); // 5s to auth
            java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(client.getInputStream()));
            writer = new PrintWriter(client.getOutputStream(), true);

            // Handshake
            String line = reader.readLine();
            if (line == null || !line.startsWith("AUTH ")) {
                writer.println("ERR Auth Required");
                client.close();
                return;
            }

            String token = line.substring(5).trim();
            if (!io.jfrtail.common.security.JwtLite.verifyToken(token, this.secret)) {
                writer.println("ERR Invalid Token");
                client.close();
                return;
            }

            // Auth Success
            client.setSoTimeout(0); // Infinite
            writer.println("OK Welcome");
            tcpClients.add(writer);
            System.out.println("[JfrTail] TCP Client authenticated and added. Current clients: " + tcpClients.size());

            // Wait for disconnect
            while (reader.readLine() != null) {
            }

        } catch (IOException e) {
            // Disconnect
        } finally {
            if (writer != null) {
                tcpClients.remove(writer);
                System.out.println("[JfrTail] TCP Client disconnected. Remaining clients: " + tcpClients.size());
            }
            try {
                client.close();
            } catch (Exception e) {
            }
        }
    }

    private void startRecording() {
        recordingStream = new RecordingStream();
        // Lower thresholds to zero (capture all) for maximum visibility into contention
        recordingStream.enable("jdk.GarbageCollection");
        recordingStream.enable("jdk.GCHeapSummary").withPeriod(Duration.ofSeconds(1));

        if (profile == CollectorProfile.HIGH || profile == CollectorProfile.BALANCED) {
            recordingStream.enable("jdk.ExceptionThrown");
            recordingStream.enable("jdk.JavaMonitorEnter").withThreshold(Duration.ZERO);
            recordingStream.enable("jdk.ThreadPark").withThreshold(Duration.ZERO);
        }

        if (profile == CollectorProfile.HIGH) {
            recordingStream.enable("jdk.ObjectAllocationOutsideTLAB").withThreshold(Duration.ZERO);
            recordingStream.enable("jdk.MetaspaceSummary").withPeriod(Duration.ofSeconds(5));
        }

        recordingStream.enable("jdk.CPULoad").withPeriod(Duration.ofSeconds(1));
        recordingStream.setOrdered(false);

        recordingStream.onEvent(this::processEvent);

        // 4. Initialize AlertManager if configuration exists (placeholder for now)
        this.alertManager = new AlertManager();

        executor.submit(() -> {
            try {
                recordingStream.start();
            } catch (Exception e) {
                System.err.println("[JfrTail] RecordingStream FAILED: " + e.getMessage());
                e.printStackTrace();
            }
        });
        System.out.println("[JfrTail] JFR Stream started");
    }

    private void processEvent(RecordedEvent event) {
        System.out.println("[DEBUG] Captured JFR Event: " + event.getEventType().getName());
        JfrEvent jfrEvent = new JfrEvent();
        jfrEvent.setTs(event.getStartTime());
        jfrEvent.setPid(ProcessHandle.current().pid());
        jfrEvent.setEvent(event.getEventType().getName());

        if (event.getThread() != null) {
            jfrEvent.setThread(event.getThread().getJavaName());
        } else {
            jfrEvent.setThread("System");
        }

        if (event.hasField("duration")) {
            jfrEvent.setDurationMs(event.getDuration("duration").toNanos() / 1_000_000.0);
        }

        // Extract detailed fields for better visibility
        java.util.Map<String, Object> fields = new java.util.HashMap<>();
        for (jdk.jfr.ValueDescriptor descriptor : event.getEventType().getFields()) {
            String name = descriptor.getName();
            if ("startTime".equals(name) || "duration".equals(name) || "eventThread".equals(name)
                    || "stackTrace".equals(name)) {
                continue;
            }
            fields.put(name, extractValue(event.getValue(name)));
        }
        jfrEvent.setFields(fields);

        // Update Stats
        statsManager.accept(jfrEvent);

        // Check Alerts
        if (alertManager != null) {
            alertManager.check(jfrEvent);
        }

        // Broadcast to TCP Clients
        if (!tcpClients.isEmpty()) {
            try {
                String json = JsonUtils.toJson(jfrEvent);
                for (PrintWriter writer : tcpClients) {
                    writer.println(json);
                    writer.flush();
                }
                System.out.println("[DEBUG] Broadcasted to " + tcpClients.size() + " clients. JSON: "
                        + json.substring(0, Math.min(json.length(), 50)) + "...");
            } catch (Exception e) {
                System.err.println("[JfrTail] ERROR broadcasting event: " + e.getMessage());
            }
        }
    }

    private Object extractValue(Object value) {
        if (value == null)
            return null;
        if (value instanceof jdk.jfr.consumer.RecordedClass) {
            return ((jdk.jfr.consumer.RecordedClass) value).getName();
        } else if (value instanceof jdk.jfr.consumer.RecordedThread) {
            return ((jdk.jfr.consumer.RecordedThread) value).getJavaName();
        } else if (value instanceof jdk.jfr.consumer.RecordedObject) {
            jdk.jfr.consumer.RecordedObject ro = (jdk.jfr.consumer.RecordedObject) value;
            java.util.Map<String, Object> map = new java.util.HashMap<>();
            for (jdk.jfr.ValueDescriptor v : ro.getFields()) {
                map.put(v.getName(), extractValue(ro.getValue(v.getName())));
            }
            return map;
        }
        return value.toString();
    }

    public void stop() {
        if (recordingStream != null)
            recordingStream.close();
        if (webServer != null)
            webServer.stop();
        executor.shutdownNow();
    }

    public StatsManager getStatsManager() {
        return statsManager;
    }

    public AlertManager getAlertManager() {
        return alertManager;
    }
}
