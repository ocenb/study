package com.example.demo.taskqueue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(15)
class UrlTaskServerTest {
    @Test
    void severalProducersAndConsumersProcessEveryTaskOnceWithSmallQueue() throws Exception {
        List<String> urls = IntStream.range(0, 200).mapToObj(i -> "url-" + i).toList();
        ConcurrentHashMap<String, AtomicInteger> calls = new ConcurrentHashMap<>();
        UrlTaskServer server = new UrlTaskServer(3, 4, 1, url -> {
            calls.computeIfAbsent(url, key -> new AtomicInteger()).incrementAndGet();
            Thread.sleep(1);
            return new UrlTaskServer.PageData(200, url);
        });
        UrlTaskServer.BatchResult batch = server.run(urls);
        assertEquals(urls, batch.results().stream().map(UrlTaskServer.Result::url).toList());
        assertEquals(200, calls.size());
        assertTrue(calls.values().stream().allMatch(count -> count.get() == 1));
        assertTrue(batch.results().stream().allMatch(r -> r.successful()
                && r.producer().startsWith("url-producer-") && r.consumer().startsWith("url-consumer-")));
        assertEquals(3, batch.results().stream().map(UrlTaskServer.Result::producer).distinct().count());
        assertTrue(batch.producersTerminated());
        assertTrue(batch.consumersTerminated());
    }

    @Test
    void failedTaskDoesNotStopOtherUrlsAndRepeatedUrlsRemainSeparate() throws Exception {
        UrlTaskServer server = new UrlTaskServer(2, 2, 1, url -> {
            if (url.equals("bad")) { throw new IOException("network unavailable"); }
            return new UrlTaskServer.PageData(200, "Title");
        });
        UrlTaskServer.BatchResult batch = server.run(List.of("good", "bad", "good"));
        assertEquals(List.of(0, 1, 2), batch.results().stream().map(UrlTaskServer.Result::index).toList());
        assertTrue(batch.results().get(0).successful());
        assertTrue(batch.results().get(2).successful());
        assertNull(batch.results().get(1).statusCode());
        assertTrue(batch.results().get(1).error().contains("IOException"));
    }

    @Test
    void downloadsActualPagesAndExtractsDecodedTitle() throws Exception {
        try (LocalPageServer pages = new LocalPageServer()) {
            UrlTaskServer server = new UrlTaskServer(2, 3, 1, new HttpPageLoader(Duration.ofSeconds(2)));
            UrlTaskServer.BatchResult batch = server.run(List.of(pages.url("/page/1"), pages.url("/missing"),
                    pages.url("/notfound")));
            assertEquals("Страница /page/1 & очередь", batch.results().get(0).title());
            assertEquals(200, batch.results().get(0).statusCode());
            assertTrue(batch.results().get(1).successful());
            assertEquals("", batch.results().get(1).title());
            assertEquals(404, batch.results().get(2).statusCode());
            assertEquals("HTTP 404", batch.results().get(2).error());
        }
    }

    @Test
    void timeoutInvalidAddressAndLargeResponseAreReturnedAsErrors() throws Exception {
        try (LocalPageServer pages = new LocalPageServer()) {
            UrlTaskServer server = new UrlTaskServer(2, 3, 1, new HttpPageLoader(Duration.ofMillis(200)));
            UrlTaskServer.BatchResult batch = server.run(List.of(pages.url("/slow"), "not a URL",
                    "file:///tmp/a", pages.url("/large")));
            assertTrue(batch.results().stream().noneMatch(UrlTaskServer.Result::successful));
            assertTrue(batch.results().get(0).error().contains("SocketTimeoutException"));
            assertTrue(batch.results().get(1).error().contains("IllegalArgumentException"));
            assertTrue(batch.results().get(2).error().contains("IllegalArgumentException"));
            assertTrue(batch.results().get(3).error().contains("1 MiB"));
        }
    }

    @Test
    void htmlParserHandlesWhitespaceEntitiesAndMissingTitle() throws Exception {
        assertEquals("First & Second", HttpPageLoader.extractTitle("<HTML><HEAD><TITLE> First &amp;   Second </TITLE></HEAD></HTML>"));
        assertEquals("A B", HttpPageLoader.extractTitle("<title>A&nbsp;B</title>"));
        assertEquals("", HttpPageLoader.extractTitle("<html><body>No title</body></html>"));
    }

    @Test
    void interruptionStopsBlockedProducersAndConsumers() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch consumerInterrupted = new CountDownLatch(1);
        UrlTaskServer server = new UrlTaskServer(1, 1, 1, url -> {
            entered.countDown();
            try {
                new CountDownLatch(1).await();
                return new UrlTaskServer.PageData(200, "unreachable");
            } catch (InterruptedException e) {
                consumerInterrupted.countDown();
                throw e;
            }
        });
        AtomicReference<Throwable> outcome = new AtomicReference<>();
        Thread coordinator = new Thread(() -> {
            try {
                server.run(List.of("A", "B", "C", "D"));
                outcome.set(new AssertionError("Expected interruption"));
            } catch (Throwable e) {
                outcome.set(e);
            }
        });
        try {
            coordinator.start();
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            coordinator.interrupt();
            coordinator.join(3_000);
            assertFalse(coordinator.isAlive());
            assertInstanceOf(InterruptedException.class, outcome.get());
            assertTrue(consumerInterrupted.await(1, TimeUnit.SECONDS));
        } finally {
            coordinator.interrupt();
            coordinator.join(3_000);
        }
    }

    @Test
    void consumerAbortDoesNotLeaveProducerBlockedOnFullQueue() {
        UrlTaskServer server = new UrlTaskServer(1, 1, 1, url -> {
            throw new InterruptedException("consumer abort");
        });
        assertThrows(ExecutionException.class, () -> server.run(List.of("A", "B", "C", "D")));
    }

    @Test
    void emptyInputConfigurationAndUrlFileAreSupported(@TempDir Path temporary) throws Exception {
        UrlTaskServer server = new UrlTaskServer(3, 4, 1, url -> new UrlTaskServer.PageData(200, "title"));
        UrlTaskServer.BatchResult empty = server.run(List.of());
        assertTrue(empty.results().isEmpty());
        assertTrue(empty.producersTerminated());
        assertTrue(empty.consumersTerminated());
        assertThrows(IllegalArgumentException.class, () -> new UrlTaskServer(0, 1, 1, url -> null));
        assertThrows(IllegalArgumentException.class, () -> new UrlTaskServer(1, 1, 0, url -> null));
        assertThrows(IllegalArgumentException.class, () -> new HttpPageLoader(Duration.ZERO));
        Path urls = temporary.resolve("urls.txt");
        Files.writeString(urls, "# comment\n\n https://example.test/a \nhttps://example.test/a\n");
        assertEquals(List.of("https://example.test/a", "https://example.test/a"), UrlTaskApplication.readUrls(urls));
    }
}
