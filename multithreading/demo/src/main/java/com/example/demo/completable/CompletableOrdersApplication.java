package com.example.demo.completable;

import java.time.Duration;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Источники — CSV-файлы; задержки имитируются, цены и курс вымышлены. */
public final class CompletableOrdersApplication {
    private CompletableOrdersApplication() {
    }

    private static OrderDataService service(Consumer<String> trace,
                                           ConcurrentHashMap<String, OrderDataService.FinalResult> orders) {
        OrderDataService.DataSource source = new FileOrderDataSource(Path.of("order-data"), "P3");
        return new OrderDataService(6, source, result -> {
            Thread.sleep(30);
            orders.put(result.requestId(), result);
        }, Duration.ofMillis(40), trace);
    }

    private record Measurement(List<OrderDataService.FinalResult> results, double millis) {
    }

    private static Measurement measure(List<String> ids, boolean async) {
        ConcurrentHashMap<String, OrderDataService.FinalResult> saved = new ConcurrentHashMap<>();
        try (OrderDataService service = service(message -> {}, saved)) {
            long began = System.nanoTime();
            List<OrderDataService.FinalResult> results = async
                    ? service.fetchAllDataAsync(ids) : service.fetchAllDataSync(ids);
            double millis = (System.nanoTime() - began) / 1_000_000.0;
            if (saved.size() != results.size()) {
                throw new IllegalStateException("Сохранены не все успешные заказы");
            }
            return new Measurement(results, millis);
        }
    }

    public static void main(String[] args) {
        List<String> ids = List.of("P1", "P2", "P3", "P4", "P5");
        System.out.println("Java=" + System.getProperty("java.version") + "; pool=6 daemon threads");
        System.out.println("Данные из order-data/*.csv; задержки: user=80ms, product=120ms, rate=100ms, transform=40ms, save=30ms.");
        ConcurrentHashMap<String, OrderDataService.FinalResult> saved = new ConcurrentHashMap<>();
        OrderDataService service = service(System.out::println, saved);
        try (service) {
            List<OrderDataService.FinalResult> results = service.fetchAllDataAsync(ids);
            System.out.println("Итоговые объекты: " + results);
            System.out.println("Входных ID=" + ids.size() + "; результатов=" + results.size()
                    + "; сохранённых заказов=" + saved.size());
        }
        System.out.println("Executor terminated=" + service.isTerminated());

        System.out.println("Сравнение без вывода трассировки: один прогрев каждого варианта, три замера.");
        measure(ids, false);
        measure(ids, true);
        double[] syncTimes = new double[3];
        double[] asyncTimes = new double[3];
        for (int repeat = 0; repeat < 3; repeat++) {
            Measurement sync;
            Measurement async;
            if (repeat % 2 == 0) {
                sync = measure(ids, false);
                async = measure(ids, true);
            } else {
                async = measure(ids, true);
                sync = measure(ids, false);
            }
            if (!sync.results().equals(async.results())) {
                throw new IllegalStateException("Результаты sync и async различаются");
            }
            syncTimes[repeat] = sync.millis();
            asyncTimes[repeat] = async.millis();
            System.out.printf(Locale.ROOT, "Замер %d: sync=%.3f ms; async=%.3f ms; equal=true; results=%d%n",
                    repeat + 1, sync.millis(), async.millis(), async.results().size());
        }
        Arrays.sort(syncTimes);
        Arrays.sort(asyncTimes);
        System.out.printf(Locale.ROOT, "Медиана: sync=%.3f ms; async=%.3f ms; speedup=%.2fx%n",
                syncTimes[1], asyncTimes[1], syncTimes[1] / asyncTimes[1]);
    }
}
