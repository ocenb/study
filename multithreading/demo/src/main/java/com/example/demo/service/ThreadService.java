package com.example.demo.service;

import com.example.demo.model.ThreadInfoDto;
import com.example.demo.task.Task;
import com.example.demo.task.WorkerThread;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Сервис для управления потоками и получения информации о них.
 */
@Service
public class ThreadService {

    private static final Logger log = LoggerFactory.getLogger(ThreadService.class);

    /**
     * Создает и запускает потоки через Thread и Runnable,
     * а также выводит информацию об активных потоках в консоль.
     */
    public void executeThreadsDemo() {
        log.info("=== Запуск демонстрации многопоточности ===");

        // 1. Создание потока через наследование класса Thread:
        // Имя потока задается понятным: «CounterWorker»
        WorkerThread counterWorker = new WorkerThread("CounterWorker", 5, 100);

        // 2. Создание потока через реализацию интерфейса Runnable:
        // Задача передается в конструктор Thread, имя потока: «LoggerThread»
        Task loggerTask = new Task(5, 100);
        Thread loggerThread = new Thread(loggerTask, "LoggerThread");

        log.info("Запуск потока CounterWorker (extends Thread)...");
        counterWorker.start();

        log.info("Запуск потока LoggerThread (implements Runnable)...");
        loggerThread.start();

        // Небольшая задержка, чтобы потоки начали работу перед выводом списка активных потоков
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Вывод списка всех активных потоков
        printActiveThreads();

        // Ожидание завершения потоков
        try {
            counterWorker.join();
            loggerThread.join();
            log.info("=== Потоки успешно завершили свою работу ===");
        } catch (InterruptedException e) {
            log.error("Основной поток был прерван во время ожидания", e);
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Возвращает список всех активных потоков в системе.
     */
    @SuppressWarnings("deprecation")
    public List<ThreadInfoDto> getActiveThreads() {
        Set<Thread> threads = Thread.getAllStackTraces().keySet();
        return threads.stream()
                .map(t -> new ThreadInfoDto(
                        t.getId(),
                        t.getName(),
                        t.getState().name(),
                        t.isAlive(),
                        t.isDaemon(),
                        t.getPriority(),
                        t.getThreadGroup() != null ? t.getThreadGroup().getName() : "N/A"
                ))
                .sorted(Comparator.comparing(ThreadInfoDto::getName))
                .collect(Collectors.toList());
    }

    /**
     * Выводит форматированный список всех активных потоков в консоль.
     */
    @SuppressWarnings("deprecation")
    public void printActiveThreads() {
        System.out.println("\n============================= СПИСОК АКТИВНЫХ ПОТОКОВ =============================");
        System.out.printf("%-6s | %-30s | %-14s | %-8s | %-8s | %-15s%n",
                "ID", "ИМЯ ПОТОКА", "СОСТОЯНИЕ", "ПРИОРИТЕТ", "ДЕМОН", "ГРУППА");
        System.out.println("----------------------------------------------------------------------------------");

        for (Thread t : Thread.getAllStackTraces().keySet()) {
            System.out.printf("%-6d | %-30s | %-14s | %-9d | %-8s | %-15s%n",
                    t.getId(),
                    t.getName(),
                    t.getState(),
                    t.getPriority(),
                    t.isDaemon() ? "Да" : "Нет",
                    t.getThreadGroup() != null ? t.getThreadGroup().getName() : "N/A");
        }
        System.out.println("==================================================================================\n");
    }
}
