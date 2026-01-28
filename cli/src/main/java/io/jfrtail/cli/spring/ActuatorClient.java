package io.jfrtail.cli.spring;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ActuatorClient {
    private final String baseUrl;
    private final String authHeader;
    private final HttpClient client;

    // Simple cache for polling results
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public ActuatorClient(String baseUrl, String user, String pass) {
        this(baseUrl, user, pass, null);
    }

    public ActuatorClient(String baseUrl, String user, String pass, String token) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();

        if (token != null && !token.isEmpty()) {
            this.authHeader = "Bearer " + token;
        } else if (user != null && !user.isEmpty()) {
            String creds = user + ":" + (pass != null ? pass : "");
            this.authHeader = "Basic " + Base64.getEncoder().encodeToString(creds.getBytes());
        } else {
            this.authHeader = null;
        }
    }

    public String health() {
        return get("/health");
    }

    public String metrics() {
        // Get all metrics names
        return get("/metrics");
    }

    public String metric(String name) {
        return get("/metrics/" + name);
    }

    public String threadDump() {
        return get("/threaddump");
    }

    public String env() {
        return get("/env");
    }

    public String get(String path) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path))
                    .timeout(Duration.ofSeconds(2))
                    .GET();

            if (authHeader != null) {
                builder.header("Authorization", authHeader);
            }

            HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                String body = response.body();
                cache.put(path, body); // Update cache
                return body;
            }
        } catch (Exception e) {
            // Ignore (poller will retry)
        }
        return cache.getOrDefault(path, "{}"); // Return stale data or empty
    }
}
