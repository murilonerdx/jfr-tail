package io.jfrtail.agent.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import io.jfrtail.agent.api.StatsManager;
import io.jfrtail.common.JsonUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

public class EmbeddedServer {
    private final int port;
    private final StatsManager statsManager;
    private final String secret;
    private final boolean statsEnabled;
    private final boolean dashboardEnabled;
    private HttpServer server;

    public EmbeddedServer(int port, StatsManager statsManager, String secret, boolean statsEnabled,
            boolean dashboardEnabled) {
        this.port = port;
        this.statsManager = statsManager;
        this.secret = secret;
        this.statsEnabled = statsEnabled;
        this.dashboardEnabled = dashboardEnabled;
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        if (statsEnabled) {
            server.createContext("/jfr/stats", new AuthMiddleware(new StatsHandler()));
            server.createContext("/jfr/metrics", new MetricsHandler()); // Public Prometheus metrics
            server.createContext("/jfr/history", new AuthMiddleware(new HistoryHandler()));
        }
        if (dashboardEnabled) {
            server.createContext("/jfr/dashboard", new AuthMiddleware(new DashboardHandler()));
            server.createContext("/jfr/bundle", new AuthMiddleware(new BundleHandler()));
        }
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        System.out.println("[JfrTail] Embedded Server started on port " + port);
        if (!statsEnabled)
            System.out.println("[JfrTail] Stats endpoint is DISABLED");
        if (!dashboardEnabled)
            System.out.println("[JfrTail] Dashboard endpoint is DISABLED");
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    // Auth Middleware
    private class AuthMiddleware implements HttpHandler {
        private final HttpHandler next;

        public AuthMiddleware(HttpHandler next) {
            this.next = next;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // CORS Headers
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Authorization, Content-Type");

            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            // Extract Token
            String token = null;
            String auth = exchange.getRequestHeaders().getFirst("Authorization");
            if (auth != null && auth.startsWith("Bearer ")) {
                token = auth.substring(7);
            } else {
                // Try query param
                String query = exchange.getRequestURI().getQuery();
                if (query != null && query.contains("token=")) {
                    token = query.split("token=")[1].split("&")[0];
                }
            }

            if (token == null || !io.jfrtail.common.security.JwtLite.verifyToken(token, secret)) {
                send401(exchange);
                return;
            }

            next.handle(exchange);
        }

        private void send401(HttpExchange exchange) throws IOException {
            String msg = "401 Unauthorized";
            exchange.sendResponseHeaders(401, msg.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(msg.getBytes());
            }
            exchange.close();
        }
    }

    private class StatsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            String jsonResponse = JsonUtils.toJson(statsManager.getSnapshot());
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            byte[] responseBytes = jsonResponse.getBytes();
            exchange.sendResponseHeaders(200, responseBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
        }
    }

    private class MetricsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            StringBuilder sb = new StringBuilder();
            java.util.Map<String, Object> snapshot = statsManager.getSnapshot();
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> metrics = (java.util.Map<String, Object>) snapshot.get("metrics");

            if (metrics != null) {
                metrics.forEach((k, v) -> {
                    sb.append("jfr_tail_").append(k).append(" ").append(v).append("\n");
                });
            }

