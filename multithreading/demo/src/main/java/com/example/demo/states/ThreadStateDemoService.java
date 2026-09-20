package com.example.demo.states;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Сервис, демонстрирующий все 6 состояний потоков в Java (Thread.State):
 * NEW, RUNNABLE, WAITING, BLOCKED, TIMED_WAITING, TERMINATED.
 *
 * Содержит 3 потока, каждый из которых проходит через различные состояния:
 * 1. TimedWaitingWorker -> NEW -> RUNNABLE -> TIMED_WAITING -> RUNNABLE -> TERMINATED
 * 2. WaitingWorker      -> NEW -> RUNNABLE -> WAITING -> RUNNABLE -> TERMINATED
 * 3. BlockedWorker      -> NEW -> RUNNABLE -> BLOCKED -> RUNNABLE -> TERMINATED
 *
 * Также включает поток-наблюдатель (ThreadStateObserver), непрерывно отслеживающий
 * и выводящий в консоль любые изменения состояний исследуемых потоков.
 */
@Service
public class ThreadStateDemoService {

    private static final Logger log = LoggerFactory.getLogger(ThreadStateDemoService.class);

    private final Object waitLock = new Object();
    private final Object blockLock = new Object();

    private final List<StateTransitionEvent> eventHistory = new CopyOnWriteArrayList<>();

    public List<StateTransitionEvent> getEventHistory() {
        return Collections.unmodifiableList(eventHistory);
    }

