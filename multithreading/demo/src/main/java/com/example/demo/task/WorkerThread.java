package com.example.demo.task;

/**
 * Класс потока, наследующийся напрямую от java.lang.Thread.
 * Выводит собственное имя и порядковый номер шага.
 */
public class WorkerThread extends Thread {

    private final int totalSteps;
    private final long delayMillis;

    public WorkerThread(String name, int totalSteps, long delayMillis) {
        super(name);
        this.totalSteps = totalSteps;
        this.delayMillis = delayMillis;
    }

    public WorkerThread(String name, int totalSteps) {
        this(name, totalSteps, 100);
    }

    @Override
    public void run() {
        String threadName = getName();
        for (int i = 1; i <= totalSteps; i++) {
            System.out.printf("[%s] Порядковый номер: %d%n", threadName, i);
            try {
                Thread.sleep(delayMillis);
            } catch (InterruptedException e) {
                System.out.printf("[%s] Поток был прерван на шаге: %d%n", threadName, i);
                interrupt();
                break;
            }
        }
    }
}
