package com.example.demo.bank.model;

/**
 * Банковский аккаунт с потокобезопасными операциями изменения баланса,
 * поддержкой ожидания пополнения через wait() и уведомления через notifyAll().
 */
public class BankAccount {
    private final long id;
    private final String ownerName;
    private long balance;

    public BankAccount(long id, String ownerName, long initialBalance) {
        this.id = id;
        this.ownerName = ownerName;
        this.balance = initialBalance;
    }

    public long getId() {
        return id;
    }

    public String getOwnerName() {
        return ownerName;
    }

    /**
     * Потокобезопасное получение текущего баланса.
     */
    public synchronized long getBalance() {
        return balance;
    }

    /**
     * Пополнение баланса.
     * После пополнения вызывается notifyAll(), чтобы разбудить потоки,
     * ожидающие средств для списания.
     */
    public synchronized void deposit(long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Сумма пополнения должна быть положительной: " + amount);
        }
        balance += amount;
        notifyAll();
    }

    /**
     * Мгновенное списание средств (без ожидания).
     */
    public synchronized boolean withdraw(long amount) {
        if (amount <= 0 || balance < amount) {
            return false;
        }
        balance -= amount;
        return true;
    }

    /**
     * Списание средств с ожиданием через wait(), если на балансе недостаточно средств.
     * Поток блокируется и ждет, пока другой поток не сделает deposit() и не вызовет notifyAll().
     */
    public synchronized void withdrawWithWait(long amount, long timeoutMs) throws InterruptedException {
        if (amount <= 0) {
            throw new IllegalArgumentException("Сумма списания должна быть положительной: " + amount);
        }

        long deadline = System.currentTimeMillis() + timeoutMs;
        while (balance < amount) {
            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0) {
                throw new IllegalStateException(String.format(
                        "Таймаут ожидания средств на аккаунте %d (%s). Требовалось: %d, доступно: %d",
                        id, ownerName, amount, balance));
            }
            wait(remaining);
        }
        balance -= amount;
    }

    @Override
    public String toString() {
        return String.format("BankAccount[id=%d, owner='%s', balance=%d]", id, ownerName, balance);
    }
}
