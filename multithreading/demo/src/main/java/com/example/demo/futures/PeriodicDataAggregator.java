package com.example.demo.futures;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Периодический параллельный сбор данных без наложения циклов. */
public final class PeriodicDataAggregator implements AutoCloseable {
    public record EntityData(String id, BigDecimal value, Instant updatedAt) {
        public EntityData {
            Objects.requireNonNull(id);
            Objects.requireNonNull(value);
            Objects.requireNonNull(updatedAt);
        }
    }

    public interface DataSource {
        List<String> identifiers() throws Exception;
        EntityData fetch(String id) throws Exception;
    }

    public record Stats(int startedCycles, int completedCycles, int skippedTicks,
                        int successes, int failures, boolean collecting,
                        boolean closed, boolean terminated) {
    }

    private final Object lifecycle = new Object();
    private final long intervalNanos;
    private final DataSource source;
    private final Consumer<EntityData> sink;
    private final BiConsumer<String, Exception> onError;
    private final ScheduledExecutorService scheduler;
    private final ExecutorService workers;
    private final List<Future<?>> inFlight = new ArrayList<>();
    private final AtomicBoolean collecting = new AtomicBoolean();
    private final AtomicInteger started = new AtomicInteger();
    private final AtomicInteger completed = new AtomicInteger();
    private final AtomicInteger skipped = new AtomicInteger();
    private final AtomicInteger successes = new AtomicInteger();
    private final AtomicInteger failures = new AtomicInteger();
    private ScheduledFuture<?> periodic;
    private volatile boolean closed;

    public PeriodicDataAggregator(Duration interval, int parallelism, DataSource source,
                                  Consumer<EntityData> sink, BiConsumer<String, Exception> onError) {
        intervalNanos = ExecutorExamples.positiveNanos(interval);
        if (parallelism < 1 || parallelism > 32) {
            throw new IllegalArgumentException("parallelism: 1..32");
        }
        this.source = Objects.requireNonNull(source);
        this.sink = Objects.requireNonNull(sink);
        this.onError = Objects.requireNonNull(onError);
        scheduler = Executors.newSingleThreadScheduledExecutor(task -> new Thread(task, "aggregator-scheduler"));
        AtomicInteger sequence = new AtomicInteger();
        workers = Executors.newFixedThreadPool(parallelism,
                task -> new Thread(task, "aggregator-fetch-" + sequence.incrementAndGet()));
    }

    public void start() {
        synchronized (lifecycle) {
            if (closed || periodic != null) {
                throw new IllegalStateException("Агрегатор закрыт или уже запущен");
            }
            periodic = scheduler.scheduleAtFixedRate(this::tick, 0, intervalNanos, TimeUnit.NANOSECONDS);
        }
    }

    private void tick() {
        synchronized (lifecycle) {
            if (closed) {
                return;
            }
            if (!collecting.compareAndSet(false, true)) {
                skipped.incrementAndGet();
                return;
            }
            started.incrementAndGet();
        }
        List<String> ids;
        try {
            // Получаем актуальный список заново на каждом цикле.
            ids = List.copyOf(source.identifiers());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            finishCycle();
            return;
        } catch (Exception e) {
            reportError("<identifiers>", e);
            finishCycle();
            return;
        }
        if (ids.isEmpty()) {
            finishCycle();
            return;
        }
        AtomicInteger remaining = new AtomicInteger(ids.size());
        synchronized (lifecycle) {
            if (closed) {
                collecting.set(false);
                return;
            }
            inFlight.clear();
            for (String id : ids) {
                inFlight.add(workers.submit(() -> {
                    try {
                        if (!closed) {
                            EntityData data = Objects.requireNonNull(source.fetch(id));
                            if (!closed) {
                                sink.accept(data); // Callback вызывается параллельно: нужен потокобезопасный sink.
                                successes.incrementAndGet();
                            }
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } catch (Exception e) {
                        reportError(id, e);
                    } finally {
                        if (remaining.decrementAndGet() == 0) {
                            finishCycle();
                        }
                    }
                }));
            }
        }
    }

    private void finishCycle() {
        if (!closed) {
            completed.incrementAndGet();
        }
        collecting.set(false);
    }

    private void reportError(String id, Exception error) {
        if (!closed) {
            failures.incrementAndGet();
            try {
                onError.accept(id, error);
            } catch (RuntimeException callbackFailure) {
                // Исключение обработчика ошибок не должно остановить периодические запуски.
                System.err.println("Ошибка onError: " + callbackFailure);
            }
        }
    }

    public Stats stats() {
        return new Stats(started.get(), completed.get(), skipped.get(), successes.get(), failures.get(),
                collecting.get(), closed, scheduler.isTerminated() && workers.isTerminated());
    }

    @Override
    public void close() {
        synchronized (lifecycle) {
            if (closed) {
                return;
            }
            closed = true;
            if (periodic != null) {
                periodic.cancel(true);
            }
            scheduler.shutdownNow();
            inFlight.forEach(future -> future.cancel(true));
            workers.shutdownNow();
            collecting.set(false);
        }
        try {
            boolean schedulerStopped = scheduler.awaitTermination(5, TimeUnit.SECONDS);
            boolean workersStopped = workers.awaitTermination(5, TimeUnit.SECONDS);
            if (!schedulerStopped || !workersStopped) {
                throw new IllegalStateException("Агрегатор не остановился: источник должен учитывать interrupt");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
