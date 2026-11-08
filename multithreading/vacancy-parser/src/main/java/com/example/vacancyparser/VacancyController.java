package com.example.vacancyparser;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
class VacancyController {
    private final VacancyRepository repository;

    VacancyController(VacancyRepository repository) {
        this.repository = repository;
    }

    @PostMapping("/post")
    @ResponseStatus(HttpStatus.ACCEPTED)
    List<ParseJob> enqueue(@RequestBody VacancyRequest request) {
        if (request.urls() == null || request.urls().isEmpty() || request.urls().size() > 20) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Передайте от 1 до 20 ссылок в поле urls");
        }
        List<VacancyLink> links;
        try {
            links = request.urls().stream().map(VacancyLink::parse).toList();
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
        return repository.enqueue(links);
    }

    @GetMapping("/answer/{id}")
    ParseJob job(@PathVariable UUID id) {
        return repository.job(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Заявка с таким ID не найдена"));
    }

    @GetMapping("/answer")
    List<Vacancy> answer(@RequestParam(defaultValue = "publishedAt") String sortBy,
                         @RequestParam(defaultValue = "desc") String direction) {
        boolean descending = switch (direction) {
            case "desc" -> true;
            case "asc" -> false;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "direction должен быть asc или desc");
        };
        Comparator<String> textOrder = descending ? String.CASE_INSENSITIVE_ORDER.reversed()
                : String.CASE_INSENSITIVE_ORDER;
        Comparator<LocalDate> dateOrder = descending ? Comparator.reverseOrder() : Comparator.naturalOrder();
        Comparator<Vacancy> order = switch (sortBy) {
            case "publishedAt" -> Comparator.comparing(Vacancy::publishedAt, dateOrder);
            case "title" -> Comparator.comparing(Vacancy::title, textOrder);
            case "city" -> Comparator.comparing(Vacancy::city, Comparator.nullsLast(textOrder));
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "sortBy должен быть publishedAt, title или city");
        };
        return repository.vacancies().parallelStream().sorted(order.thenComparing(Vacancy::id)).toList();
    }

    record VacancyRequest(List<String> urls) {}
}
