package com.example.demo.liveness;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

/** Ограниченные по времени демонстрации и исправления проблем прогресса потоков. */
public final class LivenessDemo {
    private LivenessDemo() {
    }

    public record LivelockResult(int attempts, int completed) {
    }

    public record StarvationResult(int monopolistOperations, int victimWhileMonopolized,
                                   boolean victimQueued, int victimAfterRelease) {
    }

    private static List<Integer> runWorkers(List<Callable<Integer>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        try {
            List<Future<Integer>> futures = pool.invokeAll(tasks, 10, TimeUnit.SECONDS);
            List<Integer> results = new ArrayList<>();
            for (Future<Integer> future : futures) {
                results.add(future.get()); // Отмена или ошибка задачи не маскируется.
            }
            return List.copyOf(results);
        } finally {
            pool.shutdownNow();
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Рабочие потоки не остановились");
            }
        }
    }

    private static void validate(int threads, int iterations) {
        if (threads < 1 || threads > 32 || iterations < 1 || iterations > 100_000) {
            throw new IllegalArgumentException("threads: 1..32; iterations: 1..100000");
        }
    }

    /** Все участники берут A, затем B: циклического ожидания нет. */
    public static List<Integer> orderedResources(int threads, int iterations) throws Exception {
        validate(threads, iterations);
        ReentrantLock resourceA = new ReentrantLock();
        ReentrantLock resourceB = new ReentrantLock();
        CyclicBarrier start = new CyclicBarrier(threads);
        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            tasks.add(() -> {
                start.await(5, TimeUnit.SECONDS);
                int completed = 0;
                for (int i = 0; i < iterations; i++) {
                    resourceA.lockInterruptibly();
                    try {
                        resourceB.lockInterruptibly();
                        try {
                            completed++;
                        } finally {
                            resourceB.unlock();
                        }
                    } finally {
                        resourceA.unlock();
                    }
                }
                return completed;
            });
        }
        return runWorkers(tasks);
    }

    /** Барьеры моделируют повторяющиеся столкновения, а не случайную удачу планировщика. */
    public static LivelockResult livelock(int rounds, boolean prevent) throws Exception {
        validate(2, rounds);
        ReentrantLock resourceA = new ReentrantLock();
        ReentrantLock resourceB = new ReentrantLock();
        CyclicBarrier phase = new CyclicBarrier(2);
        AtomicInteger attempts = new AtomicInteger();
        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int t = 0; t < 2; t++) {
            ReentrantLock first = t == 0 ? resourceA : resourceB;
            ReentrantLock second = t == 0 ? resourceB : resourceA;
            tasks.add(() -> {
                int completed = 0;
                for (int i = 0; i < rounds; i++) {
                    first.lockInterruptibly();
                    try {
                        phase.await(5, TimeUnit.SECONDS); // Оба удерживают разные ресурсы.
                        attempts.incrementAndGet();
                        if (second.tryLock()) {
                            try {
                                completed++;
                            } finally {
                                second.unlock();
                            }
                        }
                        // Никто не отпускает первый ресурс до попытки соседа.
                        phase.await(5, TimeUnit.SECONDS);
                    } finally {
                        first.unlock();
                    }
                }
                if (prevent) {
                    // Конечный бюджет повторов исчерпан: меняем алгоритм захвата.
                    resourceA.lockInterruptibly();
                    try {
                        resourceB.lockInterruptibly();
                        try {
                            completed++;
                        } finally {
                            resourceB.unlock();
                        }
                    } finally {
                        resourceA.unlock();
                    }
                }
                return completed;
            });
        }
        List<Integer> results = runWorkers(tasks);
        return new LivelockResult(attempts.get(), results.stream().mapToInt(Integer::intValue).sum());
    }

    /** Контролируемое окно starvation: владелец обслуживает свою работу, не передавая ресурс. */
    public static StarvationResult starvation(int operations) throws Exception {
        validate(2, operations);
        ReentrantLock resource = new ReentrantLock(false);
        CountDownLatch held = new CountDownLatch(1);
        CountDownLatch work = new CountDownLatch(1);
        CountDownLatch worked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger monopolistCount = new AtomicInteger();
        AtomicInteger victimCount = new AtomicInteger();
        AtomicReference<Thread> victimThread = new AtomicReference<>();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> monopolist = pool.submit(() -> {
                resource.lockInterruptibly();
                try {
                    held.countDown();
                    work.await();
                    for (int i = 0; i < operations; i++) {
                        monopolistCount.incrementAndGet();
                    }
                    worked.countDown();
                    release.await(); // Ресурс не передаётся даже после выполнения своей работы.
                } finally {
                    resource.unlock();
                }
                return null;
            });
            if (!held.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Владелец не захватил ресурс");
            }
            Future<?> victim = pool.submit(() -> {
                victimThread.set(Thread.currentThread());
                resource.lockInterruptibly();
                try {
                    victimCount.incrementAndGet();
                } finally {
                    resource.unlock();
                }
                return null;
            });
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (victimThread.get() == null || !resource.hasQueuedThread(victimThread.get())) {
                if (System.nanoTime() >= deadline) {
                    throw new IllegalStateException("Ожидающий поток не встал в очередь");
                }
                Thread.sleep(1);
            }
            work.countDown();
            if (!worked.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Владелец не выполнил работу");
            }
            int during = victimCount.get();
            boolean queued = resource.hasQueuedThread(victimThread.get());
            release.countDown();
            monopolist.get(5, TimeUnit.SECONDS);
            victim.get(5, TimeUnit.SECONDS);
            return new StarvationResult(monopolistCount.get(), during, queued, victimCount.get());
        } finally {
            work.countDown();
            release.countDown();
            pool.shutdownNow();
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Потоки starvation не остановились");
            }
        }
    }

    /** Короткие критические секции и справедливая очередь вместо монополизации ресурса. */
    public static List<Integer> fairAccess(int threads, int iterations) throws Exception {
        validate(threads, iterations);
        ReentrantLock resource = new ReentrantLock(true);
        CyclicBarrier start = new CyclicBarrier(threads);
        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            tasks.add(() -> {
                start.await(5, TimeUnit.SECONDS);
                int completed = 0;
                for (int i = 0; i < iterations; i++) {
                    resource.lockInterruptibly();
                    try {
                        completed++;
                    } finally {
                        resource.unlock(); // Передаём ресурс после каждой операции.
                    }
                }
                return completed;
            });
        }
        return runWorkers(tasks);
    }

    public static void main(String[] args) throws Exception {
        System.out.printf("Java=%s; OS=%s; CPUs=%d%n", System.getProperty("java.version"),
                System.getProperty("os.name"), Runtime.getRuntime().availableProcessors());
        System.out.println("Deadlock prevention (A -> B): " + orderedResources(2, 10_000));
        System.out.println("Livelock (bounded observation): " + livelock(1_000, false));
        System.out.println("Livelock prevention (retry budget + A -> B): " + livelock(10, true));
        System.out.println("Starvation (controlled observation): " + starvation(10_000));
        System.out.println("Starvation prevention (fair lock, short sections): " + fairAccess(4, 1_000));
    }
}
