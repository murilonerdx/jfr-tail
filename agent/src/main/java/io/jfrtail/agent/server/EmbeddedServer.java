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
    private HttpServer server;

    public EmbeddedServer(int port, StatsManager statsManager, String secret) {
        this.port = port;
        this.statsManager = statsManager;
        this.secret = secret;
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/jfr/stats", new AuthMiddleware(new StatsHandler()));
        server.createContext("/jfr/dashboard", new AuthMiddleware(new DashboardHandler()));
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        System.out.println("[JfrTail] Embedded Server started on port " + port);
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
            String auth = exchange.getRequestHeaders().getFirst("Authorization");
            if (auth == null || !auth.startsWith("Bearer ")) {
                send401(exchange);
                return;
            }

            String token = auth.substring(7);
            if (!io.jfrtail.common.security.JwtLite.verifyToken(token, secret)) {
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
                            body { font-family: monospace; background: #222; color: #eee; padding: 20px; }
                            .metric { font-size: 1.5em; margin: 10px; }
                            pre { background: #333; padding: 10px; border-radius: 5px; }
                        </style>
                    </head>
                    <body>
                        <h1>JFR-Tail Embedded Dashboard</h1>
                        <div id="app">Loading...</div>
                        <script>
                            fetch('/jfr/stats', {
                                headers: {
                                    'Authorization': 'Bearer ' + (new URLSearchParams(window.location.search)).get('token')
                                }
                            })
                            .then(r => r.json())
                            .then(data => {
                                document.getElementById('app').innerHTML =
                                    '<div class="metric">GC Events: ' + data.metrics.gc_count + '</div>' +
                                    '<div class="metric">Lock Events: ' + data.metrics.lock_count + '</div>' +
                                    '<div class="metric">Exceptions: ' + data.metrics.exception_count + '</div>' +
                                    '<h3>Last Event</h3>' +
                                    '<pre>' + JSON.stringify(data.last_event, null, 2) + '</pre>';
                            });
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
}
