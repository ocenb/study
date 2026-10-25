package com.example.demo.forkjoin;

import com.example.demo.completable.FileOrderDataSource;
import com.example.demo.completable.OrderDataService;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/** Использует агрегированные заказы из предыдущего задания с CompletableFuture. */
public final class ForkJoinApplication {
    private ForkJoinApplication() {
    }

    public static void main(String[] args) {
        Path directory = args.length == 0 ? Path.of("order-data") : Path.of(args[0]);
        List<String> ids = List.of("P1", "P2", "P3", "P4", "P5");
        ConcurrentHashMap<String, OrderDataService.FinalResult> saved = new ConcurrentHashMap<>();
        List<OrderDataService.FinalResult> aggregated;
        System.out.println("Java=" + System.getProperty("java.version"));
        try (OrderDataService service = new OrderDataService(6,
                new FileOrderDataSource(directory, "P3"),
                order -> saved.put(order.requestId(), order), Duration.ZERO,
                message -> { if (message.contains("ERROR:")) { System.out.println(message); } })) {
            aggregated = service.fetchAllDataAsync(ids);
        }
        System.out.println("Исходных ID: " + ids.size());
        System.out.println("Успешные агрегированные ID: "
                + aggregated.stream().map(OrderDataService.FinalResult::requestId).toList());
        System.out.println("Сохранённых заказов: " + saved.size());

        ForkJoinElementCounter counter = new ForkJoinElementCounter(4);
        try (counter) {
            print("Агрегация предыдущего задания", counter.count(aggregated, 2));
            print("Пустой список", counter.count(List.of(), 2));
            // Расширяем уже полученные результаты, не делая 80000 запросов к источникам.
            List<OrderDataService.FinalResult> repeated = new ArrayList<>();
            for (int i = 0; i < 20_000; i++) {
                repeated.addAll(aggregated);
            }
            ForkJoinElementCounter.CountResult result = counter.count(repeated, 1_000);
            print("Увеличенный список, batchSize=1000", result);
            long sequential = repeated.stream().mapToLong(item -> 1L).sum();
            System.out.println("Последовательный подсчёт: " + sequential);
            System.out.println("Результаты совпадают: " + (sequential == result.elements()));
            if (result.elements() != sequential || aggregated.size() != saved.size()) {
                throw new IllegalStateException("Результаты подсчёта различаются");
            }
        }
        System.out.println("ForkJoinPool terminated=" + counter.isTerminated());
    }

    private static void print(String label, ForkJoinElementCounter.CountResult result) {
        System.out.printf("%s: элементов=%d; батчей=%d; всего задач=%d%n",
                label, result.elements(), result.batches(), result.tasks());
    }
}
