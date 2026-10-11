package com.example.demo.atomics;

import java.util.concurrent.atomic.AtomicInteger;

/** Атомарная операция read-modify-write предотвращает потерю инкрементов. */
public final class AtomicCounter {
    private final AtomicInteger value = new AtomicInteger();

    public int increment() {
        return value.incrementAndGet();
    }

    public int get() {
        return value.get();
    }
}
