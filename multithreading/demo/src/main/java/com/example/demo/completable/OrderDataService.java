package com.example.demo.completable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/** Параллельный конвейер: пользователь + товар + курс -> цена -> сохранённый заказ. */
public final class OrderDataService implements AutoCloseable {
    public record User(String name) {
        public User { Objects.requireNonNull(name); }
    }

    public record Product(String name, BigDecimal priceUsd) {
        public Product {
            Objects.requireNonNull(name);
            Objects.requireNonNull(priceUsd);
        }
    }

    public record FinalResult(String requestId, String userName, String productName,
                              BigDecimal priceUsd, BigDecimal priceRub) {
    }

    public interface DataSource {
        User user(String id) throws Exception;
        Product product(String id) throws Exception;
        BigDecimal rubPerUsd(String id) throws Exception;
    }

    @FunctionalInterface
    public interface OrderStore {
        void save(FinalResult result) throws Exception;
    }

    @FunctionalInterface
    private interface CheckedSupplier<T> {
        T get() throws Exception;
    }

    private record PricedProduct(Product product, BigDecimal rate) {
    }

    private record Combined(User user, PricedProduct priced) {
    }

    private final ExecutorService executor;
    private final DataSource source;
    private final OrderStore store;
    private final Consumer<String> trace;
    private final long transformationNanos;
    private boolean closed;

    public OrderDataService(int parallelism, DataSource source, OrderStore store,
                            Duration transformationDelay, Consumer<String> trace) {
        if (parallelism < 1 || parallelism > 32) {
            throw new IllegalArgumentException("parallelism: 1..32");
        }
        transformationNanos = Objects.requireNonNull(transformationDelay).toNanos();
        if (transformationNanos < 0) {
            throw new IllegalArgumentException("Задержка не может быть отрицательной");
        }
        this.source = Objects.requireNonNull(source);
        this.store = Objects.requireNonNull(store);
        this.trace = Objects.requireNonNull(trace);
        AtomicInteger number = new AtomicInteger();
        executor = Executors.newFixedThreadPool(parallelism, task -> {
            Thread thread = new Thread(task, "order-data-" + number.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
    }

    private void log(String id, String stage) {
        trace.accept("request=" + id + " thread=" + Thread.currentThread().getName() + " stage=" + stage);
    }

    private <T> T call(String id, String stage, CheckedSupplier<T> operation) {
        log(id, stage + ":start");
        try {
            T result = Objects.requireNonNull(operation.get(), "Источник вернул null");
            log(id, stage + ":done");
            return result;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CompletionException(e);
        } catch (Exception e) {
            throw new CompletionException(e);
        }
    }

    private <T> CompletableFuture<T> fetch(String id, String stage, CheckedSupplier<T> operation) {
        return CompletableFuture.supplyAsync(() -> call(id, stage, operation), executor);
    }

    private Product normalize(String id, Product product) {
        log(id, "thenApply:normalize");
        return new Product(product.name().strip().replaceAll("\\s+", " "), product.priceUsd());
    }

    private FinalResult convert(String id, Combined data) {
        return call(id, "price", () -> {
            // Имитация тяжёлого/блокирующего обогащения; всегда выполняется на собственном executor.
            TimeUnit.NANOSECONDS.sleep(transformationNanos);
            BigDecimal priceRub = data.priced().product().priceUsd().multiply(data.priced().rate())
                    .setScale(2, RoundingMode.HALF_UP);
            return new FinalResult(id, data.user().name().strip(), data.priced().product().name(),
                    data.priced().product().priceUsd(), priceRub);
        });
    }

    private FinalResult save(FinalResult result) {
        return call(result.requestId(), "save", () -> {
            store.save(result);
            return result;
        });
    }

    private void failure(String id, Throwable error) {
        while (error instanceof CompletionException && error.getCause() != null) {
            error = error.getCause();
        }
        log(id, "ERROR:" + error.getClass().getSimpleName() + ":" + error.getMessage());
    }

    /** Запускает все конвейеры параллельно; возвращает успешные заказы в порядке входных ID. */
    public synchronized List<FinalResult> fetchAllDataAsync(List<String> ids) {
        checkOpen();
        List<String> input = List.copyOf(ids);
        List<CompletableFuture<Optional<FinalResult>>> results = new ArrayList<>();
        for (String id : input) {
            CompletableFuture<User> user = fetch(id, "user", () -> source.user(id));
            CompletableFuture<Product> product = fetch(id, "product", () -> source.product(id))
                    .thenApply(value -> normalize(id, value));
            CompletableFuture<BigDecimal> rate = fetch(id, "rate", () -> source.rubPerUsd(id));
            CompletableFuture<PricedProduct> priced = product.thenCombine(rate, (value, exchange) -> {
                log(id, "thenCombine:product+rate");
                return new PricedProduct(value, exchange);
            });
            CompletableFuture<FinalResult> saved = priced.thenCombine(user, (value, owner) -> {
                        log(id, "thenCombine:user+pricedProduct");
                        return new Combined(owner, value);
                    })
                    .thenApplyAsync(value -> convert(id, value), executor)
                    .thenApplyAsync(this::save, executor);
            CompletableFuture<Void> accepted = saved.thenAccept(value -> log(id, "thenAccept:saved"));
            // Ожидаем и сохранение, и thenAccept; никакого join внутри рабочего пула.
            results.add(saved.thenCombine(accepted, (value, unused) -> value)
                    .handle((value, error) -> {
                        if (error != null) {
                            failure(id, error);
                            return Optional.empty();
                        }
                        return Optional.of(value);
                    }));
        }
        CompletableFuture.allOf(results.toArray(CompletableFuture<?>[]::new)).join();
        return results.stream().map(CompletableFuture::join).flatMap(Optional::stream).toList();
    }

    /** Те же источники, преобразования, округление и сохранение, но последовательно. */
    public synchronized List<FinalResult> fetchAllDataSync(List<String> ids) {
        checkOpen();
        List<FinalResult> results = new ArrayList<>();
        for (String id : List.copyOf(ids)) {
            try {
                User user = call(id, "user", () -> source.user(id));
                Product product = normalize(id, call(id, "product", () -> source.product(id)));
                BigDecimal rate = call(id, "rate", () -> source.rubPerUsd(id));
                FinalResult result = save(convert(id, new Combined(user, new PricedProduct(product, rate))));
                log(id, "thenAccept:saved");
                results.add(result);
            } catch (CompletionException e) {
                failure(id, e);
            }
        }
        return List.copyOf(results);
    }

    private void checkOpen() {
        if (closed) {
            throw new IllegalStateException("OrderDataService закрыт");
        }
    }

    public boolean isTerminated() {
        return executor.isTerminated();
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Пул заказов не завершился");
                }
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
