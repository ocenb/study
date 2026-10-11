package com.example.demo.atomics;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** Консольная демонстрация и учебный нагрузочный тест без запуска Spring. */
public final class AtomicsDemo {
    private AtomicsDemo() {
    }

    public record Result(int threads, int operations, int counter, long elapsedNanos,
                         long stopNanos, boolean sameCacheReference) {
        public double millis() {
            return elapsedNanos / 1_000_000.0;
        }

        public double millionOperationsPerSecond() {
            return operations * 1_000.0 / elapsedNanos;
        }
    }

    private interface Counter {
        void increment();
        int get();
    }

    private static final class MonitorCounter implements Counter {
        private int value;

        public synchronized void increment() {
            value++;
        }

        public synchronized int get() {
            return value;
        }
    }

    private record WorkerResult(int operations, String cached) {
    }

    public static Result fixedWork(int threads, int iterations, boolean atomic) throws Exception {
        if (threads < 1 || threads > 64 || iterations < 1
                || (long) threads * iterations > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("threads: 1..64; iterations > 0; итог <= Integer.MAX_VALUE");
        }
        return run(threads, iterations, 0, atomic);
    }

    public static Result stopDemo(int threads, long durationMillis) throws Exception {
        if (threads < 1 || threads > 64 || durationMillis < 1 || durationMillis > 1_000) {
            throw new IllegalArgumentException("threads: 1..64; durationMillis: 1..1000");
        }
        return run(threads, Integer.MAX_VALUE, durationMillis, true);
    }

    private static Result run(int threads, int iterations, long durationMillis, boolean atomic)
            throws Exception {
        AtomicCounter atomicCounter = new AtomicCounter();
        Counter counter = atomic ? new Counter() {
            public void increment() { atomicCounter.increment(); }
            public int get() { return atomicCounter.get(); }
        } : new MonitorCounter();
        StopSignal stop = new StopSignal();
        SingletonCache cache = SingletonCache.getInstance();
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Future<WorkerResult>> futures = new ArrayList<>();
        try {
            for (int t = 0; t < threads; t++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    int completed = 0;
                    String first = null;
                    while (!stop.isStopped() && completed < iterations) {
                        counter.increment();
                        String cached = cache.getOrCreate();
                        if (first == null) {
                            first = cached;
                        } else if (first != cached) {
                            throw new IllegalStateException("Ссылка кэша изменилась");
                        }
                        completed++;
                    }
                    return new WorkerResult(completed, first);
                }));
            }
            if (!ready.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Потоки не готовы к старту");
            }
            long began = System.nanoTime();
            start.countDown();
            long stopBegan = 0;
            if (durationMillis > 0) {
                Thread.sleep(durationMillis);
                stopBegan = System.nanoTime();
                stop.stop();
            }
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
            int operations = 0;
            boolean sameReference = true;
            String expected = null;
            for (Future<WorkerResult> future : futures) {
                WorkerResult worker = future.get(Math.max(1, deadline - System.nanoTime()),
                        TimeUnit.NANOSECONDS);
                operations = Math.addExact(operations, worker.operations());
                if (worker.operations() > 0) {
                    if (expected == null) {
                        expected = worker.cached();
                    }
                    sameReference &= expected == worker.cached();
                }
            }
            long ended = System.nanoTime();
            int actual = counter.get();
            if (actual != operations || !sameReference
                    || (durationMillis == 0 && operations != threads * iterations)) {
                throw new IllegalStateException("Проверка счётчика или кэша не пройдена");
            }
            return new Result(threads, operations, actual, ended - began,
                    stopBegan == 0 ? 0 : ended - stopBegan, sameReference);
        } finally {
            stop.stop();
            start.countDown();
            pool.shutdownNow();
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Потоки не завершились после остановки");
            }
        }
    }

    public static void main(String[] args) throws Exception {
        Locale.setDefault(Locale.ROOT);
        System.out.printf("Java: %s; VM: %s; OS: %s %s; CPUs: %d%n",
                System.getProperty("java.version"), System.getProperty("java.vm.name"),
                System.getProperty("os.name"), System.getProperty("os.arch"),
                Runtime.getRuntime().availableProcessors());
        System.out.println("Начальное состояние нового счётчика: " + new AtomicCounter().get());
        // Первый запуск также проверяет конкурентную ленивую инициализацию кэша.
        Result cold = fixedWork(8, 10_000, true);
        System.out.printf("Первое обращение: counter=%d; sameReference=%s; cache=%s%n",
                cold.counter(), cold.sameCacheReference(), SingletonCache.getInstance().getOrCreate());
        Result stopped = stopDemo(4, 200);
        System.out.printf("volatile stop: threads=4; operations=%d; counter=%d; sameReference=%s; "
                        + "total=%.3f ms; stop-to-join=%.3f ms%n",
                stopped.operations(), stopped.counter(), stopped.sameCacheReference(),
                stopped.millis(), stopped.stopNanos() / 1_000_000.0);

        System.out.println("Прогрев: 3 запуска каждого варианта; замер: 5 запусков; 250000 операций/поток.");
        System.out.println("threads,mode,operations,median_ms,min_ms,max_ms,median_Mops_s,counter,cache_same");
        for (int threads : new int[]{1, 2, 4, 8}) {
            for (int warmup = 0; warmup < 3; warmup++) {
                fixedWork(threads, 250_000, true);
                fixedWork(threads, 250_000, false);
            }
            double[][] times = new double[2][5];
            Result[] last = new Result[2];
            for (int repeat = 0; repeat < 5; repeat++) {
                // Чередуем порядок, чтобы уменьшить систематическое влияние порядка запусков.
                for (int index = 0; index < 2; index++) {
                    int mode = (index + repeat) % 2;
                    last[mode] = fixedWork(threads, 250_000, mode == 0);
                    times[mode][repeat] = last[mode].millis();
                }
            }
            for (int mode = 0; mode < 2; mode++) {
                Arrays.sort(times[mode]);
                Result result = last[mode];
                System.out.printf("%d,%s,%d,%.3f,%.3f,%.3f,%.3f,%d,%s%n", threads,
                        mode == 0 ? "AtomicInteger" : "synchronized", result.operations(),
                        times[mode][2], times[mode][0], times[mode][4],
                        result.operations() / times[mode][2] / 1_000.0,
                        result.counter(), result.sameCacheReference());
            }
        }
    }
}
