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
    private final String actuatorUrl;
    private final String token;

    private final io.jfrtail.cli.spring.ActuatorClient actuatorClient;

    // UI State
    private boolean showSpringPanel = false;
    private String healthStatus = "UNKNOWN";
    private String topEndPoints = "Loading...";

    private final List<JfrEvent> events = new CopyOnWriteArrayList<>();
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");
    private volatile boolean running = true;

    private String eventFilter = "";
    private long gcCount = 0;
    private long lockCount = 0;
    private long exceptionCount = 0;
    private long totalEvents = 0;

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
        this.actuatorUrl = actuatorUrl;
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
            try (Socket socket = new Socket(host, port);
                    PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                    BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

                // AUTH HANDSHAKE
                if (token != null) {
                    out.println("AUTH " + token);
                    String response = in.readLine();
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
                    processEventLine(line);
                }
            } catch (Exception e) {
                // Log error
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

                        // 2. Metrics (Top Endpoints Simulation)
                        String metrics = actuatorClient.metric("http.server.requests");
                        if (metrics.length() > 50)
                            topEndPoints = "Data received (JSON parsing TODO)";
                        else
                            topEndPoints = "No http.server.requests data";

                        Thread.sleep(5000);
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
                if (keyStroke.getKeyType() == KeyType.Escape
                        || (keyStroke.getCharacter() != null && keyStroke.getCharacter() == 'q')) {
                    running = false;
                    break;
                } else if (keyStroke.getCharacter() != null) {
                    char c = keyStroke.getCharacter();
                    if (c == 'c') {
                        clearData();
                    } else if (c == 's') {
                        showSpringPanel = !showSpringPanel;
                    } else if (c == 'b') {
                        createIncidentBundle();
                    }
                }
            }

            // check if resize is needed
            screen.doResizeIfNecessary();

            draw(screen);
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

            JfrEvent event = JsonUtils.fromJson(line, JfrEvent.class);
            updateStats(event);
            events.add(0, event);
            if (events.size() > 500) {
                events.remove(events.size() - 1);
            }
        } catch (Exception e) {
            // Ignore parse errors
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

    private void cleanup() {
        if (fileWriter != null)
            fileWriter.close();
    }

    private void updateStats(JfrEvent event) {
        totalEvents++;
        currentSecondEvents++;
        if (event.getEvent() != null) {
            if (event.getEvent().contains("GarbageCollection"))
                gcCount++;
            else if (event.getEvent().contains("JavaMonitorEnter"))
                lockCount++;
            else if (event.getEvent().contains("ExceptionThrown"))
                exceptionCount++;
        }
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
        tg.putString(width - 40, 1, "Web: http://" + host + ":8080 (Agent)");
        tg.putString(width - 50, 1, "Rec: " + (recordFilePath != null ? "ON" : "OFF"));

        if (showSpringPanel && actuatorClient != null) {
            drawSpringPanel(tg, width, height);
        } else {
            drawStandardLayout(tg, width, height);
        }

        // Footer
        tg.setBackgroundColor(TextColor.ANSI.WHITE);
        tg.setForegroundColor(TextColor.ANSI.BLACK);
        String footer = " Q: Quit | C: Clear | S: Spring Panel | B: Bundle Zip ";
        tg.putString(0, height - 1, String.format("%-" + width + "s", footer));

        screen.refresh();
    }

    private void drawStandardLayout(TextGraphics tg, int width, int height) {
        int splitCol = (int) (width * 0.7);

        // --- LOG PANEL (Left) ---
        drawBox(tg, 0, 3, splitCol, height - 4);
        tg.putString(2, 2, " LIVE EVENTS ");

        int row = 4;
        for (JfrEvent event : events) {
            if (row >= height - 2)
                break;
            if (event.getEvent() != null && !event.getEvent().toLowerCase().contains(eventFilter.toLowerCase()))
                continue;

            String time = event.getTs() != null
                    ? timeFormatter.format(event.getTs().atZone(java.time.ZoneId.systemDefault()))
                    : "??:??:??";
            String type = event.getEvent() != null ? event.getEvent().replace("jdk.", "") : "Unknown";
            String dur = event.getDurationMs() != null ? String.format("%.1fms", event.getDurationMs()) : "";

            if (type.contains("GarbageCollection"))
                tg.setForegroundColor(TextColor.ANSI.YELLOW);
            else if (type.contains("JavaMonitorEnter"))
                tg.setForegroundColor(TextColor.ANSI.RED);
            else if (type.contains("ExceptionThrown"))
                tg.setForegroundColor(TextColor.ANSI.MAGENTA);
            else
                tg.setForegroundColor(TextColor.ANSI.WHITE);

            String line = String.format("%s | %-16s | %-6s | %s", time, compact(type, 16), dur,
                    event.getFields() != null ? event.getFields() : "");
            tg.putString(2, row++, truncate(line, splitCol - 4));
        }

        // --- STATS PANEL (Right) ---
        drawBox(tg, splitCol, 3, width - splitCol, height - 4);
        tg.setForegroundColor(TextColor.ANSI.GREEN);
        tg.putString(splitCol + 2, 4, "STATISTICS");

        tg.setForegroundColor(TextColor.ANSI.WHITE);
        tg.putString(splitCol + 2, 6, String.format("Total: %d", totalEvents));
        tg.setForegroundColor(TextColor.ANSI.YELLOW);
        tg.putString(splitCol + 2, 7, String.format("GC:    %d", gcCount));
        tg.setForegroundColor(TextColor.ANSI.RED);
        tg.putString(splitCol + 2, 8, String.format("Locks: %d", lockCount));
        tg.setForegroundColor(TextColor.ANSI.MAGENTA);
        tg.putString(splitCol + 2, 9, String.format("Excep: %d", exceptionCount));

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
        if (s.length() <= max)
            return s;
        return s.substring(0, Math.max(0, max - 1)) + "~";
    }
}
