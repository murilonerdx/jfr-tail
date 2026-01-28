package io.jfrtail.common;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.Map;

public class JfrEvent {
    private Instant ts;
    private long pid;
    private String event;
    private String thread;

    @JsonProperty("duration_ms")
    private Double durationMs;

    private Map<String, Object> fields;

    // Default constructor for Jackson
    public JfrEvent() {
    }

    public JfrEvent(Instant ts, long pid, String event, String thread, Double durationMs, Map<String, Object> fields) {
        this.ts = ts;
        this.pid = pid;
        this.event = event;
        this.thread = thread;
        this.durationMs = durationMs;
        this.fields = fields;
    }

    public Instant getTs() {
        return ts;
    }

    public void setTs(Instant ts) {
        this.ts = ts;
    }

    public long getPid() {
        return pid;
    }

    public void setPid(long pid) {
        this.pid = pid;
    }

    public String getEvent() {
        return event;
    }

    public void setEvent(String event) {
        this.event = event;
    }

    public String getThread() {
        return thread;
    }

    public void setThread(String thread) {
        this.thread = thread;
    }

    public Double getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Double durationMs) {
        this.durationMs = durationMs;
    }

    public Map<String, Object> getFields() {
        return fields;
    }

    public void setFields(Map<String, Object> fields) {
        this.fields = fields;
    }
}
