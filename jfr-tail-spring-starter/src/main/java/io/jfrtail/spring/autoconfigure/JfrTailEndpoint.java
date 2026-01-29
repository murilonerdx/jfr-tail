package io.jfrtail.spring.autoconfigure;

import io.jfrtail.agent.api.JfrTailMonitor;
import io.jfrtail.agent.api.StatsManager;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Endpoint(id = "jfrtail")
public class JfrTailEndpoint {

    @ReadOperation
    public Map<String, Object> jfrStats() {
        StatsManager statsManager = JfrTailMonitor.getInstance().getStatsManager();
        return statsManager.getSnapshot();
    }
}
