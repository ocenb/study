package com.example.demo.bank.collector;

import com.example.demo.bank.model.Item;

import java.util.*;

/**
 * Класс для многопоточного сбора, дедупликации и агрегации данных.
 * Защищен от гонки данных (race condition) синхронизацией методов.
 * Поддерживает ожидание готовности данных через wait() и notifyAll().
 */
public class DataCollector {

    private int processedCount = 0;
    private final Set<String> processedKeys = new HashSet<>();
    private final List<Item> collectedItems = new ArrayList<>();

    /**
     * Добавляет элемент в общий список, если он еще не обрабатывался.
     * Защищен synchronized. При успешном добавлении вызывает notifyAll().
     */
    public synchronized boolean collectItem(Item item) {
        if (item == null || isAlreadyProcessed(item.getKey())) {
            return false;
        }

        processedKeys.add(item.getKey());
        collectedItems.add(item);
        incrementProcessed();

        // Уведомляем потоки, ожидающие поступления данных в коллектор
        notifyAll();
        return true;
    }

    /**
     * Увеличивает счётчик обработанных элементов.
     * Синхронизирован для предотвращения потерь инкрементов.
     */
    public synchronized void incrementProcessed() {
        processedCount++;
    }

    /**
     * Проверяет, обрабатывался ли элемент по ключу.
     */
    public synchronized boolean isAlreadyProcessed(String key) {
        if (key == null) return false;
        return processedKeys.contains(key);
    }

    /**
     * Ожидает, пока количество обработанных элементов не достигнет targetCount.
     * Использует цикл while с wait() для защиты от ложных пробуждений (spurious wakeups).
     */
    public synchronized void waitUntilProcessedCount(int targetCount, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (processedCount < targetCount) {
            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0) {
                break;
            }
            wait(remaining);
        }
    }

    public synchronized int getProcessedCount() {
        return processedCount;
    }

    public synchronized List<Item> getCollectedItems() {
        return new ArrayList<>(collectedItems);
    }

    public synchronized void clear() {
        processedCount = 0;
        processedKeys.clear();
        collectedItems.clear();
    }
}
