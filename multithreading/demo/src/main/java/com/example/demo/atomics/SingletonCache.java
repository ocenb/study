package com.example.demo.atomics;

import java.util.concurrent.atomic.AtomicReference;

/** Один экземпляр кэша на загрузчик классов; значение публикуется лениво через CAS. */
public final class SingletonCache {
    private static final SingletonCache INSTANCE = new SingletonCache();
    private final AtomicReference<String> value = new AtomicReference<>();

    private SingletonCache() {
    }

    public static SingletonCache getInstance() {
        return INSTANCE;
    }

    public String getOrCreate() {
        String existing = value.get();
        if (existing != null) {
            return existing;
        }
        // Отдельные объекты позволяют проверить идентичность ссылок при гонке.
        // Создание кандидата должно быть без побочных эффектов: проигравшие его отбросят.
        String candidate = new String("shared-cache-value");
        if (value.compareAndSet(null, candidate)) {
            return candidate;
        }
        // Сброса кэша нет, поэтому после проигранного CAS здесь всегда значение победителя.
        return value.get();
    }
}
