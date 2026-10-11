package com.example.demo.futures;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;

public final class ExecutorExamples {
    private ExecutorExamples() {
    }

    public record CancellationResult(boolean cancelAccepted, boolean cancelled, boolean done,
                                     boolean interrupted, boolean workerStopped,
                                     boolean getThrewCancellation, double elapsedMillis) {
    }

    public record TaskResult(int id, int plannedMillis, int value, double elapsedMillis) {
    }

    static long positiveNanos(Duration duration) {
        long nanos = duration.toNanos();
        if (nanos <= 0) {
            throw new IllegalArgumentException("Продолжительность должна быть положительной");
        }
        return nanos;
    }

    /** Отдельно проверяет отмену Future и фактическое завершение исполняющего задачу потока. */
    public static CancellationResult cancelLongTask(Duration processing, Duration cancelAfter)
            throws InterruptedException, java.util.concurrent.ExecutionException {
        long processingNanos = positiveNanos(processing);
        long delayNanos = positiveNanos(cancelAfter);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch stopped = new CountDownLatch(1);
        AtomicBoolean interrupted = new AtomicBoolean();
        long began = System.nanoTime();
        try {
            Future<String> future = executor.submit(() -> {
                started.countDown();
                try {
                    TimeUnit.NANOSECONDS.sleep(processingNanos);
                    return "Обработка завершена";
                } catch (InterruptedException e) {
                    interrupted.set(true);
                    Thread.currentThread().interrupt();
                    throw e;
                } finally {
                    stopped.countDown();
                }
            });
            if (!started.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Задача не стартовала");
            }
            TimeUnit.NANOSECONDS.sleep(delayNanos);
            boolean accepted = future.cancel(true);
            boolean cancellationException = false;
            try {
                future.get();
            } catch (CancellationException e) {
                cancellationException = true;
            }
            boolean workerStopped = stopped.await(5, TimeUnit.SECONDS);
            return new CancellationResult(accepted, future.isCancelled(), future.isDone(),
                    interrupted.get(), workerStopped, cancellationException,
                    (System.nanoTime() - began) / 1_000_000.0);
        } finally {
            shutdown(executor);
        }
    }

    /** Пять независимых Callable: каждый занимает случайные 1000..3000 мс. */
    public static List<TaskResult> invokeRandomTasks() throws Exception {
        List<Callable<TaskResult>> tasks = new ArrayList<>();
        for (int id = 1; id <= 5; id++) {
            int taskId = id;
            tasks.add(() -> {
                int delay = ThreadLocalRandom.current().nextInt(1_000, 3_001);
                long began = System.nanoTime();
                Thread.sleep(delay);
                return new TaskResult(taskId, delay, taskId * 10,
                        (System.nanoTime() - began) / 1_000_000.0);
            });
        }
        ExecutorService executor = Executors.newFixedThreadPool(5);
        try {
            List<Future<TaskResult>> futures = executor.invokeAll(tasks);
            List<TaskResult> results = new ArrayList<>();
            for (Future<TaskResult> future : futures) {
                results.add(future.get());
            }
            return List.copyOf(results);
        } finally {
            shutdown(executor);
        }
    }

    public static int monitor(int count, Duration interval, IntConsumer operation)
            throws InterruptedException {
        if (count < 1) {
            throw new IllegalArgumentException("Нужна хотя бы одна проверка");
        }
        long nanos = positiveNanos(interval);
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        CountDownLatch completed = new CountDownLatch(count);
        AtomicInteger checks = new AtomicInteger();
        ScheduledFuture<?> periodic = null;
        try {
            periodic = scheduler.scheduleAtFixedRate(() -> {
                if (checks.get() < count) {
                    operation.accept(checks.incrementAndGet());
                    completed.countDown();
                }
            }, 0, nanos, TimeUnit.NANOSECONDS);
            // Небольшой запас для планирования; таймаут исключает бесконечное ожидание при ошибке callback.
            if (!completed.await(Math.addExact(Math.multiplyExact(nanos, count),
                    TimeUnit.SECONDS.toNanos(5)), TimeUnit.NANOSECONDS)) {
                throw new IllegalStateException("Периодический мониторинг не завершился");
            }
            return checks.get();
        } finally {
            if (periodic != null) {
                periodic.cancel(true);
            }
            shutdown(scheduler);
        }
    }

    static void shutdown(ExecutorService executor) throws InterruptedException {
        executor.shutdownNow();
        if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Executor не завершился");
        }
    }
}
