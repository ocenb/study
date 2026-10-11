package com.example.demo.http;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Воспроизводимый запуск на 20 локальных URL для отчёта. */
public final class LocalHttpDemo {
    private LocalHttpDemo() {
    }

    public static void main(String[] args) throws Exception {
        AsyncHttpService service = new AsyncHttpService(new AsyncHttpService.PoolSettings(4, 8, 64, 30),
                Duration.ofSeconds(1), Duration.ofMillis(300));
        try (LocalHttpFixture server = new LocalHttpFixture(); service) {
            List<String> urls = new ArrayList<>();
            for (int i = 1; i <= 14; i++) {
                urls.add(server.url("/status/200?i=" + i));
            }
            urls.add(server.url("/status/201?i=15"));
            urls.add(server.url("/status/201?i=16"));
            urls.add(server.url("/status/404?i=17"));
            urls.add(server.url("/status/404?i=18"));
            urls.add(server.url("/status/503?i=19"));
            urls.add(server.url("/slow-headers?i=20"));
            System.out.println("Локальный HTTP-сервер; 20 URL; requestTimeout=300ms");
            AsyncHttpApplication.print(service.fetchAll(urls), System.out);
            System.out.println("Максимум одновременно активных обработчиков сервера: " + server.maximumActive());
        }
        System.out.println("Состояние пула после закрытия: " + service.poolSnapshot());
    }
}