    /**
     * Запуск демонстрации изменения состояний потоков с выводом в консоль.
     */
    public List<StateTransitionEvent> runStateDemo() {
        eventHistory.clear();

        System.out.println("\n==========================================================================================");
        System.out.println("            ДЕМОНСТРАЦИЯ СОСТОЯНИЙ ПОТОКОВ (NEW, RUNNABLE, WAITING, BLOCKED, TIMED_WAITING, TERMINATED)");
        System.out.println("==========================================================================================\n");

        AtomicBoolean canProceedWait = new AtomicBoolean(false);

        // 1. Создание Потока 1: Демонстрация TIMED_WAITING (через Thread.sleep)
        Thread timedWaitingWorker = new Thread(() -> {
            logEvent("TimedWaitingWorker", Thread.currentThread().getState(), "Поток начал выполнение (RUNNABLE)");
            try {
                // Имитация полезной работы
                busyWork(20);
                logEvent("TimedWaitingWorker", Thread.State.RUNNABLE, "Переходит в режим сна на 600 мс (будет TIMED_WAITING)");
                Thread.sleep(600);
                logEvent("TimedWaitingWorker", Thread.State.RUNNABLE, "Проснулся и завершает работу");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "TimedWaitingWorker");

        // 2. Создание Потока 2: Демонстрация WAITING (через Object.wait)
        Thread waitingWorker = new Thread(() -> {
            logEvent("WaitingWorker", Thread.currentThread().getState(), "Поток начал выполнение (RUNNABLE)");
            synchronized (waitLock) {
                try {
                    while (!canProceedWait.get()) {
                        logEvent("WaitingWorker", Thread.State.RUNNABLE, "Ожидает сигнала через waitLock.wait() (будет WAITING)");
                        waitLock.wait();
                    }
                    logEvent("WaitingWorker", Thread.State.RUNNABLE, "Получил сигнал notify, возобновил работу");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }, "WaitingWorker");

        // 3. Создание Потока 3: Демонстрация BLOCKED (через ожидание монитора synchronized)
        Thread blockedWorker = new Thread(() -> {
            logEvent("BlockedWorker", Thread.currentThread().getState(), "Поток начал выполнение (RUNNABLE), пытается захватить blockLock");
            synchronized (blockLock) {
                logEvent("BlockedWorker", Thread.State.RUNNABLE, "Успешно захватил blockLock и выполняет работу");
                busyWork(30);
            }
        }, "BlockedWorker");

        List<Thread> targetThreads = List.of(timedWaitingWorker, waitingWorker, blockedWorker);

        // Фиксация состояния NEW (до вызова start)
        for (Thread t : targetThreads) {
            logEvent(t.getName(), t.getState(), "Поток создан, но еще не запущен (NEW)");
        }
        printSnapshotTable("ФАЗА 1: Потоки созданы (состояние NEW)", targetThreads);

        // Запуск фонового потока-наблюдателя (Observer), отслеживающего смену состояний в реальном времени
        AtomicBoolean observing = new AtomicBoolean(true);
        Thread observerThread = new Thread(() -> {
            Map<String, Thread.State> lastKnownStates = new HashMap<>();
            for (Thread t : targetThreads) {
                lastKnownStates.put(t.getName(), t.getState());
            }

            while (observing.get()) {
                for (Thread t : targetThreads) {
                    Thread.State current = t.getState();
                    Thread.State previous = lastKnownStates.get(t.getName());
                    if (current != previous) {
                        lastKnownStates.put(t.getName(), current);
                        System.out.printf("  >>> [НАБЛЮДАТЕЛЬ] Поток '%s': %s -> %s%n",
                                t.getName(), previous, current);
                    }
                }
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }, "ThreadStateObserver");
        observerThread.setDaemon(true);
        observerThread.start();

        // ФАЗА 2: Захват блокировки для blockedWorker и запуск потоков
        synchronized (blockLock) {
            log.info("Главный поток захватил blockLock, сейчас запустит BlockedWorker...");
            blockedWorker.start();
            waitingWorker.start();
            timedWaitingWorker.start();

            // Ждем, пока потоки перейдут в свои специфические состояния
            waitForState(blockedWorker, Thread.State.BLOCKED, 1000);
            waitForState(waitingWorker, Thread.State.WAITING, 1000);
            waitForState(timedWaitingWorker, Thread.State.TIMED_WAITING, 1000);

            printSnapshotTable("ФАЗА 2: Потоки запущены и находятся в различных состояниях ожидания и блокировки", targetThreads);

            // Фиксация наблюдаемых состояний
            logEvent(timedWaitingWorker.getName(), timedWaitingWorker.getState(), "Поток заснул на время (TIMED_WAITING)");
            logEvent(waitingWorker.getName(), waitingWorker.getState(), "Поток ожидает внешнего сигнала (WAITING)");
            logEvent(blockedWorker.getName(), blockedWorker.getState(), "Поток ожидает освобождения монитора (BLOCKED)");

            log.info("Главный поток освобождает blockLock...");
        } // Здесь blockLock освобождается, blockedWorker должен выйти из BLOCKED

        // Ждем, пока blockedWorker захватит монитор и завершится
        try {
            blockedWorker.join(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // ФАЗА 3: Пробуждение waitingWorker
        synchronized (waitLock) {
            log.info("Главный поток отправляет notifyAll для waitingWorker...");
            canProceedWait.set(true);
            waitLock.notifyAll();
        }

        // Ждем завершения остальных потоков
        try {
            waitingWorker.join(2000);
            timedWaitingWorker.join(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        observing.set(false);

        // ФАЗА 4: Фиксация завершения потоков (TERMINATED)
        for (Thread t : targetThreads) {
            logEvent(t.getName(), t.getState(), "Поток завершил выполнение (TERMINATED)");
        }
        printSnapshotTable("ФАЗА 3: Все потоки завершили работу (TERMINATED)", targetThreads);

        System.out.println("==========================================================================================");
        System.out.println("                     ДЕМОНСТРАЦИЯ СОСТОЯНИЙ УСПЕШНО ЗАВЕРШЕНА");
        System.out.println("==========================================================================================\n");

        return new ArrayList<>(eventHistory);
    }

    private void busyWork(int iterations) {
        long sum = 0;
        for (int i = 0; i < iterations * 100_000; i++) {
            sum += i;
        }
        if (sum == 42) {
            System.out.print("");
        }
    }

    private void waitForState(Thread thread, Thread.State expectedState, long timeoutMs) {
        long start = System.currentTimeMillis();
        while (thread.getState() != expectedState && (System.currentTimeMillis() - start) < timeoutMs) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void logEvent(String threadName, Thread.State state, String description) {
        StateTransitionEvent event = new StateTransitionEvent(threadName, state.name(), description);
        eventHistory.add(event);
        System.out.println(" " + event);
    }

    private void printSnapshotTable(String phaseTitle, List<Thread> threads) {
        System.out.println("\n------------------------------------------------------------------------------------------");
        System.out.println(" " + phaseTitle);
        System.out.println("------------------------------------------------------------------------------------------");
        System.out.printf(" %-22s | %-16s | %-8s | %-10s%n", "ИМЯ ПОТОКА", "СОСТОЯНИЕ", "ALIVE", "DAEMON");
        System.out.println("------------------------------------------------------------------------------------------");
        for (Thread t : threads) {
            System.out.printf(" %-22s | %-16s | %-8s | %-10s%n",
                    t.getName(),
                    t.getState(),
                    t.isAlive() ? "Да" : "Нет",
                    t.isDaemon() ? "Да" : "Нет");
        }
        System.out.println("------------------------------------------------------------------------------------------\n");
    }
}
