package com.example.demo.buffer.service;

import com.example.demo.buffer.model.BoundedBuffer;
import com.example.demo.buffer.model.SynchronizedBoundedBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Сервис бенчмаркинга и демонстрации работы BoundedBuffer.
 */
@Service
public class BufferBenchmarkService {

    private static final Logger log = LoggerFactory.getLogger(BufferBenchmarkService.class);

    /**
     * Запуск сравнительного бенчмарка между BoundedBuffer (ReentrantLock+Condition)
     * и SynchronizedBoundedBuffer (synchronized+wait/notifyAll).
     */
    public Map<String, Object> runBenchmark(int capacity, int producers, int consumers, int itemsPerProducer)
            throws InterruptedException {

        int totalItems = producers * itemsPerProducer;
        log.info("Запуск бенчмарка буфера: емкость={}, producers={}, consumers={}, totalItems={}",
                capacity, producers, consumers, totalItems);

        // 1. Бенчмарк ReentrantLock + Condition
        long lockStart = System.nanoTime();
        BenchmarkRun lockRun = executeLockBufferRun(capacity, producers, consumers, itemsPerProducer);
        long lockDurationMs = (System.nanoTime() - lockStart) / 1_000_000;

        // 2. Бенчмарк Synchronized + wait/notifyAll
        long syncStart = System.nanoTime();
        BenchmarkRun syncRun = executeSyncBufferRun(capacity, producers, consumers, itemsPerProducer);
        long syncDurationMs = (System.nanoTime() - syncStart) / 1_000_000;

        double speedup = syncDurationMs > 0 ? (double) syncDurationMs / Math.max(1, lockDurationMs) : 1.0;

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("bufferCapacity", capacity);
        report.put("producersCount", producers);
        report.put("consumersCount", consumers);
        report.put("totalItemsTransferred", totalItems);

        Map<String, Object> lockMetrics = new LinkedHashMap<>();
        lockMetrics.put("durationMs", lockDurationMs);
        lockMetrics.put("throughputOpsPerSec", (long) ((totalItems * 1000.0) / Math.max(1, lockDurationMs)));
        lockMetrics.put("checksumMatches", lockRun.producedChecksum == lockRun.consumedChecksum);
        report.put("reentrantLockCondition", lockMetrics);

        Map<String, Object> syncMetrics = new LinkedHashMap<>();
        syncMetrics.put("durationMs", syncDurationMs);
        syncMetrics.put("throughputOpsPerSec", (long) ((totalItems * 1000.0) / Math.max(1, syncDurationMs)));
        syncMetrics.put("checksumMatches", syncRun.producedChecksum == syncRun.consumedChecksum);
        report.put("synchronizedWaitNotify", syncMetrics);

        report.put("speedupFactor", String.format("%.2fx", speedup));
        report.put("winner", lockDurationMs <= syncDurationMs ? "ReentrantLock + Condition" : "synchronized");

        return report;
    }

    /**
     * Пошаговая демонстрация взаимодействия producer'ов и consumer'ов для логов.
     */
    public List<String> runDemo(int capacity, int itemsCount) throws InterruptedException {
        BoundedBuffer<String> buffer = new BoundedBuffer<>(capacity);
        List<String> logList = new CopyOnWriteArrayList<>();

        logList.add(String.format("Создан BoundedBuffer емкостью %d", capacity));

        Thread producer = new Thread(() -> {
            try {
                for (int i = 1; i <= itemsCount; i++) {
                    String item = "Item-" + i;
                    buffer.put(item);
                    logList.add(String.format("[PRODUCER] Добавил: %s (размер буфера: %d)", item, buffer.size()));
                    Thread.sleep(30);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "DemoProducer");

        Thread consumer = new Thread(() -> {
            try {
                for (int i = 1; i <= itemsCount; i++) {
                    Thread.sleep(60); // Consumer медленнее, буфер будет наполняться
                    String item = buffer.take();
                    logList.add(String.format("[CONSUMER] Извлек: %s (остаток в буфере: %d)", item, buffer.size()));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "DemoConsumer");

        consumer.start();
        producer.start();

        producer.join();
        consumer.join();

        logList.add("Демонстрация взаимодействия producer и consumer успешно завершена!");
        return logList;
    }

    private BenchmarkRun executeLockBufferRun(int capacity, int producers, int consumers, int itemsPerProducer)
            throws InterruptedException {
        BoundedBuffer<Integer> buffer = new BoundedBuffer<>(capacity);
        int totalItems = producers * itemsPerProducer;
        int itemsPerConsumer = totalItems / consumers;

        ExecutorService executor = Executors.newFixedThreadPool(producers + consumers);
        CountDownLatch finishLatch = new CountDownLatch(producers + consumers);

        AtomicLong producedSum = new AtomicLong(0);
        AtomicLong consumedSum = new AtomicLong(0);

        // Запуск производителей
        for (int p = 0; p < producers; p++) {
            final int base = p * itemsPerProducer;
            executor.submit(() -> {
                try {
                    for (int i = 1; i <= itemsPerProducer; i++) {
                        int val = base + i;
                        buffer.put(val);
                        producedSum.addAndGet(val);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        // Запуск потребителей
        for (int c = 0; c < consumers; c++) {
            executor.submit(() -> {
                try {
                    for (int i = 0; i < itemsPerConsumer; i++) {
                        Integer val = buffer.take();
                        consumedSum.addAndGet(val);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        finishLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        return new BenchmarkRun(producedSum.get(), consumedSum.get());
    }

    private BenchmarkRun executeSyncBufferRun(int capacity, int producers, int consumers, int itemsPerProducer)
            throws InterruptedException {
        SynchronizedBoundedBuffer<Integer> buffer = new SynchronizedBoundedBuffer<>(capacity);
        int totalItems = producers * itemsPerProducer;
        int itemsPerConsumer = totalItems / consumers;

        ExecutorService executor = Executors.newFixedThreadPool(producers + consumers);
        CountDownLatch finishLatch = new CountDownLatch(producers + consumers);

        AtomicLong producedSum = new AtomicLong(0);
        AtomicLong consumedSum = new AtomicLong(0);

        for (int p = 0; p < producers; p++) {
            final int base = p * itemsPerProducer;
            executor.submit(() -> {
                try {
                    for (int i = 1; i <= itemsPerProducer; i++) {
                        int val = base + i;
                        buffer.put(val);
                        producedSum.addAndGet(val);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        for (int c = 0; c < consumers; c++) {
            executor.submit(() -> {
                try {
                    for (int i = 0; i < itemsPerConsumer; i++) {
                        Integer val = buffer.take();
                        consumedSum.addAndGet(val);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        finishLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        return new BenchmarkRun(producedSum.get(), consumedSum.get());
    }

    private static class BenchmarkRun {
        final long producedChecksum;
        final long consumedChecksum;

        BenchmarkRun(long producedChecksum, long consumedChecksum) {
            this.producedChecksum = producedChecksum;
            this.consumedChecksum = consumedChecksum;
        }
    }
}
