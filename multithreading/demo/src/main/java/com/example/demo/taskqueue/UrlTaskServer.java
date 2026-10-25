package com.example.demo.taskqueue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/** Ограниченная очередь URL: Producer.put -> Consumer.take -> загрузка страницы. */
public final class UrlTaskServer {
    public record PageData(int statusCode, String title) {
        public PageData { Objects.requireNonNull(title); }
    }

    @FunctionalInterface
    public interface PageLoader {
        PageData load(String url) throws Exception;
    }

    public record Result(int index, String url, Integer statusCode, String title, String error,
                         String producer, String consumer, long elapsedNanos) {
        public boolean successful() {
            return error.isEmpty();
        }
    }

    public record BatchResult(List<Result> results, long elapsedNanos,
                              boolean producersTerminated, boolean consumersTerminated) {
        public BatchResult { results = List.copyOf(results); }
    }

    private record Task(int index, String url, String producer) {
    }

    private static final Task STOP = new Task(-1, "", "");
    private final int producerCount;
    private final int consumerCount;
    private final int queueCapacity;
    private final PageLoader loader;

    public UrlTaskServer(int producerCount, int consumerCount, int queueCapacity, PageLoader loader) {
        if (producerCount < 1 || producerCount > 16 || consumerCount < 1 || consumerCount > 32
                || queueCapacity < 1) {
            throw new IllegalArgumentException("producers: 1..16; consumers: 1..32; queueCapacity > 0");
        }
        this.producerCount = producerCount;
        this.consumerCount = consumerCount;
        this.queueCapacity = queueCapacity;
        this.loader = Objects.requireNonNull(loader);
    }

    public BatchResult run(List<String> urls) throws InterruptedException, ExecutionException {
        List<String> input = List.copyOf(urls);
        BlockingQueue<Task> queue = new ArrayBlockingQueue<>(queueCapacity);
        ConcurrentLinkedQueue<Result> results = new ConcurrentLinkedQueue<>();
        AtomicInteger producerNames = new AtomicInteger();
        AtomicInteger consumerNames = new AtomicInteger();
        ExecutorService producers = Executors.newFixedThreadPool(producerCount,
                task -> new Thread(task, "url-producer-" + producerNames.incrementAndGet()));
        ExecutorService consumers = Executors.newFixedThreadPool(consumerCount,
                task -> new Thread(task, "url-consumer-" + consumerNames.incrementAndGet()));
        long began = System.nanoTime();
        try {
            List<Future<?>> consumerFutures = new ArrayList<>();
            for (int i = 0; i < consumerCount; i++) {
                consumerFutures.add(consumers.submit(() -> {
                    while (true) {
                        Task task = queue.take();
                        if (task == STOP) {
                            return null;
                        }
                        results.add(process(task));
                    }
                }));
            }
            List<Future<?>> producerFutures = new ArrayList<>();
            for (int p = 0; p < producerCount; p++) {
                int first = p;
                producerFutures.add(producers.submit(() -> {
                    for (int index = first; index < input.size(); index += producerCount) {
                        queue.put(new Task(index, input.get(index), Thread.currentThread().getName()));
                    }
                    return null;
                }));
            }
            // Сначала заканчивается публикация. Только затем в хвост FIFO добавляются стоп-маркеры.
            for (Future<?> producer : producerFutures) {
                while (true) {
                    checkConsumers(consumerFutures);
                    try {
                        producer.get(100, TimeUnit.MILLISECONDS);
                        break;
                    } catch (TimeoutException waitingForProducer) {
                        // При аварии Consumer нельзя бесконечно ждать Producer, заблокированный в put().
                    }
                }
            }
            for (int i = 0; i < consumerCount; i++) {
                while (!queue.offer(STOP, 100, TimeUnit.MILLISECONDS)) {
                    checkConsumers(consumerFutures);
                }
            }
            for (Future<?> consumer : consumerFutures) {
                consumer.get();
            }
            if (results.size() != input.size()) {
                throw new IllegalStateException("Обработаны не все URL");
            }
        } finally {
            // Разные пулы исключают ситуацию, когда заблокированные Producer заняли все потоки.
            producers.shutdownNow();
            consumers.shutdownNow();
            boolean producersStopped = producers.awaitTermination(5, TimeUnit.SECONDS);
            boolean consumersStopped = consumers.awaitTermination(5, TimeUnit.SECONDS);
            if (!producersStopped || !consumersStopped) {
                throw new IllegalStateException("Потоки сервера URL не завершились");
            }
        }
        return new BatchResult(results.stream().sorted(Comparator.comparingInt(Result::index)).toList(),
                System.nanoTime() - began, producers.isTerminated(), consumers.isTerminated());
    }

    private static void checkConsumers(List<Future<?>> consumers) throws InterruptedException, ExecutionException {
        for (Future<?> consumer : consumers) {
            if (consumer.isDone()) {
                consumer.get();
            }
        }
    }

    private Result process(Task task) throws InterruptedException {
        long began = System.nanoTime();
        Integer status = null;
        String title = "";
        String error = "";
        try {
            PageData page = Objects.requireNonNull(loader.load(task.url()));
            status = page.statusCode();
            title = page.title();
            if (status < 200 || status >= 300) {
                error = "HTTP " + status;
            }
        } catch (InterruptedException e) {
            throw e; // Прерывание останавливает Consumer, а не превращается в обычную ошибку URL.
        } catch (Exception e) {
            error = e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage());
            error = error.replaceAll("\\s+", " ");
        }
        return new Result(task.index(), task.url(), status, title, error, task.producer(),
                Thread.currentThread().getName(), System.nanoTime() - began);
    }
}