            byte[] responseBytes = sb.toString().getBytes();
            exchange.sendResponseHeaders(200, responseBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
        }
    }

    private class HistoryHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String jsonResponse = JsonUtils.toJson(statsManager.getHistory());
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            byte[] responseBytes = jsonResponse.getBytes();
            exchange.sendResponseHeaders(200, responseBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
        }
    }

    private class DashboardHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String html = """
                    <!DOCTYPE html>
                    <html>
                    <head>
                        <title>JFR-Tail Dashboard</title>
                        <meta http-equiv="refresh" content="2">
                        <style>
                            body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background: #0f172a; color: #f1f5f9; padding: 20px; line-height: 1.6; }
                            .container { max-width: 1200px; margin: 0 auto; }
                            h1 { color: #38bdf8; border-bottom: 2px solid #334155; padding-bottom: 10px; }
                            .grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(250px, 1fr)); gap: 20px; margin-bottom: 30px; }
                            .card { background: #1e293b; padding: 20px; border-radius: 12px; box-shadow: 0 4px 6px -1px rgba(0,0,0,0.1); border: 1px solid #334155; }
                            .card h3 { margin-top: 0; color: #94a3b8; font-size: 0.9rem; text-transform: uppercase; }
                            .metric { font-size: 2.5rem; font-weight: bold; color: #f8fafc; }
                            .subtext { color: #64748b; font-size: 0.8rem; }
                            pre { background: #020617; padding: 15px; border-radius: 8px; overflow-x: auto; border: 1px solid #1e293b; color: #10b981; font-size: 0.85rem; }
                            .btn { background: #38bdf8; color: #0f172a; border: none; padding: 10px 20px; border-radius: 6px; cursor: pointer; font-weight: bold; text-decoration: none; display: inline-block; }
                            .btn:hover { background: #7dd3fc; }
                        </style>
                    </head>
                    <body>
                        <div class="container">
                            <h1>JFR-Tail Live Monitor</h1>
                            <div class="grid">
                                <div class="card">
                                    <h3>GC Events</h3>
                                    <div id="gc-count" class="metric">-</div>
                                    <div id="last-gc" class="subtext">Last Pause: - ms</div>
                                </div>
                                <div class="card">
                                    <h3>Exceptions</h3>
                                    <div id="exc-count" class="metric">-</div>
                                    <div class="subtext">Proactive tracking enabled</div>
                                </div>
                                <div class="card">
                                    <h3>Lock Contention</h3>
                                    <div id="lock-count" class="metric">-</div>
                                    <div class="subtext">Threads waiting</div>
                                </div>
                                <div class="card">
                                    <h3>Heap Used</h3>
                                    <div id="heap-used" class="metric">- MB</div>
                                    <div id="heap-committed" class="subtext">Committed: - MB</div>
                                </div>
                            </div>

                            <div style="margin-bottom: 20px;">
                                <a href="#" id="bundle-btn" class="btn">Download Incident Bundle</a>
                            </div>

                            <div class="card">
                                <h3>Latest Event Stream</h3>
                                <pre id="last-event">Waiting for events...</pre>
                            </div>
                        </div>

                        <script>
                            const token = (new URLSearchParams(window.location.search)).get('token');
                            document.getElementById('bundle-btn').href = '/jfr/bundle?token=' + token;

                            function update() {
                                fetch('/jfr/stats', {
                                    headers: { 'Authorization': 'Bearer ' + token }
                                })
                                .then(r => r.json())
                                .then(data => {
                                    document.getElementById('gc-count').innerText = data.metrics.gc_count;
                                    document.getElementById('last-gc').innerText = 'Last Pause: ' + data.metrics.last_gc_pause_ms + ' ms';
                                    document.getElementById('exc-count').innerText = data.metrics.exception_count;
                                    document.getElementById('lock-count').innerText = data.metrics.lock_count;
                                    document.getElementById('heap-used').innerText = data.metrics.heap_used_mb + ' MB';
                                    document.getElementById('heap-committed').innerText = 'Committed: ' + data.metrics.heap_committed_mb + ' MB';
                                    document.getElementById('last-event').innerText = JSON.stringify(data.last_event, null, 2);
                                })
                                .catch(err => console.error('Fetch error:', err));
                            }
                            setInterval(update, 2000);
                            update();
                        </script>
                    </body>
                    </html>
                    """;
            exchange.getResponseHeaders().set("Content-Type", "text/html");
            byte[] bytes = html.getBytes();
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    private class BundleHandler implements com.sun.net.httpserver.HttpHandler {
        @Override
        public void handle(com.sun.net.httpserver.HttpExchange exchange) throws java.io.IOException {
            // Re-check auth if token is in query param for easier download
            String query = exchange.getRequestURI().getQuery();
            String token = null;
            if (query != null && query.contains("token=")) {
                token = query.split("token=")[1].split("&")[0];
            } else {
                String auth = exchange.getRequestHeaders().getFirst("Authorization");
                if (auth != null && auth.startsWith("Bearer ")) {
                    token = auth.substring(7);
                }
            }

            if (token == null || !io.jfrtail.common.security.JwtLite.verifyToken(token, secret)) {
                exchange.sendResponseHeaders(401, -1);
                return;
            }

            java.util.Map<String, Object> bundle = java.util.Map.of(
                    "timestamp", java.time.Instant.now().toString(),
                    "snapshot", statsManager.getSnapshot(),
                    "history", statsManager.getHistory(),
                    "system", java.util.Map.of(
                            "os", System.getProperty("os.name"),
                            "java_version", System.getProperty("java.version"),
                            "available_processors", Runtime.getRuntime().availableProcessors(),
                            "total_memory_mb", Runtime.getRuntime().totalMemory() / (1024 * 1024)));

            String json = io.jfrtail.common.JsonUtils.toJson(bundle);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=jfr-incident-bundle.json");
            byte[] bytes = json.getBytes();
            exchange.sendResponseHeaders(200, bytes.length);
            try (java.io.OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }
}
