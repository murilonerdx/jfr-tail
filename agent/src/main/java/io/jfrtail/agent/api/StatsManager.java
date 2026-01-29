package io.jfrtail.agent.api;

import io.jfrtail.common.JfrEvent;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class StatsManager {
    private final AtomicLong gcCount = new AtomicLong(0);
    private final AtomicLong lockCount = new AtomicLong(0);
    private final AtomicLong exceptionCount = new AtomicLong(0);
    private final AtomicLong totalEvents = new AtomicLong(0);

    // Memory and GC details
    private final AtomicLong heapUsed = new AtomicLong(0);
    private final AtomicLong heapCommitted = new AtomicLong(0);
    private final AtomicLong lastGcPauseMs = new AtomicLong(0);

    // Last minute metrics
    private final Map<String, AtomicLong> eventCounts = new ConcurrentHashMap<>();

    // History Buffer (Circular)
    private static final int MAX_HISTORY = 50;
    private final List<JfrEvent> history = Collections.synchronizedList(new ArrayList<>());

    // Latest event for display
    private volatile JfrEvent lastEvent;

    public void accept(JfrEvent event) {
        totalEvents.incrementAndGet();
        lastEvent = event;
        addToHistory(event);

        String type = event.getEvent();
        eventCounts.computeIfAbsent(type, k -> new AtomicLong(0)).incrementAndGet();

        if (type.contains("GarbageCollection")) {
            gcCount.incrementAndGet();
            if (event.getDurationMs() != null) {
                lastGcPauseMs.set(event.getDurationMs().longValue());
            }
        } else if (type.contains("GCHeapSummary")) {
            Map<String, Object> fields = event.getFields();
            if (fields != null && fields.containsKey("heapUsed")) {
                Object used = fields.get("heapUsed");
                if (used instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Object val = ((Map<String, Object>) used).get("used");
                    if (val != null)
                        heapUsed.set(Long.parseLong(val.toString()));
                }
            }
            if (fields != null && fields.containsKey("heapCommitted")) {
                Object comm = fields.get("heapCommitted");
                if (comm instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Object val = ((Map<String, Object>) comm).get("committed");
                    if (val != null)
                        heapCommitted.set(Long.parseLong(val.toString()));
                }
            }
        } else if (type.contains("JavaMonitor") || type.contains("ThreadPark"))
            lockCount.incrementAndGet();
        else if (type.contains("ExceptionThrown"))
            exceptionCount.incrementAndGet();
    }

    private void addToHistory(JfrEvent event) {
        synchronized (history) {
            if (history.size() >= MAX_HISTORY) {
                history.remove(0);
            }
            history.add(event);
        }
    }

    public List<JfrEvent> getHistory() {
        synchronized (history) {
            return new ArrayList<>(history);
        }
    }

    public Map<String, Object> getSnapshot() {
        return Map.of(
                "timestamp", Instant.now().toString(),
                "metrics", Map.of(
                        "total_events", totalEvents.get(),
                        "gc_count", gcCount.get(),
                        "lock_count", lockCount.get(),
                        "exception_count", exceptionCount.get(),
                        "heap_used_mb", heapUsed.get() / (1024 * 1024),
                        "heap_committed_mb", heapCommitted.get() / (1024 * 1024),
                        "last_gc_pause_ms", lastGcPauseMs.get()),
                "last_event", lastEvent != null ? lastEvent : Map.of(),
                "history", getHistory());
    }
}
