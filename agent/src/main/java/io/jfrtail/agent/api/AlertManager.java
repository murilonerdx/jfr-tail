package io.jfrtail.agent.api;

import io.jfrtail.common.JfrEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class AlertManager {
    private final List<Consumer<String>> listeners = new ArrayList<>();

    // Thresholds (hardcoded for now, can be made configurable later)
    private double gcPauseThresholdMs = 500.0;
    private int exceptionThresholdPerEvent = 1;

    public void addListener(Consumer<String> listener) {
        listeners.add(listener);
    }

    public void check(JfrEvent event) {
        String type = event.getEvent();

        if (type.contains("GarbageCollection") && event.getDurationMs() != null) {
            if (event.getDurationMs() > gcPauseThresholdMs) {
                notifyListeners("ALERT: GC Pause too high! " + event.getDurationMs() + "ms");
            }
        }

        if (type.contains("ExceptionThrown")) {
            notifyListeners("ALERT: Exception detected: " + event.getFields().get("className"));
        }

        if (type.contains("JavaMonitor") || type.contains("ThreadPark")) {
            if (event.getDurationMs() != null && event.getDurationMs() > 1000) {
                notifyListeners("ALERT: High lock contention / park: " + event.getDurationMs() + "ms on thread "
                        + event.getThread());
            }
        }
    }

    private void notifyListeners(String message) {
        System.out.println("[JfrTail] " + message);
        for (Consumer<String> listener : listeners) {
            try {
                listener.accept(message);
            } catch (Exception e) {
                // ignore
            }
        }
    }

    public void setGcPauseThresholdMs(double threshold) {
        this.gcPauseThresholdMs = threshold;
    }
}
