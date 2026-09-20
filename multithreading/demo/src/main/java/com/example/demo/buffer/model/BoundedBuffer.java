package com.example.demo.buffer.model;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Потокобезопасный кольцевой буфер фиксированного размера (BoundedBuffer)
 * на базе явных блокировок java.util.concurrent.locks.ReentrantLock
 * и двух очередей ожидания java.util.concurrent.locks.Condition:
 * - notEmpty (сигнализирует потребителям о наличии данных)
 * - notFull (сигнализирует производителям о наличии свободного места).
 *
 * @param <T> тип хранимых элементов
 */
public class BoundedBuffer<T> {

    private final Object[] items;
    private int putIndex = 0;
    private int takeIndex = 0;
    private int count = 0;

    private final ReentrantLock lock;
    private final Condition notEmpty;
    private final Condition notFull;

    public BoundedBuffer(int capacity, boolean fair) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Емкость буфера должна быть больше нуля: " + capacity);
        }
        this.items = new Object[capacity];
        this.lock = new ReentrantLock(fair);
        this.notEmpty = lock.newCondition();
        this.notFull = lock.newCondition();
    }

    public BoundedBuffer(int capacity) {
        this(capacity, false);
    }

    /**
     * Добавление элемента в буфер (операция producer'а).
     * Если буфер полон, поток засыпает на условии notFull.await().
     */
    public void put(T item) throws InterruptedException {
        if (item == null) {
            throw new NullPointerException("Элемент не может быть null");
        }

        lock.lock();
        try {
            while (count == items.length) {
                notFull.await();
            }

            items[putIndex] = item;
            if (++putIndex == items.length) {
                putIndex = 0;
            }
            count++;

            // Точечное пробуждение ожидающего потребителя (consumer)
            notEmpty.signal();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Добавление элемента с ограничением по времени ожидания.
     */
    public boolean put(T item, long timeout, TimeUnit unit) throws InterruptedException {
        if (item == null) {
            throw new NullPointerException("Элемент не может быть null");
        }

        long nanos = unit.toNanos(timeout);
        lock.lockInterruptibly();
        try {
            while (count == items.length) {
                if (nanos <= 0L) {
                    return false;
                }
                nanos = notFull.awaitNanos(nanos);
            }

            items[putIndex] = item;
            if (++putIndex == items.length) {
                putIndex = 0;
            }
            count++;

            notEmpty.signal();
            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Извлечение элемента из буфера (операция consumer'а).
     * Если буфер пуст, поток засыпает на условии notEmpty.await().
     */
    @SuppressWarnings("unchecked")
    public T take() throws InterruptedException {
        lock.lock();
        try {
            while (count == 0) {
                notEmpty.await();
            }

            T item = (T) items[takeIndex];
            items[takeIndex] = null; // Очистка ссылки для сборщика мусора (GC)
            if (++takeIndex == items.length) {
                takeIndex = 0;
            }
            count--;

            // Точечное пробуждение ожидающего производителя (producer)
            notFull.signal();
            return item;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Извлечение элемента с ограничением по времени ожидания.
     */
    @SuppressWarnings("unchecked")
    public T take(long timeout, TimeUnit unit) throws InterruptedException {
        long nanos = unit.toNanos(timeout);
        lock.lockInterruptibly();
        try {
            while (count == 0) {
                if (nanos <= 0L) {
                    return null;
                }
                nanos = notEmpty.awaitNanos(nanos);
            }

            T item = (T) items[takeIndex];
            items[takeIndex] = null;
            if (++takeIndex == items.length) {
                takeIndex = 0;
            }
            count--;

            notFull.signal();
            return item;
        } finally {
            lock.unlock();
        }
    }

    public int size() {
        lock.lock();
        try {
            return count;
        } finally {
            lock.unlock();
        }
    }

    public int capacity() {
        return items.length;
    }

    public boolean isEmpty() {
        lock.lock();
        try {
            return count == 0;
        } finally {
            lock.unlock();
        }
    }

    public boolean isFull() {
        lock.lock();
        try {
            return count == items.length;
        } finally {
            lock.unlock();
        }
    }
}
