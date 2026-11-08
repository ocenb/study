package com.example.vacancyparser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
class HabrVacancyParser {
    private static final Pattern REQUIREMENT_HEADING = Pattern.compile(
            "(?:основные )?требования(?: к кандидату)?|(?:наши )?ожидания(?: от кандидата)?"
                    + "|(?:мы )?(?:ждем|ожидаем) от (?:тебя|вас)|необходимые навыки");

    private final RestTemplate http;
    private final ObjectMapper json;

    HabrVacancyParser(RestTemplate http, ObjectMapper json) {
        this.http = http;
        this.json = json;
    }

    Vacancy fetch(VacancyLink link) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.TEXT_HTML));
        headers.set(HttpHeaders.USER_AGENT, "StudyVacancyParser/1.0");
        try {
            ResponseEntity<byte[]> response = http.exchange(URI.create(link.url()), HttpMethod.GET,
                    new HttpEntity<>(headers), byte[].class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new SourceException("Хабр Карьера не вернула страницу вакансии");
            }
            try (var body = new ByteArrayInputStream(response.getBody())) {
                return parse(link, Jsoup.parse(body, null, link.url()));
            }
        } catch (HttpStatusCodeException exception) {
            throw new SourceException("Хабр Карьера вернула HTTP " + exception.getStatusCode().value());
        } catch (RestClientException exception) {
            throw new SourceException("Не удалось загрузить страницу Хабр Карьеры");
        } catch (IOException | DateTimeParseException exception) {
            throw new SourceException("Не удалось разобрать данные вакансии Хабр Карьеры");
        }
    }

    private Vacancy parse(VacancyLink link, Document html) throws IOException {
        JsonNode posting = jobPosting(html);
        String title = text(posting.path("title"));
        String company = text(posting.path("hiringOrganization").path("name"));
        String date = text(posting.path("datePosted"));
        if (title == null || company == null || date == null) {
            throw new SourceException("На странице отсутствуют основные данные вакансии");
        }

        String salary = html.select(".vacancy-header__salary .basic-salary").text();
        return new Vacancy(link.id(), link.url(), title, company, nonBlank(salary), requirements(html),
                cities(posting.path("jobLocation")), LocalDate.parse(date));
    }

    private String requirements(Document html) {
        List<String> sections = new ArrayList<>();
        html.select(".content-section")
                .stream()
                .filter(section -> section.select("h2").text().equalsIgnoreCase("Требования"))
                .findFirst()
                .map(section -> String.join(", ", section.select(".vacancy-meta .basic-chip").eachText()))
                .filter(skills -> !skills.isBlank())
                .ifPresent(sections::add);

        Element description = html.selectFirst(".vacancy-description__text");
        if (description != null) {
            String details = detailedRequirements(description);
            if (!details.isBlank()) {
                sections.add(details);
            }
        }
        return nonBlank(String.join("\n\n", sections));
    }

    private String detailedRequirements(Element description) {
        List<String> lines = new ArrayList<>();
        boolean collecting = false;
        List<Element> blocks = description.children().stream().flatMap(this::descriptionBlocks).toList();
        for (Element block : blocks) {
            if (isHeading(block)) {
                String heading = block.text().toLowerCase(Locale.ROOT).replace('ё', 'е');
                heading = heading.endsWith(":") ? heading.substring(0, heading.length() - 1).strip() : heading;
                collecting = REQUIREMENT_HEADING.matcher(heading).matches();
            } else if (collecting && block.is("ul, ol")) {
                for (Element item : block.children()) {
                    lines.add("- " + item.text());
                }
            } else if (collecting && block.hasText()) {
                lines.add(block.text());
            }
        }
        return String.join("\n", lines);
    }

    private Stream<Element> descriptionBlocks(Element element) {
        if (element.is("div, section")) {
            return element.children().stream().flatMap(this::descriptionBlocks);
        }
        return Stream.of(element);
    }

    private boolean isHeading(Element block) {
        return block.is("h1, h2, h3, h4, h5, h6")
                || (block.is("p") && block.ownText().isBlank() && block.childrenSize() == 1
                && block.child(0).is("strong, b"));
    }

    private JsonNode jobPosting(Document html) throws IOException {
        for (Element script : html.select("script[type=application/ld+json]")) {
            JsonNode data = json.readTree(script.data());
            if (data != null && "JobPosting".equals(data.path("@type").asText())) {
                return data;
            }
        }
        throw new SourceException("На странице нет данных активной вакансии. Возможно, она в архиве");
    }

    private String cities(JsonNode locations) {
        Stream<JsonNode> places = locations.isArray()
                ? StreamSupport.stream(locations.spliterator(), false) : Stream.of(locations);
        List<String> cities = places.map(place -> place.path("address"))
                .map(address -> text(address.isObject() ? address.path("addressLocality") : address))
                .filter(city -> city != null)
                .distinct()
                .toList();
        return nonBlank(String.join(", ", cities));
    }

    private String text(JsonNode value) {
        return value.isTextual() ? nonBlank(value.textValue().strip()) : null;
    }

    private String nonBlank(String value) {
        return value.isBlank() ? null : value;
    }

    static class SourceException extends RuntimeException {
        SourceException(String message) {
            super(message);
        }
    }
}
