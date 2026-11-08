package com.example.vacancyparser;

import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

record VacancyLink(String id) {
    private static final String HOST = "career.habr.com";
    private static final Pattern PATH = Pattern.compile("/vacancies/([1-9][0-9]{0,19})/?");

    static VacancyLink parse(String input) {
        if (input == null) {
            throw new IllegalArgumentException("Ожидается HTTPS-ссылка на вакансию Хабр Карьеры");
        }
        URI uri = URI.create(input.strip());
        if (!"https".equalsIgnoreCase(uri.getScheme()) || !HOST.equalsIgnoreCase(uri.getHost())
                || uri.getUserInfo() != null || (uri.getPort() != -1 && uri.getPort() != 443)) {
            throw new IllegalArgumentException("Ожидается HTTPS-ссылка на вакансию Хабр Карьеры");
        }
        Matcher path = PATH.matcher(uri.getRawPath());
        if (!path.matches()) {
            throw new IllegalArgumentException("Ожидается адрес вакансии вида https://career.habr.com/vacancies/123");
        }
        return new VacancyLink(path.group(1));
    }

    String url() {
        return "https://" + HOST + "/vacancies/" + id;
    }
}
