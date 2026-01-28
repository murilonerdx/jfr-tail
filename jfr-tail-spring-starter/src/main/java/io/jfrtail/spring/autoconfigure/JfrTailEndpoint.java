package io.jfrtail.spring.autoconfigure;

import io.jfrtail.agent.api.JfrTailMonitor;
import io.jfrtail.agent.api.StatsManager;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.util.Map;

@Component
@Endpoint(id = "jfrtail")
public class JfrTailEndpoint {

    @ReadOperation
    public Map<String, Object> jfrStats() {
        try {
            // Access stats manager via reflection from the singleton to avoid API changes
            JfrTailMonitor instance = JfrTailMonitor.getInstance();
            Field field = JfrTailMonitor.class.getDeclaredField("statsManager");
            field.setAccessible(true);
            StatsManager statsManager = (StatsManager) field.get(instance);
            return statsManager.getSnapshot();
        } catch (Exception e) {
            return Map.of("error", "Could not retrieve stats: " + e.getMessage());
        }
    }
}
