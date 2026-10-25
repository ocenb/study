package com.example.demo.forkjoin;

import com.example.demo.completable.OrderDataService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(10)
class ForkJoinElementCounterTest {
    @Test
    void handlesEmptyListSmallListAndIncompleteLastBatch() {
        try (ForkJoinElementCounter counter = new ForkJoinElementCounter(4)) {
            assertEquals(new ForkJoinElementCounter.CountResult(0, 0, 0), counter.count(List.of(), 3));
            assertEquals(new ForkJoinElementCounter.CountResult(1, 1, 1), counter.count(List.of("A"), 3));
            assertEquals(new ForkJoinElementCounter.CountResult(9, 3, 5),
                    counter.count(IntStream.range(0, 9).boxed().toList(), 3));
            assertEquals(new ForkJoinElementCounter.CountResult(10, 4, 7),
                    counter.count(IntStream.range(0, 10).boxed().toList(), 3));
            assertEquals(new ForkJoinElementCounter.CountResult(3, 1, 1),
                    counter.count(List.of("A", "B", "C"), Integer.MAX_VALUE));
        }
    }

    @Test
    void matchesSequentialTraversalForDifferentBatchSizesAndParallelism() {
        List<Integer> items = IntStream.range(0, 10_003).boxed().toList();
        long expected = items.stream().mapToLong(item -> 1L).sum();
        for (int parallelism : new int[]{1, 2, 4}) {
            try (ForkJoinElementCounter counter = new ForkJoinElementCounter(parallelism)) {
                for (int batchSize : new int[]{1, 17, 1_000, 20_000}) {
                    ForkJoinElementCounter.CountResult result = counter.count(items, batchSize);
                    assertEquals(expected, result.elements());
                    assertEquals((items.size() + batchSize - 1) / batchSize, result.batches());
                    assertEquals(2L * result.batches() - 1, result.tasks());
                }
            }
        }
    }

    @Test
    void countsRepeatedElementsWithoutDeduplication() {
        try (ForkJoinElementCounter counter = new ForkJoinElementCounter(2)) {
            assertEquals(100, counter.count(Collections.nCopies(100, "same"), 7).elements());
        }
    }

    @Test
    void countsOnlySuccessfulAggregatedOrdersFromPreviousHomework() {
        OrderDataService.DataSource source = new OrderDataService.DataSource() {
            public OrderDataService.User user(String id) { return new OrderDataService.User("User " + id); }
            public OrderDataService.Product product(String id) {
                return new OrderDataService.Product("Product " + id, BigDecimal.TEN);
            }
            public BigDecimal rubPerUsd(String id) throws Exception {
                if (id.equals("B")) { throw new IOException("rate unavailable"); }
                return BigDecimal.ONE;
            }
        };
        ConcurrentHashMap<String, OrderDataService.FinalResult> saved = new ConcurrentHashMap<>();
        try (OrderDataService service = new OrderDataService(3, source,
                order -> saved.put(order.requestId(), order), Duration.ZERO, message -> {});
             ForkJoinElementCounter counter = new ForkJoinElementCounter(2)) {
            List<OrderDataService.FinalResult> aggregated = service.fetchAllDataAsync(List.of("A", "B", "C"));
            assertEquals(List.of("A", "C"), aggregated.stream().map(OrderDataService.FinalResult::requestId).toList());
            assertEquals(2, counter.count(aggregated, 1).elements());
            assertEquals(saved.size(), counter.count(aggregated, 1).elements());
        }
    }

    @Test
    void validatesInputAndClosesOwnedPool() {
        assertThrows(IllegalArgumentException.class, () -> new ForkJoinElementCounter(0));
        ForkJoinElementCounter counter = new ForkJoinElementCounter(2);
        try (counter) {
            assertThrows(IllegalArgumentException.class, () -> counter.count(List.of("A"), 0));
            assertThrows(NullPointerException.class, () -> counter.count(null, 1));
            assertThrows(NullPointerException.class, () -> counter.count(Arrays.asList("A", null), 1));
            assertEquals(1, counter.count(List.of("A"), 1).elements());
        }
        assertTrue(counter.isTerminated());
        counter.close();
        assertThrows(IllegalStateException.class, () -> counter.count(List.of(), 1));
    }
}
