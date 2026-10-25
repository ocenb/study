package com.example.demo.events;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** Четыре производителя, четыре обработчика и два вида подписчиков. */
public final class NotificationsApplication {
    private NotificationsApplication() {
    }

    private static NotificationEvent event(int number) {
        return new NotificationEvent("evt-" + number, number % 2 == 0 ? "order.created" : "user.signup",
                "Демонстрационное событие #" + number, Instant.now());
    }

    public static void main(String[] args) throws Exception {
        ConcurrentLinkedQueue<String> audit = new ConcurrentLinkedQueue<>();
        ConcurrentLinkedQueue<String> notifications = new ConcurrentLinkedQueue<>();
        NotificationService service = new NotificationService(4, 2_048);
        try (service) {
            NotificationService.Subscriber auditSubscriber = event -> audit.add(event.id());
            service.subscribe("order.created", auditSubscriber);
            service.subscribe("user.signup", auditSubscriber);
            service.subscribe("order.created", event -> {
                Thread.sleep(1);
                if (Integer.parseInt(event.id().substring(4)) % 100 == 0) {
                    throw new IOException("Имитация недоступного канала уведомлений");
                }
                notifications.add(event.id());
            });
            ExecutorService producers = Executors.newFixedThreadPool(4);
            CountDownLatch start = new CountDownLatch(1);
            try {
                List<Future<?>> futures = new ArrayList<>();
                for (int producer = 0; producer < 4; producer++) {
                    int base = producer * 250;
                    futures.add(producers.submit(() -> {
                        start.await();
                        for (int i = 0; i < 250; i++) {
                            if (service.publish(event(base + i)) != NotificationService.PublishResult.ACCEPTED) {
                                throw new IllegalStateException("Первичная публикация отклонена");
                            }
                        }
                        for (int i = 0; i < 5; i++) {
                            service.publish(event(base));
                        }
                        return null;
                    }));
                }
                start.countDown();
                for (Future<?> future : futures) {
                    future.get(5, TimeUnit.SECONDS);
                }
            } finally {
                start.countDown();
                producers.shutdownNow();
                if (!producers.awaitTermination(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Производители не завершились");
                }
            }
        }
        System.out.println("Производителей=4; обработчиков=4; ёмкость очереди=2048");
        System.out.println("Итог: " + service.stats());
        System.out.println("Событий в журнале аудита: " + audit.size());
        System.out.println("Доставленных уведомлений: " + notifications.size());
        System.out.println("Ошибок в журнале: " + service.failures().size());
        System.out.println("evt-0: " + service.status("evt-0").orElseThrow());
        System.out.println("evt-1: " + service.status("evt-1").orElseThrow());
        if (service.stats().processed() != 1_000 || service.stats().duplicates() != 20
                || service.stats().failed() != 10 || audit.size() != 1_000 || notifications.size() != 490) {
            throw new IllegalStateException("Неверные результаты многопоточной обработки");
        }
    }
}
