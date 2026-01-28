
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;

public class MockActuator {
    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(8081), 0);
        server.createContext("/actuator/health", new HttpHandler() {
            @Override
            public void handle(HttpExchange t) throws IOException {
                String response = "{\"status\":\"UP\",\"components\":{\"db\":{\"status\":\"UP\"}}}";
                send(t, response);
            }
        });
        server.createContext("/actuator/metrics/http.server.requests", new HttpHandler() {
            @Override
            public void handle(HttpExchange t) throws IOException {
                String response = "{\"name\":\"http.server.requests\",\"measurements\":[{\"statistic\":\"COUNT\",\"value\":120.0},{\"statistic\":\"TOTAL_TIME\",\"value\":5.0}]}";
                send(t, response);
            }
        });
        server.setExecutor(null);
        server.start();
        System.out.println("Mock Actuator running on 8081...");
    }

    private static void send(HttpExchange t, String response) throws IOException {
        t.getResponseHeaders().set("Content-Type", "application/json");
        t.sendResponseHeaders(200, response.length());
        OutputStream os = t.getResponseBody();
        os.write(response.getBytes());
        os.close();
    }
}
