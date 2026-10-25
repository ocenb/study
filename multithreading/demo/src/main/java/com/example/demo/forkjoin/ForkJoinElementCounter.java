package com.example.demo.forkjoin;

import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

/** Подсчёт элементов готовой агрегации в собственном ForkJoinPool. */
public final class ForkJoinElementCounter implements AutoCloseable {
    public record CountResult(long elements, int batches, long tasks) {
    }

    private final ForkJoinPool pool;
    private boolean closed;

    public ForkJoinElementCounter(int parallelism) {
        if (parallelism < 1 || parallelism > 32) {
            throw new IllegalArgumentException("parallelism: 1..32");
        }
        pool = new ForkJoinPool(parallelism);
    }

    /** Каждый объект списка — один элемент; повторяющиеся объекты считаются повторно. */
    public synchronized CountResult count(List<?> aggregatedItems, int batchSize) {
        if (closed) {
            throw new IllegalStateException("Счётчик закрыт");
        }
        if (batchSize < 1) {
            throw new IllegalArgumentException("Размер батча должен быть положительным");
        }
        // Неизменяемый снимок с произвольным доступом; null не является элементом агрегации.
        List<?> snapshot = List.copyOf(aggregatedItems);
        if (snapshot.isEmpty()) {
            return new CountResult(0, 0, 0);
        }
        int batches = (int) ((snapshot.size() + (long) batchSize - 1) / batchSize);
        return pool.invoke(new AggregatedCountTask(snapshot, batchSize, 0, batches));
    }

    public boolean isTerminated() {
        return pool.isTerminated();
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        pool.shutdown();
        try {
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                pool.shutdownNow();
                if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("ForkJoinPool не завершился");
                }
            }
        } catch (InterruptedException e) {
            pool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
