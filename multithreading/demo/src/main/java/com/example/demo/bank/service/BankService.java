package com.example.demo.bank.service;

import com.example.demo.bank.collector.DataCollector;
import com.example.demo.bank.model.BankAccount;
import com.example.demo.bank.model.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Сервис банковских операций и координации многопоточных переводов.
 * Обеспечивает:
 * 1. Защиту от гонки данных (Race Condition)
 * 2. Предотвращение взаимных блокировок (Deadlock) через глобальный порядок захвата мониторов
 * 3. Ожидание и уведомление через wait() и notifyAll()
 * 4. Сбор и аудит транзакций через DataCollector
 */
@Service
public class BankService {

    private static final Logger log = LoggerFactory.getLogger(BankService.class);

    private final DataCollector dataCollector = new DataCollector();

    public DataCollector getDataCollector() {
        return dataCollector;
    }

    /**
     * Потокобезопасный перевод средств с гарантированным ПРЕДОТВРАЩЕНИЕМ DEADLOCK.
     * Мониторы аккаунтов ВСЕГДА захватываются в фиксированном порядке: по возрастанию их ID.
     */
    public boolean transferSafe(BankAccount from, BankAccount to, long amount) {
        if (from == null || to == null || from == to || amount <= 0) {
            return false;
        }

        // Правило предотвращения Deadlock: всегда сначала блокируем меньший ID, затем больший
        BankAccount firstLock = from.getId() < to.getId() ? from : to;
        BankAccount secondLock = from.getId() < to.getId() ? to : from;

        synchronized (firstLock) {
            synchronized (secondLock) {
                if (from.withdraw(amount)) {
                    to.deposit(amount);

                    // Регистрируем успешную операцию в DataCollector
                    String txKey = String.format("TX-%d-%d-%d-%d", from.getId(), to.getId(), amount, System.nanoTime());
                    dataCollector.collectItem(new Item(txKey, "Transfer " + from.getId() + " -> " + to.getId(), amount));
                    return true;
                }
                return false;
            }
        }
    }

    /**
     * Небезопасный перевод (без упорядочивания блокировок).
     * Демонстрация: если Thread 1 переводит A -> B, а Thread 2 переводит B -> A, возникает DEADLOCK!
     */
    public boolean transferUnsafe(BankAccount from, BankAccount to, long amount) {
        if (from == null || to == null || from == to || amount <= 0) {
            return false;
        }

        // ОШИБКА: порядок захвата зависит от направления перевода!
        synchronized (from) {
            try {
                // Имитируем небольшую задержку, провоцирующую взаимную блокировку
                Thread.sleep(1);
            } catch (InterruptedException ignored) {}

            synchronized (to) {
                if (from.withdraw(amount)) {
                    to.deposit(amount);
                    return true;
                }
                return false;
            }
        }
    }

