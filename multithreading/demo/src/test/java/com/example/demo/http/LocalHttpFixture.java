package com.example.demo.http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Локальные воспроизводимые ответы без зависимости от публичного интернета. */
final class LocalHttpFixture implements AutoCloseable {
    private final HttpServer server;
    private final ExecutorService workers = Executors.newFixedThreadPool(8);
    private final AtomicInteger active = new AtomicInteger();
    private final AtomicInteger maximumActive = new AtomicInteger();
    private final CountDownLatch parallel = new CountDownLatch(4);
    final CountDownLatch slowStarted = new CountDownLatch(1);

    LocalHttpFixture() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(workers);
        server.createContext("/", this::handle);
        server.start();
    }

    String url(String path) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + path;
    }

    int maximumActive() {
        return maximumActive.get();
    }

    private void handle(HttpExchange exchange) throws IOException {
        maximumActive.accumulateAndGet(active.incrementAndGet(), Math::max);
        try (exchange) {
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/drop")) {
                return; // Соединение закрывается до отправки HTTP-заголовков.
            }
            if (path.equals("/parallel")) {
                parallel.countDown();
                if (!parallel.await(3, TimeUnit.SECONDS)) {
                    respond(exchange, 503);
                    return;
                }
            }
            if (path.equals("/slow-headers")) {
                slowStarted.countDown();
                Thread.sleep(700);
            }
            if (path.equals("/slow-body")) {
                exchange.sendResponseHeaders(200, 1);
                exchange.getResponseBody().flush();
                slowStarted.countDown();
                Thread.sleep(700);
                exchange.getResponseBody().write('x');
                return;
            }
            int code = path.startsWith("/status/")
                    ? Integer.parseInt(path.substring("/status/".length())) : 200;
            respond(exchange, code);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            active.decrementAndGet();
        }
    }

    private static void respond(HttpExchange exchange, int status) throws IOException {
        byte[] body = ("status=" + status).getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
    }

    @Override
    public void close() {
        server.stop(0);
        workers.shutdownNow();
        try {
            if (!workers.awaitTermination(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Локальный HTTP-сервер не завершился");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
