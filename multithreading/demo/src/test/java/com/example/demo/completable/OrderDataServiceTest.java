package com.example.demo.completable;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.nio.file.Files;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(10)
class OrderDataServiceTest {
    @Test
    void readsIndependentExternalFilesAndReportsMissingId(@TempDir Path directory) throws Exception {
        Files.writeString(directory.resolve("users.csv"), "# id;name\nA; Alice \n");
        Files.writeString(directory.resolve("products.csv"), "A; Book ;10.25\n");
        Files.writeString(directory.resolve("rates.csv"), "A;92.45\n");
        FileOrderDataSource source = new FileOrderDataSource(directory, null);
        assertEquals(" Alice ", source.user("A").name());
        assertEquals(new BigDecimal("10.25"), source.product("A").priceUsd());
        assertEquals(new BigDecimal("92.45"), source.rubPerUsd("A"));
        assertThrows(IOException.class, () -> source.user("missing"));
    }

    private static class Source implements OrderDataService.DataSource {
        public OrderDataService.User user(String id) throws Exception {
            return new OrderDataService.User(" User " + id + " ");
        }
        public OrderDataService.Product product(String id) throws Exception {
            return new OrderDataService.Product("  Product   " + id + " ", new BigDecimal("10.25"));
        }
        public BigDecimal rubPerUsd(String id) throws Exception {
            return new BigDecimal("92.45");
        }
    }

    @Test
    void independentSourcesOverlapAndUseNamedDaemonThreads() {
        CountDownLatch entered = new CountDownLatch(3);
        ConcurrentLinkedQueue<Thread> usedThreads = new ConcurrentLinkedQueue<>();
        Source source = new Source() {
            private void meet() throws Exception {
                usedThreads.add(Thread.currentThread());
                entered.countDown();
                if (!entered.await(2, TimeUnit.SECONDS)) {
                    throw new IOException("Sources did not overlap");
                }
            }
            public OrderDataService.User user(String id) throws Exception { meet(); return super.user(id); }
            public OrderDataService.Product product(String id) throws Exception { meet(); return super.product(id); }
            public BigDecimal rubPerUsd(String id) throws Exception { meet(); return super.rubPerUsd(id); }
        };
        ConcurrentLinkedQueue<String> trace = new ConcurrentLinkedQueue<>();
        try (OrderDataService service = new OrderDataService(3, source,
                result -> usedThreads.add(Thread.currentThread()), Duration.ofMillis(1), trace::add)) {
            List<OrderDataService.FinalResult> results = service.fetchAllDataAsync(List.of("A"));
            assertEquals(1, results.size());
            assertEquals(new BigDecimal("947.61"), results.get(0).priceRub());
            assertEquals("User A", results.get(0).userName());
            assertEquals("Product A", results.get(0).productName());
            assertTrue(usedThreads.stream().allMatch(t -> t.isDaemon() && t.getName().startsWith("order-data-")));
            assertTrue(trace.stream().anyMatch(s -> s.contains("stage=thenApply:normalize")));
            assertTrue(trace.stream().anyMatch(s -> s.contains("stage=thenCombine:product+rate")));
            assertTrue(trace.stream().anyMatch(s -> s.contains("stage=thenAccept:saved")));
            assertTrue(trace.stream().filter(s -> s.contains("stage=price:") || s.contains("stage=save:"))
                    .allMatch(s -> s.contains("thread=order-data-")));
            assertTrue(trace.stream().allMatch(s -> s.contains("request=A thread=")));
        }
    }

    @Test
    void sourceFailureReturnsPartialResultsAndDoesNotSaveFailedOrder() {
        Source source = new Source() {
            public BigDecimal rubPerUsd(String id) throws Exception {
                if (id.equals("B")) { throw new IOException("rate unavailable"); }
                return super.rubPerUsd(id);
            }
        };
        ConcurrentHashMap<String, OrderDataService.FinalResult> saved = new ConcurrentHashMap<>();
        ConcurrentLinkedQueue<String> trace = new ConcurrentLinkedQueue<>();
        try (OrderDataService service = new OrderDataService(4, source,
                result -> saved.put(result.requestId(), result), Duration.ZERO, trace::add)) {
            List<OrderDataService.FinalResult> results = service.fetchAllDataAsync(List.of("A", "B", "C"));
            assertEquals(List.of("A", "C"), results.stream().map(OrderDataService.FinalResult::requestId).toList());
            assertEquals(2, saved.size());
            assertFalse(saved.containsKey("B"));
            assertTrue(trace.stream().anyMatch(s -> s.contains("request=B ") && s.contains("ERROR:IOException")));
        }
    }

    @Test
    void savingFailureDoesNotDiscardOtherOrders() {
        ConcurrentLinkedQueue<String> saved = new ConcurrentLinkedQueue<>();
        try (OrderDataService service = new OrderDataService(3, new Source(), result -> {
            if (result.requestId().equals("B")) { throw new IOException("store unavailable"); }
            saved.add(result.requestId());
        }, Duration.ZERO, message -> {})) {
            List<OrderDataService.FinalResult> results = service.fetchAllDataAsync(List.of("A", "B", "C"));
            assertEquals(List.of("A", "C"), results.stream().map(OrderDataService.FinalResult::requestId).toList());
            assertEquals(2, saved.size());
        }
    }

    @Test
    void synchronousAndAsynchronousVersionsReturnEqualObjectsAndPreserveDuplicates() {
        Source source = new Source();
        try (OrderDataService service = new OrderDataService(2, source, result -> {}, Duration.ZERO, message -> {})) {
            List<String> ids = List.of("C", "A", "C");
            List<OrderDataService.FinalResult> async = service.fetchAllDataAsync(ids);
            assertEquals(service.fetchAllDataSync(ids), async);
            assertEquals(ids, async.stream().map(OrderDataService.FinalResult::requestId).toList());
        }
    }

    @Test
    void emptyInputAllFailedAndExecutorShutdownAreHandled() {
        Source source = new Source() {
            public OrderDataService.User user(String id) throws Exception { throw new IOException("no user"); }
        };
        OrderDataService service = new OrderDataService(2, source, result -> fail("Must not save"),
                Duration.ZERO, message -> {});
        try (service) {
            assertTrue(service.fetchAllDataAsync(List.of()).isEmpty());
            assertTrue(service.fetchAllDataAsync(List.of("A", "B")).isEmpty());
        }
        service.close();
        assertTrue(service.isTerminated());
        assertThrows(IllegalStateException.class, () -> service.fetchAllDataAsync(List.of("A")));
        assertThrows(IllegalArgumentException.class,
                () -> new OrderDataService(0, source, result -> {}, Duration.ZERO, message -> {}));
    }
}
