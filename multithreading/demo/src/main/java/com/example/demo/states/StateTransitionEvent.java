package com.example.demo.states;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Запись о наблюдении состояния потока.
 */
public class StateTransitionEvent {
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private final String timestamp;
    private final String threadName;
    private final String state;
    private final String description;

    public StateTransitionEvent(String threadName, String state, String description) {
        this.timestamp = LocalTime.now().format(TIME_FORMATTER);
        this.threadName = threadName;
        this.state = state;
        this.description = description;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public String getThreadName() {
        return threadName;
    }

    public String getState() {
        return state;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return String.format("[%s] %-20s -> %-14s | %s", timestamp, threadName, state, description);
    }
}
