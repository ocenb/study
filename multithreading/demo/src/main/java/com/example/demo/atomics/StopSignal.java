package com.example.demo.atomics;

/** Одноразовый сигнал кооперативной остановки: видимость между потоками. */
public final class StopSignal {
    private volatile boolean stopped;

    public void stop() {
        stopped = true;
    }

    public boolean isStopped() {
        return stopped;
    }
}
