package com.example.demo.taskqueue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public final class LocalUrlTaskDemo {
    private LocalUrlTaskDemo() {
    }

    public static void main(String[] args) throws Exception {
        try (LocalPageServer pages = new LocalPageServer()) {
            List<String> urls = new ArrayList<>();
            for (int i = 1; i <= 12; i++) {
                urls.add(pages.url("/page/" + i));
            }
            urls.add(pages.url("/page/1")); // Повторный URL — самостоятельная задача.
            urls.add(pages.url("/missing"));
            urls.add(pages.url("/notfound"));
            urls.add(pages.url("/slow"));
            urls.add("not a URL");
            System.out.println("Локальный HTTP; Producer=3; Consumer=4; queue=2; HTTP timeout=300ms");
            UrlTaskServer server = new UrlTaskServer(3, 4, 2, new HttpPageLoader(Duration.ofMillis(300)));
            UrlTaskServer.BatchResult result = server.run(urls);
            UrlTaskApplication.print(result, System.out);
            long successful = result.results().stream().filter(UrlTaskServer.Result::successful).count();
            if (result.results().size() != 17 || successful != 14
                    || !result.producersTerminated() || !result.consumersTerminated()) {
                throw new IllegalStateException("Неверные результаты локальной демонстрации");
            }
        }
    }
}
