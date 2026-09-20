package com.example.demo.model;

/**
 * DTO с информацией об активном потоке.
 */
public class ThreadInfoDto {
    private long id;
    private String name;
    private String state;
    private boolean alive;
    private boolean daemon;
    private int priority;
    private String threadGroup;

    public ThreadInfoDto() {
    }

    public ThreadInfoDto(long id, String name, String state, boolean alive, boolean daemon, int priority, String threadGroup) {
        this.id = id;
        this.name = name;
        this.state = state;
        this.alive = alive;
        this.daemon = daemon;
        this.priority = priority;
        this.threadGroup = threadGroup;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public boolean isAlive() {
        return alive;
    }

    public void setAlive(boolean alive) {
        this.alive = alive;
    }

    public boolean isDaemon() {
        return daemon;
    }

    public void setDaemon(boolean daemon) {
        this.daemon = daemon;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public String getThreadGroup() {
        return threadGroup;
    }

    public void setThreadGroup(String threadGroup) {
        this.threadGroup = threadGroup;
    }

    @Override
    public String toString() {
        return String.format("Thread[id=%d, name='%s', state=%s, priority=%d, daemon=%s, group=%s]",
                id, name, state, priority, daemon, threadGroup);
    }
}
