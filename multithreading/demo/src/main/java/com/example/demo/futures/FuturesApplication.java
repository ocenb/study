package com.example.demo.futures;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/** Консольный запуск всех этапов; значения валют полностью вымышлены. */
public final class FuturesApplication {
    private FuturesApplication() {
    }

    public static void main(String[] args) throws Exception {
        System.out.println("1. Future: обработка рассчитана на 8 секунд; cancel(true) через 2,5 секунды.");
        System.out.println(ExecutorExamples.cancelLongTask(Duration.ofSeconds(8), Duration.ofMillis(2_500)));

        System.out.println("2. invokeAll: пять задач, случайная длительность 1000..3000 мс.");
        long began = System.nanoTime();
        for (ExecutorExamples.TaskResult result : ExecutorExamples.invokeRandomTasks()) {
            System.out.println(result);
        }
        System.out.printf(java.util.Locale.ROOT, "Время всей партии: %.3f мс%n",
                (System.nanoTime() - began) / 1_000_000.0);

        System.out.println("3. ScheduledExecutorService: проверка статуса каждую секунду.");
        int checks = ExecutorExamples.monitor(3, Duration.ofSeconds(1),
                tick -> System.out.println("Проверка #" + tick + ": система работает"));
        System.out.println("Проверок выполнено: " + checks);

        System.out.println("4. PeriodicDataAggregator: N=1 секунда, три сущности, три цикла.");
        System.out.println("Значения валют вымышлены. Первый запрос EUR намеренно завершается ошибкой.");
        AtomicInteger eurCalls = new AtomicInteger();
        AtomicInteger lists = new AtomicInteger();
        ConcurrentHashMap<String, PeriodicDataAggregator.EntityData> saved = new ConcurrentHashMap<>();
        PeriodicDataAggregator.DataSource source = new PeriodicDataAggregator.DataSource() {
            public List<String> identifiers() {
                System.out.println("Получен список сущностей, цикл #" + lists.incrementAndGet());
                return List.of("USD", "EUR", "CNY");
            }

            public PeriodicDataAggregator.EntityData fetch(String id) throws Exception {
                Thread.sleep(ThreadLocalRandom.current().nextInt(50, 151));
                if (id.equals("EUR") && eurCalls.getAndIncrement() == 0) {
                    throw new IOException("Имитация временной недоступности источника EUR");
                }
                BigDecimal value = switch (id) {
                    case "USD" -> new BigDecimal("92.45");
                    case "EUR" -> new BigDecimal("101.20");
                    default -> new BigDecimal("12.88");
                };
                return new PeriodicDataAggregator.EntityData(id, value, Instant.now());
            }
        };
        PeriodicDataAggregator aggregator = new PeriodicDataAggregator(Duration.ofSeconds(1), 3, source,
                data -> {
                    saved.put(data.id(), data);
                    System.out.println("Сохранено: " + data.id() + "=" + data.value());
                }, (id, error) -> System.out.println("Ошибка " + id + ": " + error.getMessage()));
        try (aggregator) {
            aggregator.start();
            long deadline = System.nanoTime() + Duration.ofSeconds(8).toNanos();
            while (aggregator.stats().completedCycles() < 3) {
                if (System.nanoTime() >= deadline) {
                    throw new IllegalStateException("Три цикла агрегатора не завершились");
                }
                Thread.sleep(10);
            }
            System.out.println("Результат до закрытия: " + aggregator.stats());
        }
        System.out.println("Сохранённые сущности: " + saved.keySet().stream().sorted().toList());
        System.out.println("Результат после закрытия: " + aggregator.stats());
    }
}
