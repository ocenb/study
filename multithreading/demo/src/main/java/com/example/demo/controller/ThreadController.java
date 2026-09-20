package com.example.demo.controller;

import com.example.demo.model.ThreadInfoDto;
import com.example.demo.service.ThreadService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * REST контроллер для управления и мониторинга потоков.
 */
@RestController
@RequestMapping("/api/threads")
public class ThreadController {

    private final ThreadService threadService;

    public ThreadController(ThreadService threadService) {
        this.threadService = threadService;
    }

    /**
     * Запуск демонстрации многопоточности.
     */
    @PostMapping("/run-demo")
    public ResponseEntity<Map<String, String>> runDemo() {
        threadService.executeThreadsDemo();
        return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Демонстрация потоков (CounterWorker и LoggerThread) успешно выполнена"
        ));
    }

    /**
     * Получение списка всех активных потоков.
     */
    @GetMapping("/active")
    public ResponseEntity<List<ThreadInfoDto>> getActiveThreads() {
        List<ThreadInfoDto> activeThreads = threadService.getActiveThreads();
        return ResponseEntity.ok(activeThreads);
    }
}
