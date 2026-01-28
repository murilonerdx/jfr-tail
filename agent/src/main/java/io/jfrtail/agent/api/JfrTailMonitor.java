package io.jfrtail.agent.api;

import io.jfrtail.agent.server.EmbeddedServer;
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
    private EmbeddedServer webServer;
    private RecordingStream recordingStream;
    private final Set<PrintWriter> tcpClients = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public static synchronized JfrTailMonitor getInstance() {
        if (instance == null) {
            instance = new JfrTailMonitor();
        }
        return instance;
    }

    private String secret;

    public void start(int webPort, int tcpPort) throws IOException {
        start(webPort, tcpPort, null);
    }

    public void start(int webPort, int tcpPort, String secret) throws IOException {
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
        webServer = new EmbeddedServer(webPort, statsManager, secret);
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

            // Wait for disconnect
            while (reader.readLine() != null) {
            }

        } catch (IOException e) {
            // Disconnect
        } finally {
            if (writer != null)
                tcpClients.remove(writer);
            try {
                client.close();
            } catch (Exception e) {
            }
        }
    }

    private void startRecording() {
        recordingStream = new RecordingStream();
        recordingStream.enable("jdk.GarbageCollection").withThreshold(Duration.ofMillis(10));
        recordingStream.enable("jdk.JavaMonitorEnter").withThreshold(Duration.ofMillis(10));
        recordingStream.enable("jdk.ExceptionThrown");
        recordingStream.setOrdered(false);

        recordingStream.onEvent(this::processEvent);

        executor.submit(() -> recordingStream.start());
        System.out.println("[JfrTail] JFR Stream started");
    }

    private void processEvent(RecordedEvent event) {
        JfrEvent jfrEvent = new JfrEvent();
        jfrEvent.setTs(event.getStartTime());
        jfrEvent.setPid(ProcessHandle.current().pid());
        jfrEvent.setEvent(event.getEventType().getName());
        jfrEvent.setThread(event.getThread().getJavaName());
        if (event.hasField("duration")) {
            jfrEvent.setDurationMs(event.getDuration("duration").toNanos() / 1_000_000.0);
        }

        // Update Stats
        statsManager.accept(jfrEvent);

        // Broadcast to TCP Clients
        if (!tcpClients.isEmpty()) {
            String json = JsonUtils.toJson(jfrEvent);
            for (PrintWriter writer : tcpClients) {
                writer.println(json);
                writer.flush();
            }
        }
    }

    public void stop() {
        if (recordingStream != null)
            recordingStream.close();
        if (webServer != null)
            webServer.stop();
        executor.shutdownNow();
    }
}
