package com.example.demo.taskqueue;

import javax.swing.text.MutableAttributeSet;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.parser.ParserDelegator;
import java.io.IOException;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Реальная HTTP-загрузка со сроками ожидания, ограничением тела и разбором HTML. */
public final class HttpPageLoader implements UrlTaskServer.PageLoader {
    private static final int MAX_BYTES = 1_048_576;
    private static final Pattern CHARSET = Pattern.compile("charset\\s*=\\s*[\"']?([^\\s;\"']+)", Pattern.CASE_INSENSITIVE);
    private final int timeoutMillis;

    public HttpPageLoader(Duration timeout) {
        long millis = timeout.toMillis();
        if (millis < 1 || millis > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Таймаут: 1..Integer.MAX_VALUE миллисекунд");
        }
        timeoutMillis = (int) millis;
    }

    @Override
    public UrlTaskServer.PageData load(String url) throws IOException {
        URI uri = URI.create(url);
        if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null) {
            throw new IllegalArgumentException("Нужен абсолютный HTTP/HTTPS URL");
        }
        HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
        connection.setConnectTimeout(timeoutMillis);
        connection.setReadTimeout(timeoutMillis);
        connection.setRequestProperty("User-Agent", "Study-BlockingQueue-Demo/1.0");
        try {
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                return new UrlTaskServer.PageData(status, "");
            }
            byte[] body;
            try (var input = connection.getInputStream()) {
                body = input.readNBytes(MAX_BYTES + 1);
            }
            if (body.length > MAX_BYTES) {
                throw new IOException("Размер страницы превышает 1 MiB");
            }
            Charset charset = StandardCharsets.UTF_8;
            String contentType = connection.getContentType();
            if (contentType != null) {
                Matcher matcher = CHARSET.matcher(contentType);
                if (matcher.find()) {
                    charset = Charset.forName(matcher.group(1));
                }
            }
            return new UrlTaskServer.PageData(status, extractTitle(new String(body, charset)));
        } finally {
            connection.disconnect();
        }
    }

    static String extractTitle(String html) throws IOException {
        StringBuilder title = new StringBuilder();
        new ParserDelegator().parse(new StringReader(html), new HTMLEditorKit.ParserCallback() {
            private boolean inTitle;
            private boolean found;

            @Override
            public void handleStartTag(HTML.Tag tag, MutableAttributeSet attributes, int position) {
                if (tag == HTML.Tag.TITLE && !found) {
                    inTitle = true;
                    found = true;
                }
            }

            @Override
            public void handleEndTag(HTML.Tag tag, int position) {
                if (tag == HTML.Tag.TITLE) {
                    inTitle = false;
                }
            }

            @Override
            public void handleText(char[] data, int position) {
                if (inTitle) {
                    title.append(data);
                }
            }
        }, true);
        return title.toString().replace('\u00a0', ' ').strip().replaceAll("\\s+", " ");
    }
}
