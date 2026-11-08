import com.sun.management.OperatingSystemMXBean;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class VirtualThreadsDemo {
    private enum Mode { VIRTUAL, PLATFORM, SCOPE }

    private VirtualThreadsDemo() {
    }

    public static void main(String[] args) throws InterruptedException, ExecutionException {
        if (args.length == 1 && args[0].equals("cancel")) {
            demonstrateCancellation();
            return;
        }
        if (args.length > 4 || (args.length == 4 && !args[3].equals("--quiet"))) {
            throw new IllegalArgumentException(
                    "Usage: [virtual|platform|scope [tasks [sleep-ms [--quiet]]]] or cancel");
        }

        Mode mode = args.length == 0 ? Mode.VIRTUAL : Mode.valueOf(args[0].toUpperCase(Locale.ROOT));
        int count = args.length >= 2 ? Integer.parseInt(args[1]) : 10_000;
        long sleepMillis = args.length >= 3 ? Long.parseLong(args[2]) : 100;
        boolean logNames = args.length < 4;
        if (count < 1 || sleepMillis < 0) {
            throw new IllegalArgumentException("tasks must be positive and sleep-ms must be nonnegative");
        }

        AtomicInteger completed = new AtomicInteger();
        Callable<Void> task = () -> {
            if (logNames) {
                System.out.println(Thread.currentThread().getName());
            }
            Thread.sleep(sleepMillis);
            completed.incrementAndGet();
            return null;
        };

        OperatingSystemMXBean os = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);
        long cpuStart = os.getProcessCpuTime();
        long start = System.nanoTime();
        switch (mode) {
            case VIRTUAL, PLATFORM -> runDirect(mode, count, task);
            case SCOPE -> runScoped(count, task);
        }
        long elapsed = System.nanoTime() - start;
        long cpuEnd = os.getProcessCpuTime();

        if (completed.get() != count) {
            throw new IllegalStateException("Only " + completed.get() + " of " + count + " tasks completed");
        }
        String cpuMillis = cpuStart < 0 || cpuEnd < 0 ? "n/a"
                : String.format(Locale.ROOT, "%.3f", (cpuEnd - cpuStart) / 1_000_000.0);
        System.out.printf(Locale.ROOT,
                "mode=%s tasks=%d completed=%d sleep_ms=%d wall_ms=%.3f cpu_ms=%s%n",
                mode.name().toLowerCase(Locale.ROOT), count, completed.get(), sleepMillis,
                elapsed / 1_000_000.0, cpuMillis);
    }

    private static void runDirect(Mode mode, int count, Callable<Void> task) throws InterruptedException {
        List<Thread> threads = new ArrayList<>(count);
        Thread.Builder.OfVirtual virtualBuilder = Thread.ofVirtual().name("virtual-", 0);
        Runnable work = () -> {
            try {
                task.call();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                throw new IllegalStateException("Task failed", e);
            }
        };

        try {
            for (int i = 0; i < count; i++) {
                if (mode == Mode.VIRTUAL) {
                    threads.add(virtualBuilder.start(work));
                } else {
                    Thread thread = new Thread(work, "platform-" + i);
                    thread.start();
                    threads.add(thread);
                }
            }
            for (Thread thread : threads) {
                thread.join();
            }
        } catch (InterruptedException | RuntimeException | Error failure) {
            stopAndJoin(threads);
            throw failure;
        }
    }

    private static void stopAndJoin(List<Thread> threads) {
        threads.forEach(Thread::interrupt);
        boolean interrupted = false;
        for (Thread thread : threads) {
            while (thread.isAlive()) {
                try {
                    thread.join();
                } catch (InterruptedException e) {
                    interrupted = true;
                }
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static StructuredTaskScope.ShutdownOnFailure newScope() {
        return new StructuredTaskScope.ShutdownOnFailure(
                "io-tasks", Thread.ofVirtual().name("scope-", 0).factory());
    }

    private static void runScoped(int count, Callable<Void> task)
            throws InterruptedException, ExecutionException {
        try (var scope = newScope()) {
            for (int i = 0; i < count; i++) {
                scope.fork(task);
            }
            scope.join().throwIfFailed();
        }
    }

    private static void demonstrateCancellation() throws InterruptedException, ExecutionException {
        CountDownLatch ready = new CountDownLatch(1);
        AtomicBoolean cancelled = new AtomicBoolean();
        IllegalStateException plannedFailure = new IllegalStateException("simulated I/O failure");

        try (var scope = newScope()) {
            scope.fork(() -> {
                ready.countDown();
                try {
                    Thread.sleep(30_000);
                } catch (InterruptedException e) {
                    cancelled.set(true);
                    System.out.println(Thread.currentThread().getName() + " interrupted");
                    throw e;
                }
                return null;
            });
            scope.fork(() -> {
                ready.await();
                throw plannedFailure;
            });
            scope.join().throwIfFailed();
        } catch (ExecutionException e) {
            if (e.getCause() != plannedFailure) {
                throw e;
            }
            if (!cancelled.get()) {
                throw new IllegalStateException("The sibling task did not observe cancellation", e);
            }
            System.out.println("scope failure: " + e.getCause());
            return;
        }
        throw new IllegalStateException("Expected the scope to fail");
    }
}
