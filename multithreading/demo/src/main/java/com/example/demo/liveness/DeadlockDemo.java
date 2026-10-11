package com.example.demo.liveness;

import java.lang.management.ManagementFactory;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Намеренное зависание. Запускать только в отдельной JVM; завершать процесс после дампа. */
public final class DeadlockDemo {
    private static final Object resourceA = new Object();
    private static final Object resourceB = new Object();

    private DeadlockDemo() {
    }

    private static void acquire(Object first, Object second, CountDownLatch acquired) {
        synchronized (first) {
            System.out.println(Thread.currentThread().getName() + ": первый ресурс захвачен");
            acquired.countDown();
            try {
                // await освобождает ожидание latch, но НЕ монитор first.
                acquired.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            synchronized (second) {
                System.out.println("Эта строка недостижима при созданном deadlock");
            }
        }
    }

    public static void main(String[] args) throws InterruptedException {
        CountDownLatch acquired = new CountDownLatch(2);
        Thread first = new Thread(() -> acquire(resourceA, resourceB, acquired), "Deadlock-1-A-then-B");
        Thread second = new Thread(() -> acquire(resourceB, resourceA, acquired), "Deadlock-2-B-then-A");
        System.out.println("PID=" + ProcessHandle.current().pid());
        first.start();
        second.start();
        if (!acquired.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Первые ресурсы не захвачены");
        }
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (ManagementFactory.getThreadMXBean().findMonitorDeadlockedThreads() == null) {
            if (System.nanoTime() >= deadline) {
                throw new IllegalStateException("Deadlock не обнаружен");
            }
            Thread.sleep(10);
        }
        System.out.println("DEADLOCK_CONFIRMED: оба потока BLOCKED. Снимите jstack <PID> или kill -3 <PID>.");
        // Намеренно не daemon: JVM действительно остаётся зависшей для диагностики.
        first.join();
        second.join();
    }
}
