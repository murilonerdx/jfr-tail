package io.jfrtail.spring.autoconfigure;

import io.jfrtail.agent.api.JfrTailMonitor;
import io.jfrtail.agent.api.StatsManager;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import java.util.Collections;

@AutoConfiguration(after = JfrTailAutoConfiguration.class)
@ConditionalOnClass(MeterRegistry.class)
@ConditionalOnProperty(prefix = "jfr-tail", name = "metrics.enabled", havingValue = "true", matchIfMissing = true)
public class JfrTailMetricsAutoConfiguration {

    private final MeterRegistry registry;

    public JfrTailMetricsAutoConfiguration(MeterRegistry registry) {
        this.registry = registry;
    }

    @PostConstruct
    public void bindMetrics() {
        StatsManager stats = JfrTailMonitor.getInstance().getStatsManager();

        Gauge.builder("jfrtail.events.total", stats, StatsManager::getTotalEvents)
                .description("Total JFR events captured")
                .register(registry);

        Gauge.builder("jfrtail.gc.count", stats, StatsManager::getGcCount)
                .description("Number of GC events")
                .register(registry);

        Gauge.builder("jfrtail.gc.pause", stats, StatsManager::getLastGcPauseMs)
                .description("Last GC pause duration in ms")
                .register(registry);

        Gauge.builder("jfrtail.exceptions.count", stats, StatsManager::getExceptionCount)
                .description("Number of exceptions thrown")
                .register(registry);

        Gauge.builder("jfrtail.locks.count", stats, StatsManager::getLockCount)
                .description("Number of lock contention events")
                .register(registry);

        Gauge.builder("jfrtail.heap.used", stats, stats1 -> stats1.getHeapUsed() / (1024.0 * 1024.0))
                .description("Current heap used in MB")
                .register(registry);
    }
}
