package com.example.demo.http;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Асинхронный HTTP-транспорт с явно созданным и управляемым пулом. */
public final class AsyncHttpService implements AutoCloseable {
    public record PoolSettings(int coreThreads, int maxThreads, int queueCapacity,
                               long keepAliveSeconds) {
        public PoolSettings {
            if (coreThreads < 1 || maxThreads < coreThreads || queueCapacity < 1
                    || keepAliveSeconds < 1) {
                throw new IllegalArgumentException("Некорректная конфигурация ThreadPoolExecutor");
            }
        }
    }

    public record PoolSnapshot(int coreThreads, int maxThreads, int queueCapacity,
                               int largestPoolSize, long completedTasks, boolean terminated) {
    }

    public record RequestResult(String url, Integer statusCode, long elapsedNanos, String error) {
        public double millis() {
            return elapsedNanos / 1_000_000.0;
        }
    }

    public record Summary(int total, int successful, int httpErrors, int requestErrors,
                          double averageMillis, double minMillis, double maxMillis,
                          double wallMillis) {
    }

    public record BatchResult(List<RequestResult> results, long elapsedNanos) {
        public BatchResult {
            results = List.copyOf(results);
        }

        public Summary summary() {
            int successful = 0;
            int httpErrors = 0;
            int requestErrors = 0;
            double totalMillis = 0;
            double min = Double.POSITIVE_INFINITY;
            double max = 0;
            for (RequestResult result : results) {
                if (result.statusCode() == null) {
                    requestErrors++;
                } else if (result.statusCode() >= 200 && result.statusCode() < 300) {
                    successful++;
                } else {
                    httpErrors++;
                }
                totalMillis += result.millis();
                min = Math.min(min, result.millis());
                max = Math.max(max, result.millis());
            }
            return new Summary(results.size(), successful, httpErrors, requestErrors,
                    results.isEmpty() ? 0 : totalMillis / results.size(),
                    results.isEmpty() ? 0 : min, max, elapsedNanos / 1_000_000.0);
        }
    }

    private record Pending(CompletableFuture<?> transport, CompletableFuture<RequestResult> result) {
    }

    private final PoolSettings settings;
    private final Duration requestTimeout;
    private final ThreadPoolExecutor executor;
    private final HttpClient client;
    private boolean closed;

    public AsyncHttpService() {
        this(new PoolSettings(4, 8, 64, 30), Duration.ofSeconds(3), Duration.ofSeconds(5));
    }

    public AsyncHttpService(PoolSettings settings, Duration connectTimeout, Duration requestTimeout) {
        this.settings = Objects.requireNonNull(settings);
        requirePositive(connectTimeout);
        requirePositive(requestTimeout);
        // Проверяем представимость до создания пула и запросов.
        requestTimeout.toNanos();
        this.requestTimeout = requestTimeout;
        AtomicInteger sequence = new AtomicInteger();
        executor = new ThreadPoolExecutor(settings.coreThreads(), settings.maxThreads(),
                settings.keepAliveSeconds(), TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(settings.queueCapacity()), task ->
                        new Thread(task, "async-http-worker-" + sequence.incrementAndGet()),
                new ThreadPoolExecutor.CallerRunsPolicy());
        client = HttpClient.newBuilder()
                .executor(executor)
                .connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    private static void requirePositive(Duration duration) {
        Objects.requireNonNull(duration);
        if (duration.isNegative() || duration.isZero()) {
            throw new IllegalArgumentException("Таймаут должен быть положительным");
        }
    }

    /** Запускает все запросы до ожидания результатов; сохраняет порядок и повторы URL. */
    public synchronized BatchResult fetchAll(List<String> urls) throws InterruptedException {
        if (closed) {
            throw new IllegalStateException("HTTP-сервис закрыт");
        }
        List<String> input = List.copyOf(urls);
        long started = System.nanoTime();
        List<Pending> pending = new ArrayList<>();
        try {
            for (String url : input) {
                pending.add(submit(url));
            }
            CompletableFuture.allOf(pending.stream().map(Pending::result)
                    .toArray(CompletableFuture<?>[]::new)).get();
            List<RequestResult> results = pending.stream().map(p -> p.result().join()).toList();
            return new BatchResult(results, System.nanoTime() - started);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Ошибка сбора результатов", e.getCause());
        } finally {
            // При прерывании координатора не оставляем незавершённые запросы.
            for (Pending request : pending) {
                if (!request.transport().isDone()) {
                    request.transport().cancel(true);
                }
            }
        }
    }

    private Pending submit(String url) {
        long began = System.nanoTime();
        try {
            URI uri = URI.create(url);
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null || uri.getUserInfo() != null) {
                throw new IllegalArgumentException("Нужен абсолютный http/https URL без credentials");
            }
            HttpRequest request = HttpRequest.newBuilder(uri).GET().timeout(requestTimeout).build();
            CompletableFuture<HttpResponse<Void>> transport =
                    client.sendAsync(request, HttpResponse.BodyHandlers.discarding());
            // Отдельная копия ограничивает время ВСЕГО ответа, включая чтение тела.
            // При таймауте отменяем исходный HTTP-запрос, а не только ожидание результата.
            CompletableFuture<RequestResult> result = transport.copy()
                    .orTimeout(requestTimeout.toNanos(), TimeUnit.NANOSECONDS)
                    .handle((response, failure) -> {
                        if (failure != null) {
                            transport.cancel(true);
                            return failed(url, began, failure);
                        }
                        return new RequestResult(url, response.statusCode(),
                                System.nanoTime() - began, "");
                    });
            return new Pending(transport, result);
        } catch (IllegalArgumentException | java.util.concurrent.RejectedExecutionException e) {
            return new Pending(CompletableFuture.completedFuture(null),
                    CompletableFuture.completedFuture(failed(url, began, e)));
        }
    }

    private static RequestResult failed(String url, long began, Throwable failure) {
        while (failure instanceof CompletionException || failure instanceof ExecutionException) {
            failure = failure.getCause();
        }
        String detail = failure.getMessage();
        String error = failure.getClass().getSimpleName()
                + (detail == null ? "" : ": " + detail.replaceAll("\\s+", " "));
        return new RequestResult(url, null, System.nanoTime() - began, error);
    }

    public PoolSnapshot poolSnapshot() {
        return new PoolSnapshot(settings.coreThreads(), settings.maxThreads(), settings.queueCapacity(),
                executor.getLargestPoolSize(), executor.getCompletedTaskCount(), executor.isTerminated());
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("HTTP-пул не завершился");
                }
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
