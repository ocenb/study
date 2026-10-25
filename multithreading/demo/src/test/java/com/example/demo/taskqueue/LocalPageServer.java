package com.example.demo.taskqueue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Локальный HTTP-источник HTML для тестов и воспроизводимого отчёта. */
final class LocalPageServer implements AutoCloseable {
    private final HttpServer server;
    private final ExecutorService executor = Executors.newFixedThreadPool(8);

    LocalPageServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(executor);
        server.createContext("/", this::handle);
        server.start();
    }

    String url(String path) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + path;
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            String path = exchange.getRequestURI().getPath();
            int status = path.equals("/notfound") ? 404 : 200;
            if (path.equals("/slow")) {
                Thread.sleep(800);
            } else {
                Thread.sleep(20);
            }
            byte[] body;
            if (path.equals("/large")) {
                body = new byte[1_048_577];
            } else {
                String html = path.equals("/missing") ? "<html><body>Без title</body></html>"
                        : "<html><head><title> Страница " + path + " &amp; очередь </title></head><body>OK</body></html>";
                body = html.getBytes(StandardCharsets.UTF_8);
            }
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Локальный сервер страниц не завершился");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
