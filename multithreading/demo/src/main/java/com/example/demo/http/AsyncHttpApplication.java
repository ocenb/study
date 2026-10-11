package com.example.demo.http;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Консольный вход: URL аргументами либо UTF-8 файл (по одному URL на строку). */
public final class AsyncHttpApplication {
    private AsyncHttpApplication() {
    }

    public static List<String> readUrls(Path path) throws IOException {
        return Files.readAllLines(path, StandardCharsets.UTF_8).stream()
                .map(String::strip)
                .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                .toList();
    }

    public static void print(AsyncHttpService.BatchResult batch, PrintStream output) {
        output.println("№ | HTTP | Время, мс | URL | Ошибка");
        int index = 0;
        for (AsyncHttpService.RequestResult result : batch.results()) {
            output.printf(Locale.ROOT, "%d | %s | %.3f | %s | %s%n", ++index,
                    result.statusCode() == null ? "—" : result.statusCode(),
                    result.millis(), result.url(), result.error());
        }
        AsyncHttpService.Summary summary = batch.summary();
        output.printf(Locale.ROOT, "Всего: %d; 2xx: %d; HTTP не-2xx: %d; ошибки запросов: %d%n",
                summary.total(), summary.successful(), summary.httpErrors(), summary.requestErrors());
        output.printf(Locale.ROOT, "Длительность запроса, мс: средняя=%.3f; min=%.3f; max=%.3f%n",
                summary.averageMillis(), summary.minMillis(), summary.maxMillis());
        output.printf(Locale.ROOT, "Общее время партии: %.3f мс%n", summary.wallMillis());
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.out.println("Запуск: AsyncHttpApplication URL [URL ...] или --file urls-http.txt");
            return;
        }
        List<String> urls;
        if (args[0].equals("--file")) {
            if (args.length != 2) {
                throw new IllegalArgumentException("После --file нужен один путь к UTF-8 файлу");
            }
            urls = readUrls(Path.of(args[1]));
        } else {
            urls = Arrays.asList(args);
        }
        AsyncHttpService service = new AsyncHttpService();
        try (service) {
            System.out.println("ThreadPoolExecutor: core=4; max=8; queue=64; keepAlive=30s; CallerRunsPolicy");
            print(service.fetchAll(urls), System.out);
        }
        System.out.println("Состояние пула после закрытия: " + service.poolSnapshot());
    }
}
