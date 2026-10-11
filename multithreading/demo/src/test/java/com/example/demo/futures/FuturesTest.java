package com.example.demo.futures;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(15)
class FuturesTest {
    private static PeriodicDataAggregator.EntityData data(String id) {
        return new PeriodicDataAggregator.EntityData(id, BigDecimal.ONE, Instant.now());
    }

    private static void await(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(5);
        }
        assertTrue(condition.getAsBoolean(), "Условие не выполнено за 5 секунд");
    }

    @Test
    void cancellationInterruptsRunningTaskAndGetThrowsCancellation() throws Exception {
        ExecutorExamples.CancellationResult result = ExecutorExamples.cancelLongTask(
                Duration.ofSeconds(5), Duration.ofMillis(30));
        assertTrue(result.cancelAccepted());
        assertTrue(result.cancelled());
        assertTrue(result.done());
        assertTrue(result.interrupted());
        assertTrue(result.workerStopped());
        assertTrue(result.getThrewCancellation());
    }

    @Test
    void completedTaskCannotBeCancelled() throws Exception {
        ExecutorExamples.CancellationResult result = ExecutorExamples.cancelLongTask(
                Duration.ofMillis(1), Duration.ofMillis(100));
        assertFalse(result.cancelAccepted());
        assertFalse(result.cancelled());
        assertTrue(result.done());
        assertFalse(result.interrupted());
        assertTrue(result.workerStopped());
        assertFalse(result.getThrewCancellation());
    }

    @Test
    void invokeAllReturnsFiveResultsInInputOrder() throws Exception {
        List<ExecutorExamples.TaskResult> results = ExecutorExamples.invokeRandomTasks();
        assertEquals(5, results.size());
        for (int i = 0; i < results.size(); i++) {
            ExecutorExamples.TaskResult result = results.get(i);
            assertEquals(i + 1, result.id());
            assertEquals((i + 1) * 10, result.value());
            assertTrue(result.plannedMillis() >= 1_000 && result.plannedMillis() <= 3_000);
            assertTrue(result.elapsedMillis() > 0);
        }
    }

    @Test
    void periodicMonitoringRunsSeveralTimesAndStops() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        assertEquals(3, ExecutorExamples.monitor(3, Duration.ofMillis(10), tick -> calls.incrementAndGet()));
        assertEquals(3, calls.get());
    }

    @Test
    void aggregatorFetchesConcurrentlyAndRecoversFromEntityFailure() throws Exception {
        CountDownLatch allFetching = new CountDownLatch(3);
        AtomicInteger idsCalls = new AtomicInteger();
        AtomicInteger failOnce = new AtomicInteger();
        AtomicInteger errors = new AtomicInteger();
        ConcurrentHashMap<String, PeriodicDataAggregator.EntityData> saved = new ConcurrentHashMap<>();
        PeriodicDataAggregator.DataSource source = new PeriodicDataAggregator.DataSource() {
            public List<String> identifiers() {
                idsCalls.incrementAndGet();
                return List.of("A", "B", "C");
            }

            public PeriodicDataAggregator.EntityData fetch(String id) throws Exception {
                allFetching.countDown();
                if (!allFetching.await(2, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Запросы не выполнялись параллельно");
                }
                if (id.equals("B") && failOnce.getAndIncrement() == 0) {
                    throw new IOException("temporary failure");
                }
                return data(id);
            }
        };
        PeriodicDataAggregator aggregator = new PeriodicDataAggregator(Duration.ofMillis(30), 3, source,
                value -> saved.put(value.id(), value), (id, error) -> errors.incrementAndGet());
        try (aggregator) {
            aggregator.start();
            await(() -> aggregator.stats().completedCycles() >= 2);
            assertEquals(3, saved.size());
            assertEquals(1, errors.get());
            assertEquals(1, aggregator.stats().failures());
            assertTrue(aggregator.stats().successes() >= 5);
            assertTrue(idsCalls.get() >= 2);
        }
        assertTrue(aggregator.stats().terminated());
        assertTrue(aggregator.stats().closed());
    }

    @Test
    void unfinishedCycleDoesNotOverlapNextTicksAndCloseInterruptsFetch() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        AtomicInteger lists = new AtomicInteger();
        PeriodicDataAggregator.DataSource source = new PeriodicDataAggregator.DataSource() {
            public List<String> identifiers() {
                lists.incrementAndGet();
                return List.of("A");
            }

            public PeriodicDataAggregator.EntityData fetch(String id) throws Exception {
                entered.countDown();
                try {
                    new CountDownLatch(1).await();
                    return data(id);
                } catch (InterruptedException e) {
                    interrupted.countDown();
                    throw e;
                }
            }
        };
        PeriodicDataAggregator aggregator = new PeriodicDataAggregator(Duration.ofMillis(10), 1, source,
                value -> fail("Отменённый запрос не должен сохраниться"), (id, error) -> {});
        try (aggregator) {
            aggregator.start();
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            await(() -> aggregator.stats().skippedTicks() >= 2);
            assertEquals(1, lists.get());
            assertEquals(1, aggregator.stats().startedCycles());
        }
        assertTrue(interrupted.await(1, TimeUnit.SECONDS));
        assertTrue(aggregator.stats().terminated());
        assertEquals(0, aggregator.stats().successes());
        assertFalse(aggregator.stats().collecting());
    }

    @Test
    void listAndSinkExceptionsDoNotStopSubsequentCycles() throws Exception {
        AtomicInteger lists = new AtomicInteger();
        AtomicInteger sinkCalls = new AtomicInteger();
        AtomicInteger errors = new AtomicInteger();
        PeriodicDataAggregator.DataSource source = new PeriodicDataAggregator.DataSource() {
            public List<String> identifiers() throws Exception {
                if (lists.getAndIncrement() == 0) {
                    throw new IOException("identifiers unavailable");
                }
                return List.of("A");
            }

            public PeriodicDataAggregator.EntityData fetch(String id) {
                return data(id);
            }
        };
        try (PeriodicDataAggregator aggregator = new PeriodicDataAggregator(Duration.ofMillis(15), 1, source,
                value -> {
                    if (sinkCalls.getAndIncrement() == 0) {
                        throw new IllegalStateException("storage unavailable");
                    }
                }, (id, error) -> errors.incrementAndGet())) {
            aggregator.start();
            await(() -> aggregator.stats().successes() >= 1);
            assertEquals(2, errors.get());
            assertEquals(2, aggregator.stats().failures());
        }
    }

    @Test
    void emptyListsAndLifecycleAreSupported() throws Exception {
        PeriodicDataAggregator.DataSource emptySource = new PeriodicDataAggregator.DataSource() {
            public List<String> identifiers() { return List.of(); }
            public PeriodicDataAggregator.EntityData fetch(String id) { throw new AssertionError(); }
        };
        PeriodicDataAggregator aggregator = new PeriodicDataAggregator(Duration.ofMillis(10), 1, emptySource,
                value -> {}, (id, error) -> {});
        try (aggregator) {
            aggregator.start();
            assertThrows(IllegalStateException.class, aggregator::start);
            await(() -> aggregator.stats().completedCycles() >= 1);
            assertEquals(0, aggregator.stats().successes());
        }
        aggregator.close();
        assertThrows(IllegalStateException.class, aggregator::start);
        assertTrue(aggregator.stats().terminated());
        assertThrows(IllegalArgumentException.class, () -> new PeriodicDataAggregator(
                Duration.ZERO, 1, emptySource, value -> {}, (id, error) -> {}));
    }
}
