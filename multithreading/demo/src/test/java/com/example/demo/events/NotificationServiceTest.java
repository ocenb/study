package com.example.demo.events;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(15)
class NotificationServiceTest {
    private static NotificationEvent event(String id) {
        return new NotificationEvent(id, "orders", "payload", Instant.now());
    }

    private static void await(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(5);
        }
        assertTrue(condition.getAsBoolean());
    }

    @Test
    void concurrentProducersDoNotLoseEventsOrDeliverDuplicates() throws Exception {
        ConcurrentHashMap<String, AtomicInteger> deliveries = new ConcurrentHashMap<>();
        NotificationService service = new NotificationService(4, 2_048);
        try (service) {
            service.subscribe("orders", e -> deliveries.computeIfAbsent("one:" + e.id(), key -> new AtomicInteger()).incrementAndGet());
            service.subscribe("orders", e -> deliveries.computeIfAbsent("two:" + e.id(), key -> new AtomicInteger()).incrementAndGet());
            ExecutorService producers = Executors.newFixedThreadPool(8);
            CountDownLatch start = new CountDownLatch(1);
            try {
                List<Future<?>> futures = new ArrayList<>();
                for (int p = 0; p < 8; p++) {
                    int base = p * 125;
                    futures.add(producers.submit(() -> {
                        start.await();
                        for (int i = 0; i < 125; i++) {
                            NotificationEvent event = event("event-" + (base + i));
                            assertEquals(NotificationService.PublishResult.ACCEPTED, service.publish(event));
                            assertEquals(NotificationService.PublishResult.DUPLICATE, service.publish(event));
                        }
                        return null;
                    }));
                }
                start.countDown();
                for (Future<?> future : futures) {
                    future.get(5, TimeUnit.SECONDS);
                }
            } finally {
                start.countDown();
                producers.shutdownNow();
                assertTrue(producers.awaitTermination(5, TimeUnit.SECONDS));
            }
        }
        assertEquals(1_000, service.stats().accepted());
        assertEquals(1_000, service.stats().processed());
        assertEquals(1_000, service.stats().duplicates());
        assertEquals(2_000, service.stats().delivered());
        assertEquals(2_000, deliveries.size());
        assertTrue(deliveries.values().stream().allMatch(count -> count.get() == 1));
        assertEquals(0, service.stats().failed());
        assertEquals(0, service.stats().queued());
        assertTrue(service.stats().terminated());
    }

    @Test
    void subscriberFailureIsIsolatedAndNextEventStillRuns() {
        AtomicInteger healthyDeliveries = new AtomicInteger();
        NotificationService service = new NotificationService(1, 10);
        try (service) {
            service.subscribe("orders", e -> {
                if (e.id().equals("A")) { throw new IOException("channel unavailable"); }
            });
            service.subscribe("orders", e -> healthyDeliveries.incrementAndGet());
            service.publish(event("A"));
            service.publish(event("B"));
        }
        assertEquals(2, healthyDeliveries.get());
        assertEquals(3, service.stats().delivered());
        assertEquals(1, service.stats().failed());
        assertEquals(NotificationService.State.COMPLETED_WITH_ERRORS, service.status("A").orElseThrow().state());
        assertEquals(NotificationService.State.COMPLETED, service.status("B").orElseThrow().state());
        assertEquals(1, service.failures().size());
        assertEquals("A", service.failures().get(0).eventId());
        assertTrue(service.failures().get(0).error().contains("IOException"));
        assertThrows(UnsupportedOperationException.class, () -> service.failures().clear());
    }

    @Test
    void fullQueueRejectsWithoutReservingIdAndRetryWorks() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        NotificationService service = new NotificationService(1, 1);
        try (service) {
            service.subscribe("orders", e -> {
                if (e.id().equals("A")) {
                    entered.countDown();
                    if (!release.await(3, TimeUnit.SECONDS)) { throw new IllegalStateException("release timeout"); }
                }
            });
            try {
                assertEquals(NotificationService.PublishResult.ACCEPTED, service.publish(event("A")));
                assertTrue(entered.await(2, TimeUnit.SECONDS));
                assertEquals(NotificationService.PublishResult.ACCEPTED, service.publish(event("B")));
                assertEquals(NotificationService.PublishResult.QUEUE_FULL, service.publish(event("C")));
                assertTrue(service.status("C").isEmpty());
                assertEquals(NotificationService.PublishResult.DUPLICATE, service.publish(event("A")));
            } finally {
                release.countDown();
            }
            await(() -> service.stats().processed() == 2);
            assertEquals(NotificationService.PublishResult.ACCEPTED, service.publish(event("C")));
        }
        assertEquals(3, service.stats().processed());
        assertEquals(1, service.stats().queueRejections());
        assertEquals(1, service.stats().duplicates());
    }

    @Test
    void unsubscribeDuringDispatchUsesSnapshotAndDuplicateSubscriptionIsIgnored() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger secondCalls = new AtomicInteger();
        NotificationService.Subscriber second = e -> secondCalls.incrementAndGet();
        NotificationService service = new NotificationService(1, 10);
        try (service) {
            service.subscribe("orders", e -> {
                if (e.id().equals("A")) {
                    entered.countDown();
                    if (!release.await(3, TimeUnit.SECONDS)) { throw new IllegalStateException("release timeout"); }
                }
            });
            service.subscribe("orders", second);
            service.subscribe("orders", second);
            assertEquals(2, service.stats().subscriptions());
            try {
                service.publish(event("A"));
                assertTrue(entered.await(2, TimeUnit.SECONDS));
                assertTrue(service.unsubscribe("orders", second));
                assertFalse(service.unsubscribe("orders", second));
                service.publish(event("B"));
            } finally {
                release.countDown();
            }
        }
        assertEquals(1, secondCalls.get(), "Отписка не отменяет текущий обход снимка, но действует на следующее событие");
        assertEquals(2, service.stats().processed());
    }

    @Test
    void closeDrainsAcceptedEventsAndRejectsNewPublications() {
        AtomicInteger calls = new AtomicInteger();
        NotificationService service = new NotificationService(2, 30);
        try (service) {
            service.subscribe("orders", e -> {
                Thread.sleep(2);
                calls.incrementAndGet();
            });
            for (int i = 0; i < 20; i++) {
                assertEquals(NotificationService.PublishResult.ACCEPTED, service.publish(event("id-" + i)));
            }
        }
        assertEquals(20, calls.get());
        assertEquals(20, service.stats().processed());
        assertTrue(service.stats().terminated());
        assertThrows(IllegalStateException.class, () -> service.publish(event("new")));
        assertThrows(IllegalStateException.class, () -> service.subscribe("orders", e -> {}));
        service.close();
    }

    @Test
    void noSubscribersAndCloseFromCallbackAreHandled() {
        NotificationService service = new NotificationService(1, 10);
        try (service) {
            service.publish(new NotificationEvent("none", "unknown", "payload", Instant.now()));
            service.subscribe("orders", e -> service.close());
            service.publish(event("close-in-callback"));
        }
        assertEquals(NotificationService.State.COMPLETED, service.status("none").orElseThrow().state());
        assertEquals(0, service.status("none").orElseThrow().delivered());
        assertEquals(NotificationService.State.COMPLETED_WITH_ERRORS,
                service.status("close-in-callback").orElseThrow().state());
        assertEquals(2, service.stats().processed());
        assertEquals(1, service.stats().failed());
    }

    @Test
    void validatesConfigurationAndEventFields() {
        assertThrows(IllegalArgumentException.class, () -> new NotificationService(0, 10));
        assertThrows(IllegalArgumentException.class, () -> new NotificationService(1, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new NotificationEvent(" ", "orders", "payload", Instant.now()));
        assertThrows(NullPointerException.class,
                () -> new NotificationEvent("A", "orders", null, Instant.now()));
    }
}
