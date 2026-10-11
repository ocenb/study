package com.example.demo.atomics;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(15)
class AtomicsTest {
    @Test
    void volatileFlagStopsRunningThread() throws Exception {
        StopSignal signal = new StopSignal();
        CountDownLatch working = new CountDownLatch(1);
        Thread worker = new Thread(() -> {
            working.countDown();
            while (!signal.isStopped()) {
                Thread.onSpinWait();
            }
        });
        // Ошибка реализации не должна удерживать тестовую JVM бесконечно.
        worker.setDaemon(true);
        try {
            assertFalse(signal.isStopped());
            worker.start();
            assertTrue(working.await(2, TimeUnit.SECONDS));
            signal.stop();
            worker.join(2_000);
            assertFalse(worker.isAlive());
            assertTrue(signal.isStopped());
        } finally {
            signal.stop();
            worker.join(2_000);
        }
    }

    @Test
    void atomicCounterDoesNotLoseConcurrentIncrements() throws Exception {
        AtomicCounter counter = new AtomicCounter();
        assertEquals(0, counter.get());
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int t = 0; t < 8; t++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    for (int i = 0; i < 100_000; i++) {
                        counter.increment();
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(5, TimeUnit.SECONDS);
            }
            assertEquals(800_000, counter.get());
        } finally {
            start.countDown();
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void cacheReturnsSameObjectToAllThreads() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CyclicBarrier start = new CyclicBarrier(8);
        try {
            List<Future<String>> futures = new ArrayList<>();
            for (int t = 0; t < 8; t++) {
                futures.add(pool.submit(() -> {
                    start.await(3, TimeUnit.SECONDS);
                    SingletonCache cache = SingletonCache.getInstance();
                    String first = cache.getOrCreate();
                    for (int i = 0; i < 10_000; i++) {
                        assertSame(first, cache.getOrCreate());
                    }
                    return first;
                }));
            }
            String expected = futures.get(0).get(5, TimeUnit.SECONDS);
            assertEquals("shared-cache-value", expected);
            assertSame(SingletonCache.getInstance(), SingletonCache.getInstance());
            for (Future<String> future : futures) {
                assertSame(expected, future.get(5, TimeUnit.SECONDS));
            }
        } finally {
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private static final class BrokenCounter {
        private volatile int value;
    }

    @Test
    void volatileReadThenWriteCanLoseUpdates() throws Exception {
        BrokenCounter broken = new BrokenCounter();
        CyclicBarrier phase = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int t = 0; t < 2; t++) {
                futures.add(pool.submit(() -> {
                    for (int i = 0; i < 100; i++) {
                        int previous = broken.value;
                        // Оба потока читают одно значение до записи: допустимое чередование ++.
                        phase.await(3, TimeUnit.SECONDS);
                        broken.value = previous + 1;
                        phase.await(3, TimeUnit.SECONDS);
                    }
                    return null;
                }));
            }
            for (Future<?> future : futures) {
                future.get(5, TimeUnit.SECONDS);
            }
            assertEquals(100, broken.value, "Из 200 инкрементов сохранились только 100");
        } finally {
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void combinedLoadChecksCounterCacheAndStop() throws Exception {
        for (int threads : new int[]{1, 2, 4, 8}) {
            for (boolean atomic : new boolean[]{true, false}) {
                AtomicsDemo.Result result = AtomicsDemo.fixedWork(threads, 25_000, atomic);
                assertEquals(threads * 25_000, result.counter());
                assertEquals(result.operations(), result.counter());
                assertTrue(result.sameCacheReference());
            }
        }
        AtomicsDemo.Result stopped = AtomicsDemo.stopDemo(4, 100);
        assertTrue(stopped.operations() > 0);
        assertEquals(stopped.operations(), stopped.counter());
        assertTrue(stopped.sameCacheReference());
        assertTrue(stopped.stopNanos() > 0);
    }

    @Test
    void invalidLoadParametersAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> AtomicsDemo.fixedWork(0, 100, true));
        assertThrows(IllegalArgumentException.class, () -> AtomicsDemo.fixedWork(65, 100, true));
        assertThrows(IllegalArgumentException.class, () -> AtomicsDemo.fixedWork(2, 0, true));
        assertThrows(IllegalArgumentException.class,
                () -> AtomicsDemo.fixedWork(2, Integer.MAX_VALUE, true));
        assertThrows(IllegalArgumentException.class, () -> AtomicsDemo.stopDemo(4, 0));
    }
}
