package com.example.demo.completable;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/** Три независимых UTF-8 файла; разделитель ';', без кавычек и вложенных разделителей. */
public final class FileOrderDataSource implements OrderDataService.DataSource {
    private final Path directory;
    private final String failedRateId;

    public FileOrderDataSource(Path directory, String failedRateId) {
        this.directory = Objects.requireNonNull(directory);
        this.failedRateId = failedRateId;
    }

    private String[] row(String file, String id, int columns) throws IOException {
        for (String line : Files.readAllLines(directory.resolve(file), StandardCharsets.UTF_8)) {
            if (line.isBlank() || line.stripLeading().startsWith("#")) {
                continue;
            }
            String[] fields = line.split(";", -1);
            if (fields[0].strip().equals(id)) {
                if (fields.length != columns) {
                    throw new IOException("Неверное число полей: " + file + ", ID=" + id);
                }
                return fields;
            }
        }
        throw new IOException("Не найден ID=" + id + " в " + file);
    }

    @Override
    public OrderDataService.User user(String id) throws Exception {
        Thread.sleep(80);
        return new OrderDataService.User(row("users.csv", id, 2)[1]);
    }

    @Override
    public OrderDataService.Product product(String id) throws Exception {
        Thread.sleep(120);
        String[] fields = row("products.csv", id, 3);
        return new OrderDataService.Product(fields[1], new BigDecimal(fields[2].strip()));
    }

    @Override
    public BigDecimal rubPerUsd(String id) throws Exception {
        Thread.sleep(100);
        if (id.equals(failedRateId)) {
            throw new IOException("Имитация недоступного курса для " + id);
        }
        return new BigDecimal(row("rates.csv", id, 2)[1].strip());
    }
}
