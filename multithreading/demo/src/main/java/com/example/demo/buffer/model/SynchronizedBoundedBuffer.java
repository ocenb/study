package com.example.demo.buffer.model;

/**
 * Классическая реализация ограниченного буфера на базе synchronized и wait()/notifyAll().
 * Используется для прямого сравнительного бенчмаркинга с ReentrantLock + Condition.
 */
public class SynchronizedBoundedBuffer<T> {

    private final Object[] items;
    private int putIndex = 0;
    private int takeIndex = 0;
    private int count = 0;

    public SynchronizedBoundedBuffer(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Емкость буфера должна быть больше нуля");
        }
        this.items = new Object[capacity];
    }

    public synchronized void put(T item) throws InterruptedException {
        if (item == null) {
            throw new NullPointerException("Элемент не может быть null");
        }

        while (count == items.length) {
            wait();
        }

        items[putIndex] = item;
        if (++putIndex == items.length) {
            putIndex = 0;
        }
        count++;

        // Вынуждены будить ВСЕ потоки (и продюсеров, и консьюмеров),
        // так как у монитора только одна очередь ожидания (wait-set)
        notifyAll();
    }

    @SuppressWarnings("unchecked")
    public synchronized T take() throws InterruptedException {
        while (count == 0) {
            wait();
        }

        T item = (T) items[takeIndex];
        items[takeIndex] = null;
        if (++takeIndex == items.length) {
            takeIndex = 0;
        }
        count--;

        // Вынуждены будить ВСЕ потоки
        notifyAll();
        return item;
    }

    public synchronized int size() {
        return count;
    }
}
