package com.example.demo.http;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(15)
class AsyncHttpServiceTest {
    @TempDir
    Path temporary;

    private AsyncHttpService service(Duration requestTimeout) {
        return new AsyncHttpService(new AsyncHttpService.PoolSettings(2, 4, 16, 10),
                Duration.ofSeconds(1), requestTimeout);
    }

    @Test
    void fourRequestsReachServerBeforeAnyResponseIsSent() throws Exception {
        try (LocalHttpFixture server = new LocalHttpFixture();
             AsyncHttpService service = service(Duration.ofSeconds(5))) {
            List<String> urls = List.of(server.url("/parallel?1"), server.url("/parallel?2"),
                    server.url("/parallel?3"), server.url("/parallel?4"));
            AsyncHttpService.BatchResult batch = service.fetchAll(urls);
            assertEquals(urls, batch.results().stream().map(AsyncHttpService.RequestResult::url).toList());
            assertEquals(4, batch.summary().successful());
            assertTrue(server.maximumActive() >= 4, "Запросы действительно выполняются параллельно");
            assertTrue(service.poolSnapshot().largestPoolSize() >= 2);
            assertTrue(service.poolSnapshot().completedTasks() > 0);
        }
    }

    @Test
    void statusCodesAndSummaryPreserveDuplicatesAndInputOrder() throws Exception {
        try (LocalHttpFixture server = new LocalHttpFixture();
             AsyncHttpService service = service(Duration.ofSeconds(3))) {
            String repeated = server.url("/status/200");
            AsyncHttpService.BatchResult batch = service.fetchAll(List.of(repeated,
                    server.url("/status/404"), server.url("/status/503"), repeated));
            assertEquals(List.of(200, 404, 503, 200),
                    batch.results().stream().map(AsyncHttpService.RequestResult::statusCode).toList());
            assertEquals(4, batch.summary().total());
            assertEquals(2, batch.summary().successful());
            assertEquals(2, batch.summary().httpErrors());
            assertEquals(0, batch.summary().requestErrors());
            assertTrue(batch.results().stream().allMatch(r -> r.elapsedNanos() > 0 && r.error().isEmpty()));
            assertTrue(batch.summary().minMillis() <= batch.summary().averageMillis());
            assertTrue(batch.summary().averageMillis() <= batch.summary().maxMillis());
            assertTrue(batch.summary().wallMillis() > 0);
            assertThrows(UnsupportedOperationException.class, () -> batch.results().clear());
        }
    }

    @Test
    void invalidUrlAndNetworkFailureDoNotDiscardOtherResults() throws Exception {
        try (LocalHttpFixture server = new LocalHttpFixture();
             AsyncHttpService service = service(Duration.ofSeconds(2))) {
            AsyncHttpService.BatchResult batch = service.fetchAll(List.of("not a URL", "file:///tmp/a",
                    server.url("/drop"), server.url("/status/201")));
            assertEquals(4, batch.summary().total());
            assertEquals(3, batch.summary().requestErrors());
            assertEquals(1, batch.summary().successful());
            for (int i = 0; i < 3; i++) {
                assertNull(batch.results().get(i).statusCode());
                assertFalse(batch.results().get(i).error().isBlank());
            }
            assertEquals(201, batch.results().get(3).statusCode());
        }
    }

    @Test
    void deadlineCoversBothWaitingForHeadersAndReadingResponseBody() throws Exception {
        try (LocalHttpFixture server = new LocalHttpFixture();
             AsyncHttpService service = service(Duration.ofMillis(200))) {
            AsyncHttpService.BatchResult batch = service.fetchAll(List.of(server.url("/slow-headers"),
                    server.url("/slow-body")));
            assertEquals(2, batch.summary().requestErrors());
            for (AsyncHttpService.RequestResult result : batch.results()) {
                assertNull(result.statusCode());
                assertTrue(result.error().contains("Timeout"), result.error());
            }
        }
    }

    @Test
    void interruptCancelsBatchAndAllowsPoolToClose() throws Exception {
        try (LocalHttpFixture server = new LocalHttpFixture()) {
            AsyncHttpService service = service(Duration.ofSeconds(5));
            AtomicReference<Throwable> outcome = new AtomicReference<>();
            Thread coordinator = new Thread(() -> {
                try {
                    service.fetchAll(List.of(server.url("/slow-body")));
                    outcome.set(new AssertionError("Ожидалось прерывание"));
                } catch (Throwable e) {
                    outcome.set(e);
                }
            });
            try {
                coordinator.start();
                assertTrue(server.slowStarted.await(3, TimeUnit.SECONDS));
                coordinator.interrupt();
                coordinator.join(3_000);
                assertFalse(coordinator.isAlive());
                assertInstanceOf(InterruptedException.class, outcome.get());
            } finally {
                coordinator.interrupt();
                coordinator.join(3_000);
                service.close();
            }
            assertTrue(service.poolSnapshot().terminated());
        }
    }

    @Test
    void poolConfigurationLifecycleAndEmptyBatchAreExplicit() throws Exception {
        AsyncHttpService service = service(Duration.ofSeconds(1));
        try (service) {
            AsyncHttpService.PoolSnapshot pool = service.poolSnapshot();
            assertEquals(2, pool.coreThreads());
            assertEquals(4, pool.maxThreads());
            assertEquals(16, pool.queueCapacity());
            AsyncHttpService.Summary empty = service.fetchAll(List.of()).summary();
            assertEquals(0, empty.total());
            assertEquals(0, empty.averageMillis());
            assertEquals(0, empty.minMillis());
            assertEquals(0, empty.maxMillis());
        }
        service.close();
        assertTrue(service.poolSnapshot().terminated());
        assertThrows(IllegalStateException.class, () -> service.fetchAll(List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new AsyncHttpService.PoolSettings(4, 2, 1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> service(Duration.ZERO));
    }

    @Test
    void fileInputAndPrintedReportAreUsable() throws Exception {
        Path file = temporary.resolve("urls.txt");
        Files.writeString(file, "# comment\n\n http://example.test/a \nhttp://example.test/a\n");
        assertEquals(List.of("http://example.test/a", "http://example.test/a"),
                AsyncHttpApplication.readUrls(file));
        var batch = new AsyncHttpService.BatchResult(List.of(
                new AsyncHttpService.RequestResult("http://example.test/a", 200, 1_000_000, ""),
                new AsyncHttpService.RequestResult("bad", null, 2_000_000, "IllegalArgumentException")
        ), 3_000_000);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (PrintStream output = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            AsyncHttpApplication.print(batch, output);
        }
        String printed = bytes.toString(StandardCharsets.UTF_8);
        assertTrue(printed.contains("200"));
        assertTrue(printed.contains("IllegalArgumentException"));
        assertTrue(printed.contains("Всего: 2; 2xx: 1; HTTP не-2xx: 0; ошибки запросов: 1"));
        assertTrue(printed.contains("Общее время партии: 3.000 мс"));
    }
}
