package io.jfrtail.cli;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.googlecode.lanterna.terminal.Terminal;
import io.jfrtail.common.JfrEvent;
import io.jfrtail.common.JsonUtils;

import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.time.format.DateTimeFormatter;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class TuiManager {
    private final String host;
    private final int port;
    private final String recordFilePath;
    private final String token;

    private final io.jfrtail.cli.spring.ActuatorClient actuatorClient;

    // UI State
    private boolean showSpringPanel = false;
    private String healthStatus = "UNKNOWN";
    private String topEndPoints = "Loading...";

    private final List<JfrEvent> events = new CopyOnWriteArrayList<>();
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");
    private volatile boolean running = true;

    // New UI State for v1.3.0
    private int selectedEventIndex = 0;
    private boolean showJsonModal = false;
    private boolean filteringMode = false;
    private final StringBuilder filterInput = new StringBuilder();

    private long gcCount = 0;
    private long lockCount = 0;
    private long exceptionCount = 0;
    private long totalEvents = 0;
    private final java.util.Map<String, Long> memoryStats = new java.util.concurrent.ConcurrentHashMap<>();
    private String lastAlert = "";
    private long lastAlertTime = 0;

    // Chart history (last 20 seconds)
    private final LinkedList<Integer> eventsPerSecondHistory = new LinkedList<>();
    private int currentSecondEvents = 0;
    private long lastSecondTick = System.currentTimeMillis();

    private PrintWriter fileWriter;

    public TuiManager(String host, int port, String recordFilePath, String actuatorUrl, String actuatorUser,
            String actuatorPass, String token) {
        this.host = host;
        this.port = port;
        this.recordFilePath = recordFilePath;
        this.token = token;

        if (actuatorUrl != null) {
            this.actuatorClient = new io.jfrtail.cli.spring.ActuatorClient(actuatorUrl, actuatorUser, actuatorPass,
                    token);
        } else {
            this.actuatorClient = null;
        }
    }

    // Constructor for backward compatibility (JVM Mode / No Token)
    public TuiManager(String host, int port, String recordFilePath) {
        this(host, port, recordFilePath, null, null, null, null);
    }

    // Constructor for Mock/Test (Tokenless)
    public TuiManager(String host, int port, String recordFilePath, String actuatorUrl, String actuatorUser,
            String actuatorPass) {
        this(host, port, recordFilePath, actuatorUrl, actuatorUser, actuatorPass, null);
    }

    public void start() throws Exception {
        // Initialize Recording
        if (recordFilePath != null) {
            fileWriter = new PrintWriter(new FileWriter(recordFilePath, true), true);
        }

        DefaultTerminalFactory terminalFactory = new DefaultTerminalFactory();
        // Force Swing on Windows to avoid console issues
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            terminalFactory.setForceTextTerminal(false);
            terminalFactory.setPreferTerminalEmulator(true);
        }

        Terminal terminal = terminalFactory.createTerminal();
        Screen screen = new TerminalScreen(terminal);
        screen.startScreen();
        screen.setCursorPosition(null);

        // Background thread to read from socket
        Thread networkThread = new Thread(() -> {
            while (running) {
                try (Socket socket = new Socket(host, port);
                        PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

                    initDebugLog();
                    logDebug("CLI Network Thread Connected to " + host + ":" + port);

                    // Synthesize a local event to notify user of connection
                    JfrEvent connEvent = new JfrEvent();
                    connEvent.setTs(java.time.Instant.now());
                    connEvent.setEvent("CLI_CONNECTED");
                    connEvent.setThread("Connected to " + host + ":" + port);
                    events.add(0, connEvent);

                    // AUTH HANDSHAKE
                    if (token != null) {
                        out.println("AUTH " + token);
                        String response = in.readLine();
                        logDebug("Auth Response: " + response);
                        if (response != null && !response.startsWith("OK")) {
                            // Auth Failed
                            JfrEvent err = new JfrEvent();
                            err.setTs(java.time.Instant.now());
                            err.setEvent("AUTH FAILED");
                            err.setThread(response);
                            events.add(0, err);
                        }
                    }

                    String line;
                    while (running && (line = in.readLine()) != null) {
                        logDebug("RCV: " + line);
                        processEventLine(line);
                    }
                } catch (Exception e) {
                    logDebug("Network Error (Will retry): " + e.getMessage());
                    // Notify user via event stream
                    JfrEvent disconn = new JfrEvent();
                    disconn.setTs(java.time.Instant.now());
                    disconn.setEvent("CLI_DISCONNECTED");
                    disconn.setThread("Connection lost: " + e.getMessage() + ". Retrying in 5s...");
                    events.add(0, disconn);
                }

                // Wait before reconnecting
                if (running) {
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        });
        networkThread.setDaemon(true);
        networkThread.start();

        // ACTUATOR POLLER THREAD
        if (actuatorClient != null) {
            Thread pollThread = new Thread(() -> {
                while (running) {
                    try {
                        // 1. Health
                        String healthJson = actuatorClient.health();
                        if (healthJson.contains("UP"))
                            healthStatus = "UP";
                        else if (healthJson.contains("DOWN"))
                            healthStatus = "DOWN";
                        else
                            healthStatus = "UNKNOWN";

                        // 2. Metrics (Top Endpoints)
                        String metrics = actuatorClient.metric("http.server.requests");
                        if (metrics != null && metrics.contains("measurements")) {
                            try {
                                com.fasterxml.jackson.databind.JsonNode root = JsonUtils.fromJson(metrics,
                                        com.fasterxml.jackson.databind.JsonNode.class);
                                com.fasterxml.jackson.databind.JsonNode measurements = root.get("measurements");
                                double count = 0;
                                double totalTime = 0;
                                if (measurements != null && measurements.isArray()) {
                                    for (com.fasterxml.jackson.databind.JsonNode m : measurements) {
                                        String stat = m.get("statistic").asText();
                                        double val = m.get("value").asDouble();
                                        if ("COUNT".equals(stat))
                                            count = val;
                                        if ("TOTAL_TIME".equals(stat))
                                            totalTime = val;
                                    }
                                }
                                topEndPoints = String.format("Requests: %.0f | Total Time: %.2fs", count, totalTime);
                            } catch (Exception e) {
                                topEndPoints = "Parse Error: " + e.getMessage();
                            }
                        }

                        // 3. JFR Stats (New Native Endpoint)
                        String jfrStats = actuatorClient.get("/jfrtail");
                        if (jfrStats != null && jfrStats.contains("metrics")) {
                            try {
                                @SuppressWarnings("unchecked")
                                java.util.Map<String, Object> data = JsonUtils.fromJson(jfrStats, java.util.Map.class);
                                @SuppressWarnings("unchecked")
                                java.util.Map<String, Object> stats = (java.util.Map<String, Object>) data
                                        .get("metrics");
                                if (stats != null) {
                                    if (stats.containsKey("heap_used_mb"))
                                        memoryStats.put("heap_used_mb",
                                                Long.parseLong(stats.get("heap_used_mb").toString()));
                                    if (stats.containsKey("heap_committed_mb"))
                                        memoryStats.put("heap_committed_mb",
                                                Long.parseLong(stats.get("heap_committed_mb").toString()));
                                    if (stats.containsKey("last_gc_pause_ms"))
                                        memoryStats.put("last_gc_pause_ms",
                                                Long.parseLong(stats.get("last_gc_pause_ms").toString()));
                                    totalEvents = Long.parseLong(stats.getOrDefault("total_events", "0").toString());
                                    gcCount = Long.parseLong(stats.getOrDefault("gc_count", "0").toString());
                                    lockCount = Long.parseLong(stats.getOrDefault("lock_count", "0").toString());
                                    exceptionCount = Long
                                            .parseLong(stats.getOrDefault("exception_count", "0").toString());
                                }
                            } catch (Exception e) {
                                logDebug("JFR Stats Parse Error: " + e.getMessage());
                            }
                        }

                        Thread.sleep(2000);
                    } catch (Exception e) {
                        healthStatus = "ERR";
                    }
                }
            });
            pollThread.setDaemon(true);
            pollThread.start();
        }

        // UI Refresh loop
        while (running) {
            long now = System.currentTimeMillis();
            if (now - lastSecondTick >= 1000) {
                if (eventsPerSecondHistory.size() > 20)
                    eventsPerSecondHistory.removeFirst();
                eventsPerSecondHistory.add(currentSecondEvents);
                currentSecondEvents = 0;
                lastSecondTick = now;
            }

            KeyStroke keyStroke = screen.pollInput();
            if (keyStroke != null) {
                if (filteringMode) {
                    if (keyStroke.getKeyType() == KeyType.Enter || keyStroke.getKeyType() == KeyType.Escape) {
                        filteringMode = false;
                    } else if (keyStroke.getKeyType() == KeyType.Backspace && filterInput.length() > 0) {
                        filterInput.setLength(filterInput.length() - 1);
                        selectedEventIndex = 0;
                    } else if (keyStroke.getCharacter() != null) {
                        filterInput.append(keyStroke.getCharacter());
                        selectedEventIndex = 0;
                    }
                } else if (showJsonModal) {
                    if (keyStroke.getKeyType() == KeyType.Escape || keyStroke.getKeyType() == KeyType.Enter) {
                        showJsonModal = false;
                    }
                } else {
                    if (keyStroke.getKeyType() == KeyType.Escape) {
                        running = false;
                        break;
                    } else if (keyStroke.getKeyType() == KeyType.ArrowUp) {
                        if (selectedEventIndex > 0)
                            selectedEventIndex--;
                    } else if (keyStroke.getKeyType() == KeyType.ArrowDown) {
                        selectedEventIndex++;
                    } else if (keyStroke.getKeyType() == KeyType.Enter) {
                        showJsonModal = true;
                    } else if (keyStroke.getCharacter() != null) {
                        char c = keyStroke.getCharacter();
                        if (c == 'q') {
                            running = false;
                            break;
                        } else if (c == 'c') {
                            clearData();
                        } else if (c == 's') {
                            showSpringPanel = !showSpringPanel;
                        } else if (c == 'b') {
                            createIncidentBundle();
                        } else if (c == 'f') {
                            filteringMode = true;
                        }
                    }
                }
            }

            // check if resize is needed
            screen.doResizeIfNecessary();

            draw(screen);
            logDebug("UI Loop Tick - events: " + events.size() + ", total: " + totalEvents);
            Thread.sleep(100);
        }

        cleanup();
        screen.stopScreen();
        System.exit(0);
    }

    private void processEventLine(String line) {
        try {
            if (fileWriter != null)
                fileWriter.println(line);

            if (line == null || line.trim().isEmpty())
                return;

            JfrEvent event = JsonUtils.fromJson(line, JfrEvent.class);
            updateStats(event);
            events.add(0, event);
            if (events.size() > 500) {
                events.remove(events.size() - 1);
            }
        } catch (Exception e) {
            logDebug("JSON PARSE ERROR: " + e.getMessage() + " | LINE: " + line);
            JfrEvent err = new JfrEvent();
            err.setTs(java.time.Instant.now());
            err.setEvent("PARSE ERROR");
            // Show first 50 chars of line to see what's wrong
            String snippet = line.length() > 50 ? line.substring(0, 50) + "..." : line;
            err.setThread(snippet);
            events.add(0, err);
        }
    }

    private void clearData() {
        events.clear();
        gcCount = 0;
        lockCount = 0;
        exceptionCount = 0;
        totalEvents = 0;
        eventsPerSecondHistory.clear();
    }

    private PrintWriter debugLog;

    private void initDebugLog() {
        try {
            debugLog = new PrintWriter(new FileWriter("debug-cli.log", true), true);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void logDebug(String msg) {
        if (debugLog != null) {
            debugLog.println(java.time.Instant.now() + " " + msg);
        }
    }

    private void cleanup() {
        if (fileWriter != null)
            fileWriter.close();
        if (debugLog != null)
            debugLog.close();
    }

    private void updateStats(JfrEvent event) {
        totalEvents++;
        currentSecondEvents++;
        if (event.getEvent() != null) {
            if (event.getEvent().contains("GarbageCollection"))
                gcCount++;
            else if (event.getEvent().contains("JavaMonitorEnter"))
                lockCount++;
            else if (event.getEvent().contains("ExceptionThrown")) {
                exceptionCount++;
                triggerAlert("EXCEPTION SPIKE DETECTED!");
            }
        }

        // Threshold check for GC
        if (event.getDurationMs() != null && event.getDurationMs() > 500) {
            triggerAlert("STALL DETECTED: GC PAUSE " + event.getDurationMs() + "ms");
        }
    }

    private void triggerAlert(String msg) {
        this.lastAlert = msg;
        this.lastAlertTime = System.currentTimeMillis();
    }

    private void draw(Screen screen) throws IOException {
        screen.clear();
        TextGraphics tg = screen.newTextGraphics();
        TerminalSize size = screen.getTerminalSize();
        int width = size.getColumns();
        int height = size.getRows();

        // 1. Top Bar (Header)
        drawBox(tg, 0, 0, width, 3);
        tg.setForegroundColor(TextColor.ANSI.CYAN);
        tg.putString(2, 1, "JFR-TAIL " + (actuatorClient != null ? "[SPRING MODE]" : "[JVM MODE]"));

        tg.setForegroundColor(TextColor.ANSI.GREEN);
        tg.putString(width - 20, 1, "[CONNECTED]");
        tg.putString(width - 40, 1, "Web: http://" + host + ":8080 (Agent)");
        tg.putString(width - 50, 1, "Rec: " + (recordFilePath != null ? "ON" : "OFF"));

        if (System.currentTimeMillis() - lastAlertTime < 5000) {
            tg.setForegroundColor(TextColor.ANSI.RED);
            tg.setBackgroundColor(TextColor.ANSI.YELLOW);
            tg.putString(width / 2 - 10, 1, " ALERT: " + lastAlert + " ");
            tg.setBackgroundColor(TextColor.ANSI.BLACK);
        }

        if (showSpringPanel && actuatorClient != null) {
            drawSpringPanel(tg, width, height);
        } else {
            drawStandardLayout(tg, width, height);
        }

        // Footer
        tg.setBackgroundColor(TextColor.ANSI.WHITE);
        tg.setForegroundColor(TextColor.ANSI.BLACK);
        String footer = " Q:Quit | C:Clear | S:Spring | B:Bundle | F:Filter | Enter:Detail ";
        if (filteringMode) {
            footer = " TYPE TO FILTER... [ENTER/ESC to finish] | Buffer: " + filterInput.toString();
        }
        tg.putString(0, height - 1, String.format("%-" + width + "s", footer));

        if (showJsonModal) {
            drawJsonModal(tg, width, height);
        }

        screen.refresh();
    }

    private void drawStandardLayout(TextGraphics tg, int width, int height) {
        int splitCol = (int) (width * 0.7);

        // --- LOG PANEL (Left) ---
        drawBox(tg, 0, 3, splitCol, height - 4);
        tg.putString(2, 2, " LIVE EVENTS ");

        int row = 4;

        // Filter logic
        List<JfrEvent> filtered = events.stream()
                .filter(e -> {
                    if (filterInput.length() == 0)
                        return true;
                    String f = filterInput.toString().toLowerCase();
                    String type = e.getEvent() != null ? e.getEvent().toLowerCase() : "";
                    String thread = e.getThread() != null ? e.getThread().toLowerCase() : "";
                    return type.contains(f) || thread.contains(f);
                })
                .collect(java.util.stream.Collectors.toList());

        if (selectedEventIndex >= filtered.size() && filtered.size() > 0) {
            selectedEventIndex = filtered.size() - 1;
        }

        for (int i = 0; i < filtered.size(); i++) {
            if (row >= height - 2)
                break;
            JfrEvent event = filtered.get(i);

            String time = event.getTs() != null
                    ? timeFormatter.format(event.getTs().atZone(java.time.ZoneId.systemDefault()))
                    : "??:??:??";
            String type = event.getEvent() != null ? event.getEvent().replace("jdk.", "") : "Unknown";
            String dur = event.getDurationMs() != null ? String.format("%.1fms", event.getDurationMs()) : "";

            if (i == selectedEventIndex && !filteringMode && !showJsonModal) {
                tg.setBackgroundColor(TextColor.ANSI.WHITE);
                tg.setForegroundColor(TextColor.ANSI.BLACK);
            } else {
                tg.setBackgroundColor(TextColor.ANSI.BLACK);
                if (type.contains("GarbageCollection"))
                    tg.setForegroundColor(TextColor.ANSI.YELLOW);
                else if (type.contains("JavaMonitor") || type.contains("ThreadPark"))
                    tg.setForegroundColor(TextColor.ANSI.RED);
                else if (type.contains("ExceptionThrown"))
                    tg.setForegroundColor(TextColor.ANSI.MAGENTA);
                else if (type.contains("CPULoad"))
                    tg.setForegroundColor(TextColor.ANSI.GREEN);
                else
                    tg.setForegroundColor(TextColor.ANSI.WHITE);
            }

            StringBuilder fieldStr = new StringBuilder();
            if (event.getFields() != null) {
                event.getFields().forEach((k, v) -> {
                    if (fieldStr.length() > 0)
                        fieldStr.append(", ");
                    fieldStr.append(k).append("=").append(v);
                });
            }

            String line = String.format("%s | %-16s | %-6s | %s", time, compact(type, 16), dur,
                    fieldStr.toString());
            tg.putString(2, row++, truncate(line, splitCol - 4));
            tg.setBackgroundColor(TextColor.ANSI.BLACK); // Reset for next line
        }

        // --- STATS PANEL (Right) ---
        drawBox(tg, splitCol, 3, width - splitCol, height - 4);
        tg.setForegroundColor(TextColor.ANSI.GREEN);
        tg.putString(splitCol + 2, 4, "STATISTICS");

        tg.setForegroundColor(TextColor.ANSI.WHITE);
        tg.putString(splitCol + 2, 8, String.format("GC Evt: %d", gcCount));
        tg.putString(splitCol + 2, 9, String.format("Excep:  %d", exceptionCount));
        tg.putString(splitCol + 2, 10, String.format("Locks:  %d", lockCount));

        tg.setForegroundColor(TextColor.ANSI.CYAN);
        tg.putString(splitCol + 2, 11, "MEMORY");
        tg.setForegroundColor(TextColor.ANSI.WHITE);
        tg.putString(splitCol + 2, 12, String.format("Used:  %4d MB", memoryStats.getOrDefault("heap_used_mb", 0L)));
        tg.putString(splitCol + 2, 13,
                String.format("Comm:  %4d MB", memoryStats.getOrDefault("heap_committed_mb", 0L)));
        tg.putString(splitCol + 2, 14,
                String.format("GC Ps: %4d ms", memoryStats.getOrDefault("last_gc_pause_ms", 0L)));

        // ASCII Chart (Simple Bar)
        tg.setForegroundColor(TextColor.ANSI.CYAN);
        tg.putString(splitCol + 2, 12, "Events/Sec (Last 20s):");
        int chartRow = 14;
        for (Integer val : eventsPerSecondHistory) {
            if (chartRow >= height - 2)
                break;
            String bar = new String(new char[Math.min(val, Math.max(0, (width - splitCol) - 10))]).replace('\0', '#');
            tg.putString(splitCol + 2, chartRow++, String.format("%3d |%s", val, bar));
        }
    }

    private void drawSpringPanel(TextGraphics tg, int width, int height) {
        drawBox(tg, 0, 3, width, height - 4);
        tg.setForegroundColor(TextColor.ANSI.GREEN);
        tg.putString(2, 4, "SPRING BOOT HEALTH: " + healthStatus);

        tg.setForegroundColor(TextColor.ANSI.WHITE);
        tg.putString(2, 6, "Top Endpoints (via Actuator):");
        tg.putString(2, 7, topEndPoints);

        tg.setForegroundColor(TextColor.ANSI.YELLOW);
        tg.putString(2, 10, "Correlated JFR Metrics:");
        tg.putString(2, 11, "GC Events in last minute: " + gcCount);
        tg.putString(2, 12, "Exceptions in last minute: " + exceptionCount);
    }

    private void drawJsonModal(TextGraphics tg, int width, int height) {
        int modalW = (int) (width * 0.8);
        int modalH = (int) (height * 0.7);
        int x = (width - modalW) / 2;
        int y = (height - modalH) / 2;

        tg.setBackgroundColor(TextColor.ANSI.BLACK);
        tg.setForegroundColor(TextColor.ANSI.WHITE);
        for (int i = 0; i < modalH; i++) {
            tg.putString(x, y + i, String.format("%" + modalW + "s", " "));
        }
        drawBox(tg, x, y, modalW, modalH);
        tg.setForegroundColor(TextColor.ANSI.CYAN);
        tg.putString(x + 2, y, " EVENT DETAILS (JSON) ");

        // Get selected event
        List<JfrEvent> filtered = events.stream()
                .filter(e -> {
                    if (filterInput.length() == 0)
                        return true;
                    String f = filterInput.toString().toLowerCase();
                    String type = e.getEvent() != null ? e.getEvent().toLowerCase() : "";
                    String thread = e.getThread() != null ? e.getThread().toLowerCase() : "";
                    return type.contains(f) || thread.contains(f);
                })
                .collect(java.util.stream.Collectors.toList());

        if (selectedEventIndex >= 0 && selectedEventIndex < filtered.size()) {
            JfrEvent selected = filtered.get(selectedEventIndex);
            try {
                String json = JsonUtils.toJson(selected);
                String[] lines = json.split("\n");
                tg.setForegroundColor(TextColor.ANSI.WHITE);
                for (int i = 0; i < lines.length && i < modalH - 4; i++) {
                    tg.putString(x + 2, y + 2 + i, truncate(lines[i], modalW - 4));
                }
            } catch (Exception e) {
                tg.putString(x + 2, y + 2, "Error rendering JSON: " + e.getMessage());
            }
        } else {
            tg.putString(x + 2, y + 2, "No event selected.");
        }

        tg.setForegroundColor(TextColor.ANSI.YELLOW);
        tg.putString(x + 2, y + modalH - 1, " [ESC/ENTER to close] ");
    }

    private void createIncidentBundle() {
        try {
            String fname = "incident-" + System.currentTimeMillis() + ".txt";
            try (PrintWriter pw = new PrintWriter(new FileWriter(fname))) {
                pw.println("INCIDENT BUNDLE");
                pw.println("Health: " + healthStatus);
                for (JfrEvent e : events)
                    pw.println(e.toString());
            }
        } catch (Exception e) {
        }
    }

    private void drawBox(TextGraphics tg, int x, int y, int w, int h) {
        tg.setForegroundColor(TextColor.ANSI.WHITE);
        tg.drawLine(x, y, x + w - 1, y, '-');
        tg.drawLine(x, y + h - 1, x + w - 1, y + h - 1, '-');
        tg.drawLine(x, y, x, y + h - 1, '|');
        tg.drawLine(x + w - 1, y, x + w - 1, y + h - 1, '|');
        tg.putString(x, y, "+");
        tg.putString(x + w - 1, y, "+");
        tg.putString(x, y + h - 1, "+");
        tg.putString(x + w - 1, y + h - 1, "+");
    }

    private String compact(String s, int max) {
        if (s.length() <= max)
            return s;
        return s.substring(0, max);
    }

    private String truncate(String s, int max) {
        if (s == null)
            return "";
        if (max < 4)
            return s.length() > max ? (max > 0 ? s.substring(0, max) : "") : s;
        if (s.length() <= max)
            return s;
        return s.substring(0, max - 3) + "...";
    }
}
