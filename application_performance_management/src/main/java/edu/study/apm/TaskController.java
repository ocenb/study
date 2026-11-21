package edu.study.apm;

import java.net.URI;
import java.util.List;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@Profile("jmeter")
@RestController
@RequestMapping("/api/tasks")
class TaskController {
    private static final int SEED_COUNT = 10_000;
    private final AtomicLong ids = new AtomicLong(SEED_COUNT);
    private final ConcurrentSkipListMap<Long, Task> tasks = new ConcurrentSkipListMap<>();

    TaskController() {
        for (long id = 1; id <= SEED_COUNT; id++) {
            var status = id % 2 == 0 ? Status.OPEN : Status.DONE;
            tasks.put(id, new Task(id, "Task " + id, status));
        }
    }

    @GetMapping
    List<Task> list(@RequestParam Status status,
                    @RequestParam(defaultValue = "10") int limit,
                    @RequestParam(defaultValue = "0") int offset) {
        if (limit < 1 || limit > 100 || offset < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid page");
        }
        return tasks.values().stream()
                .filter(task -> task.status() == status)
                .skip(offset)
                .limit(limit)
                .toList();
    }

    @GetMapping("/{id}")
    Task find(@PathVariable long id) {
        var task = tasks.get(id);
        if (task == null) {
            throw notFound();
        }
        return task;
    }

    @PostMapping
    ResponseEntity<Task> create(@RequestBody TaskInput input) {
        validate(input);
        long id = ids.incrementAndGet();
        var task = new Task(id, input.title(), input.status());
        tasks.put(id, task);
        return ResponseEntity.created(URI.create("/api/tasks/" + id)).body(task);
    }

    @PutMapping("/{id}")
    Task update(@PathVariable long id, @RequestBody TaskInput input) {
        validate(input);
        var task = new Task(id, input.title(), input.status());
        if (tasks.replace(id, task) == null) {
            throw notFound();
        }
        return task;
    }

    private static void validate(TaskInput input) {
        if (input.title() == null || input.title().isBlank() || input.title().length() > 120
                || input.status() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid task");
        }
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found");
    }

    enum Status { OPEN, DONE }

    record Task(long id, String title, Status status) {}

    record TaskInput(String title, Status status) {}
}
