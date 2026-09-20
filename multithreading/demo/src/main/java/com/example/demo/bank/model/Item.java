package com.example.demo.bank.model;

/**
 * Элемент данных или транзакция для сбора в DataCollector.
 */
public class Item {
    private final String key;
    private final String description;
    private final long amount;
    private final long timestamp;

    public Item(String key, String description, long amount) {
        this.key = key;
        this.description = description;
        this.amount = amount;
        this.timestamp = System.currentTimeMillis();
    }

    public String getKey() {
        return key;
    }

    public String getDescription() {
        return description;
    }

    public long getAmount() {
        return amount;
    }

    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return String.format("Item[key='%s', desc='%s', amount=%d]", key, description, amount);
    }
}
