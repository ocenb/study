package com.example.demo.taskqueue;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class UrlTaskApplication {
    private UrlTaskApplication() {
    }

    public static List<String> readUrls(Path file) throws java.io.IOException {
        return Files.readAllLines(file, StandardCharsets.UTF_8).stream().map(String::strip)
                .filter(line -> !line.isEmpty() && !line.startsWith("#")).toList();
    }

    public static void print(UrlTaskServer.BatchResult batch, PrintStream out) {
        out.println("№ | HTTP | Время, мс | Producer -> Consumer | URL | Title | Ошибка");
        for (UrlTaskServer.Result result : batch.results()) {
            out.printf(Locale.ROOT, "%d | %s | %.3f | %s -> %s | %s | %s | %s%n", result.index() + 1,
                    result.statusCode() == null ? "—" : result.statusCode(), result.elapsedNanos() / 1_000_000.0,
                    result.producer(), result.consumer(), result.url(),
                    result.title().isEmpty() ? "—" : result.title(), result.error());
        }
        long successful = batch.results().stream().filter(UrlTaskServer.Result::successful).count();
        out.printf(Locale.ROOT, "Всего=%d; успешно=%d; ошибок=%d; время партии=%.3f ms%n",
                batch.results().size(), successful, batch.results().size() - successful,
                batch.elapsedNanos() / 1_000_000.0);
        out.println("Producer pool terminated=" + batch.producersTerminated()
                + "; Consumer pool terminated=" + batch.consumersTerminated());
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.out.println("Запуск: UrlTaskApplication URL [URL ...] либо --file urls-queue.txt");
            return;
        }
        List<String> urls;
        if (args[0].equals("--file")) {
            if (args.length != 2) { throw new IllegalArgumentException("После --file нужен один путь"); }
            urls = readUrls(Path.of(args[1]));
        } else {
            urls = Arrays.asList(args);
        }
        System.out.println("Producer=3; Consumer=4; ArrayBlockingQueue capacity=8; HTTP timeout=3s");
        UrlTaskServer server = new UrlTaskServer(3, 4, 8, new HttpPageLoader(Duration.ofSeconds(3)));
        print(server.run(urls), System.out);
    }
}
