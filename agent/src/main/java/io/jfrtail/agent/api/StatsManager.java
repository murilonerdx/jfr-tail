package io.jfrtail.agent.api;

import io.jfrtail.common.JfrEvent;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class StatsManager {
    private final AtomicLong gcCount = new AtomicLong(0);
    private final AtomicLong lockCount = new AtomicLong(0);
    private final AtomicLong exceptionCount = new AtomicLong(0);
    private final AtomicLong totalEvents = new AtomicLong(0);

    // Last minute metrics
    private final Map<String, AtomicLong> eventCounts = new ConcurrentHashMap<>();

    // Latest event for display
    private volatile JfrEvent lastEvent;

    public void accept(JfrEvent event) {
        totalEvents.incrementAndGet();
        lastEvent = event;

        String type = event.getEvent();
        eventCounts.computeIfAbsent(type, k -> new AtomicLong(0)).incrementAndGet();

        if (type.contains("GarbageCollection"))
            gcCount.incrementAndGet();
        else if (type.contains("JavaMonitor") || type.contains("ThreadPark"))
            lockCount.incrementAndGet();
        else if (type.contains("ExceptionThrown"))
            exceptionCount.incrementAndGet();
    }

    public Map<String, Object> getSnapshot() {
        return Map.of(
                "timestamp", Instant.now().toString(),
                "metrics", Map.of(
                        "total_events", totalEvents.get(),
                        "gc_count", gcCount.get(),
                        "lock_count", lockCount.get(),
                        "exception_count", exceptionCount.get()),
                "last_event", lastEvent != null ? lastEvent : "none");
    }
}
