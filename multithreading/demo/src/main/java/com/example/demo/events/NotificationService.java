package com.example.demo.events;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

/** Producer-consumer + publish-subscribe; доставка внутри одного процесса без повторных попыток. */
public final class NotificationService implements AutoCloseable {
    @FunctionalInterface
    public interface Subscriber {
        void onEvent(NotificationEvent event) throws Exception;
    }

    public enum PublishResult { ACCEPTED, DUPLICATE, QUEUE_FULL }
    public enum State { QUEUED, PROCESSING, COMPLETED, COMPLETED_WITH_ERRORS }

    public record EventStatus(String topic, State state, int delivered, int failed) {
    }

    public record DeliveryFailure(String eventId, String topic, String error, Instant occurredAt) {
    }

    public record Stats(long accepted, long processed, long duplicates, long queueRejections,
                        long delivered, long failed, int queued, int subscriptions, boolean terminated) {
    }

    private final Object lifecycle = new Object();
    private final ArrayBlockingQueue<NotificationEvent> queue;
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<Subscriber>> subscribers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, EventStatus> statuses = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<DeliveryFailure> failures = new ConcurrentLinkedQueue<>();
    private final LongAdder accepted = new LongAdder();
    private final LongAdder processed = new LongAdder();
    private final LongAdder duplicates = new LongAdder();
    private final LongAdder queueRejections = new LongAdder();
    private final LongAdder delivered = new LongAdder();
    private final LongAdder failed = new LongAdder();
    private final ExecutorService workers;
    private final ThreadLocal<Boolean> dispatchThread = ThreadLocal.withInitial(() -> false);
    private volatile boolean accepting = true;

    public NotificationService(int parallelism, int queueCapacity) {
        if (parallelism < 1 || parallelism > 32 || queueCapacity < 1) {
            throw new IllegalArgumentException("parallelism: 1..32; queueCapacity > 0");
        }
        queue = new ArrayBlockingQueue<>(queueCapacity);
        AtomicInteger sequence = new AtomicInteger();
        workers = Executors.newFixedThreadPool(parallelism,
                task -> new Thread(task, "notification-dispatch-" + sequence.incrementAndGet()));
        for (int i = 0; i < parallelism; i++) {
            workers.execute(this::dispatchLoop);
        }
    }

    public void subscribe(String topic, Subscriber subscriber) {
        Objects.requireNonNull(topic);
        Objects.requireNonNull(subscriber);
        if (topic.isBlank()) {
            throw new IllegalArgumentException("Тема не должна быть пустой");
        }
        synchronized (lifecycle) {
            checkOpen();
            subscribers.computeIfAbsent(topic, key -> new CopyOnWriteArrayList<>()).addIfAbsent(subscriber);
        }
    }

    /** Уже выполняющийся обход снимка подписчиков может завершиться после отписки. */
    public boolean unsubscribe(String topic, Subscriber subscriber) {
        synchronized (lifecycle) {
            CopyOnWriteArrayList<Subscriber> listeners = subscribers.get(topic);
            if (listeners == null || !listeners.remove(subscriber)) {
                return false;
            }
            if (listeners.isEmpty()) {
                subscribers.remove(topic, listeners);
            }
            return true;
        }
    }

    public PublishResult publish(NotificationEvent event) {
        Objects.requireNonNull(event);
        synchronized (lifecycle) {
            checkOpen();
            EventStatus initial = new EventStatus(event.topic(), State.QUEUED, 0, 0);
            if (statuses.putIfAbsent(event.id(), initial) != null) {
                duplicates.increment();
                return PublishResult.DUPLICATE;
            }
            if (!queue.offer(event)) {
                statuses.remove(event.id(), initial); // Отклонённый ID можно отправить повторно.
                queueRejections.increment();
                return PublishResult.QUEUE_FULL;
            }
            accepted.increment();
            return PublishResult.ACCEPTED;
        }
    }

    private void checkOpen() {
        if (!accepting) {
            throw new IllegalStateException("Сервис уведомлений закрыт");
        }
    }

    private void dispatchLoop() {
        dispatchThread.set(true);
        try {
            while (accepting || !queue.isEmpty()) {
                NotificationEvent event = queue.poll(100, TimeUnit.MILLISECONDS);
                if (event != null) {
                    dispatch(event);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            dispatchThread.remove();
        }
    }

    private void dispatch(NotificationEvent event) {
        statuses.put(event.id(), new EventStatus(event.topic(), State.PROCESSING, 0, 0));
        List<Subscriber> targets = subscribers.get(event.topic());
        if (targets == null) {
            targets = List.of();
        }
        int successes = 0;
        int errors = 0;
        // CopyOnWriteArrayList даёт снимок итератора без блокировки subscribe/unsubscribe.
        for (Subscriber subscriber : targets) {
            try {
                subscriber.onEvent(event);
                successes++;
                delivered.increment();
            } catch (Exception e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                errors++;
                failed.increment();
                failures.add(new DeliveryFailure(event.id(), event.topic(),
                        e.getClass().getSimpleName() + ": " + e.getMessage(), Instant.now()));
            }
        }
        statuses.put(event.id(), new EventStatus(event.topic(),
                errors == 0 ? State.COMPLETED : State.COMPLETED_WITH_ERRORS, successes, errors));
        processed.increment();
    }

    public Optional<EventStatus> status(String eventId) {
        return Optional.ofNullable(statuses.get(eventId));
    }

    public List<DeliveryFailure> failures() {
        return List.copyOf(failures);
    }

    /** Во время работы это наблюдаемый снимок; точные итоговые значения — после close(). */
    public Stats stats() {
        return new Stats(accepted.sum(), processed.sum(), duplicates.sum(), queueRejections.sum(),
                delivered.sum(), failed.sum(), queue.size(),
                subscribers.values().stream().mapToInt(List::size).sum(), workers.isTerminated());
    }

    @Override
    public void close() {
        if (dispatchThread.get()) {
            throw new IllegalStateException("close() должен вызывать владелец сервиса, а не подписчик");
        }
        synchronized (lifecycle) {
            accepting = false;
            workers.shutdown();
        }
        try {
            if (!workers.awaitTermination(5, TimeUnit.SECONDS)) {
                workers.shutdownNow();
                workers.awaitTermination(5, TimeUnit.SECONDS);
                throw new IllegalStateException("Превышен срок завершения доставки уведомлений");
            }
            if (processed.sum() != accepted.sum()) {
                throw new IllegalStateException("Завершены не все принятые события");
            }
        } catch (InterruptedException e) {
            workers.shutdownNow();
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Ожидание доставки прервано", e);
        }
    }
}