    /**
     * Стресс-тест безопасных переводов в условиях жесткой многопоточной конкуренции.
     * Запускает несколько потоков, хаотично переводящих средства между аккаунтами.
     * Контролирует:
     * 1. Отсутствие взаимных блокировок (Deadlock)
     * 2. Сохранение суммарного баланса банка (Инвариант данных)
     */
    public Map<String, Object> runTransferStressTest(int threadCount, int transfersPerThread) throws InterruptedException {
        dataCollector.clear();

        // Создаем пул счетов
        List<BankAccount> accounts = List.of(
                new BankAccount(101, "Alice", 100_000),
                new BankAccount(102, "Bob", 100_000),
                new BankAccount(103, "Charlie", 100_000),
                new BankAccount(104, "Diana", 100_000),
                new BankAccount(105, "Eve", 100_000)
        );

        long initialTotalBalance = accounts.stream().mapToLong(BankAccount::getBalance).sum();
        log.info("Начальный суммарный баланс банка: {} руб.", initialTotalBalance);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(threadCount);
        AtomicInteger successfulTransfers = new AtomicInteger(0);

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < threadCount; i++) {
            final int threadIndex = i;
            executor.submit(() -> {
                try {
                    startLatch.await(); // Одновременный старт всех потоков
                    Random random = new Random(42 + threadIndex);

                    for (int j = 0; j < transfersPerThread; j++) {
                        int fromIdx = random.nextInt(accounts.size());
                        int toIdx = random.nextInt(accounts.size());
                        if (fromIdx == toIdx) {
                            toIdx = (toIdx + 1) % accounts.size();
                        }

                        BankAccount from = accounts.get(fromIdx);
                        BankAccount to = accounts.get(toIdx);
                        long amount = 10 + random.nextInt(500);

                        if (transferSafe(from, to, amount)) {
                            successfulTransfers.incrementAndGet();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // Старт
        boolean completed = finishLatch.await(30, TimeUnit.SECONDS); // Таймаут защиты от зависания
        executor.shutdown();

        long durationMs = System.currentTimeMillis() - startTime;
        long finalTotalBalance = accounts.stream().mapToLong(BankAccount::getBalance).sum();
        boolean balancePreserved = (initialTotalBalance == finalTotalBalance);

        log.info("Стресс-тест завершён за {} мс. Успешных переводов: {}. Баланс сохранён: {}",
                durationMs, successfulTransfers.get(), balancePreserved);

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("completedWithoutDeadlock", completed);
        report.put("threads", threadCount);
        report.put("transfersPerThread", transfersPerThread);
        report.put("totalAttempted", threadCount * transfersPerThread);
        report.put("successfulTransfers", successfulTransfers.get());
        report.put("initialTotalBalance", initialTotalBalance);
        report.put("finalTotalBalance", finalTotalBalance);
        report.put("balancePreserved", balancePreserved);
        report.put("durationMs", durationMs);
        report.put("itemsCollectedInDataCollector", dataCollector.getProcessedCount());

        return report;
    }

    /**
     * Сравнение производительности и корректности:
     * Синхронизированный DataCollector vs Несинхронизированный счётчик (гонка данных).
     */
    public Map<String, Object> compareSynchronizedVsUnsynchronized(int threads, int incrementsPerThread) throws InterruptedException {
        int expectedTotal = threads * incrementsPerThread;

        // 1. Несинхронизированный счетчик (демонстрация Race Condition)
        class UnsafeCounter {
            int count = 0;
            void inc() { count++; } // НЕСИНХРОНИЗИРОВАНО: операция чтения-модификации-записи
        }
        UnsafeCounter unsafeCounter = new UnsafeCounter();

        ExecutorService execUnsafe = Executors.newFixedThreadPool(threads);
        CountDownLatch latchUnsafe = new CountDownLatch(threads);
        long startUnsafe = System.nanoTime();

        for (int i = 0; i < threads; i++) {
            execUnsafe.submit(() -> {
                for (int j = 0; j < incrementsPerThread; j++) {
                    unsafeCounter.inc();
                }
                latchUnsafe.countDown();
            });
        }
        latchUnsafe.await();
        long durationUnsafeMs = (System.nanoTime() - startUnsafe) / 1_000_000;
        execUnsafe.shutdown();

        // 2. Синхронизированный DataCollector (корректная атомарная синхронизация)
        DataCollector syncCollector = new DataCollector();
        ExecutorService execSync = Executors.newFixedThreadPool(threads);
        CountDownLatch latchSync = new CountDownLatch(threads);
        long startSync = System.nanoTime();

        for (int i = 0; i < threads; i++) {
            final int thId = i;
            execSync.submit(() -> {
                for (int j = 0; j < incrementsPerThread; j++) {
                    syncCollector.collectItem(new Item("KEY-" + thId + "-" + j, "Item", 1));
                }
                latchSync.countDown();
            });
        }
        latchSync.await();
        long durationSyncMs = (System.nanoTime() - startSync) / 1_000_000;
        execSync.shutdown();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("expectedTotal", expectedTotal);
        result.put("unsafeCount", unsafeCounter.count);
        result.put("unsafeDataLost", expectedTotal - unsafeCounter.count);
        result.put("unsafeDurationMs", durationUnsafeMs);
        result.put("syncCount", syncCollector.getProcessedCount());
        result.put("syncDataLost", expectedTotal - syncCollector.getProcessedCount());
        result.put("syncDurationMs", durationSyncMs);

        return result;
    }

    /**
     * Демонстрация wait() и notifyAll():
     * Поток-покупатель пытается списать средства при недостаточном балансе и засыпает через wait().
     * Поток-пополнитель вносит средства через deposit() и будит покупателя через notifyAll().
     */
    public Map<String, Object> runWaitNotifyDemo() throws InterruptedException {
        BankAccount account = new BankAccount(201, "Investor", 1_000);
        List<String> eventLog = new CopyOnWriteArrayList<>();

        eventLog.add(String.format("Начальный баланс аккаунта: %d руб.", account.getBalance()));

        // Поток списания: хочет списать 5000 руб, но на счете только 1000
        Thread withdrawThread = new Thread(() -> {
            try {
                eventLog.add("Поток списания: попытка списать 5000 руб (денег недостаточно, вход в wait)...");
                account.withdrawWithWait(5_000, 3_000);
                eventLog.add(String.format("Поток списания: успешно списал 5000 руб! Остаток: %d руб.", account.getBalance()));
            } catch (Exception e) {
                eventLog.add("Поток списания: ошибка - " + e.getMessage());
            }
        }, "WithdrawWorker");

        // Поток пополнения: через 300 мс вносит 6000 руб и вызывает notifyAll
        Thread depositThread = new Thread(() -> {
            try {
                Thread.sleep(300);
                eventLog.add("Поток пополнения: вносит на счет 6000 руб (вызов deposit + notifyAll)...");
                account.deposit(6_000);
            } catch (InterruptedException ignored) {}
        }, "DepositWorker");

        withdrawThread.start();
        depositThread.start();

        withdrawThread.join();
        depositThread.join();

        eventLog.add(String.format("Итоговый баланс аккаунта: %d руб.", account.getBalance()));

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("finalBalance", account.getBalance());
        res.put("log", eventLog);
        return res;
    }
}
