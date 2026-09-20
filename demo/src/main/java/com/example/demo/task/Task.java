package com.example.demo.task;

/**
 * Класс задачи, реализующий интерфейс Runnable.
 * Выводит имя текущего потока и порядковый номер шага.
 */
public class Task implements Runnable {

    private final int totalSteps;
    private final long delayMillis;

    public Task(int totalSteps, long delayMillis) {
        this.totalSteps = totalSteps;
        this.delayMillis = delayMillis;
    }

    public Task(int totalSteps) {
        this(totalSteps, 100);
    }

    @Override
    public void run() {
        String threadName = Thread.currentThread().getName();
        for (int i = 1; i <= totalSteps; i++) {
            System.out.printf("[%s] Порядковый номер: %d%n", threadName, i);
            try {
                Thread.sleep(delayMillis);
            } catch (InterruptedException e) {
                System.out.printf("[%s] Поток был прерван на шаге: %d%n", threadName, i);
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
}
