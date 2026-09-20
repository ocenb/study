package com.example.demo.states;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * REST контроллер для демонстрации состояний потоков.
 */
@RestController
@RequestMapping("/api/thread-states")
public class ThreadStateController {

    private final ThreadStateDemoService stateDemoService;

    public ThreadStateController(ThreadStateDemoService stateDemoService) {
        this.stateDemoService = stateDemoService;
    }

    /**
     * Запустить полную демонстрацию состояний потоков и вернуть журнал переходов.
     */
    @PostMapping("/run")
    public ResponseEntity<Map<String, Object>> runStatesDemo() {
        List<StateTransitionEvent> events = stateDemoService.runStateDemo();
        return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Демонстрация состояний потоков успешно завершена",
                "eventsCount", events.size(),
                "events", events
        ));
    }

    /**
     * Получить историю переходов состояний последнего запуска.
     */
    @GetMapping("/history")
    public ResponseEntity<List<StateTransitionEvent>> getHistory() {
        return ResponseEntity.ok(stateDemoService.getEventHistory());
    }
}
