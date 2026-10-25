package com.example.demo.events;

import java.time.Instant;
import java.util.Objects;

/** Неизменяемое сообщение, безопасное для передачи между потоками. */
public record NotificationEvent(String id, String topic, String payload, Instant createdAt) {
    public NotificationEvent {
        Objects.requireNonNull(id);
        Objects.requireNonNull(topic);
        Objects.requireNonNull(payload);
        Objects.requireNonNull(createdAt);
        if (id.isBlank() || topic.isBlank()) {
            throw new IllegalArgumentException("ID и тема не должны быть пустыми");
        }
    }
}
