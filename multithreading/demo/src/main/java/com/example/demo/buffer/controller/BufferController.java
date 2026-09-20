package com.example.demo.buffer.controller;

import com.example.demo.buffer.service.BufferBenchmarkService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST контроллер для тестирования и бенчмаркинга BoundedBuffer.
 */
@RestController
@RequestMapping("/api/buffer")
public class BufferController {

    private final BufferBenchmarkService benchmarkService;

    public BufferController(BufferBenchmarkService benchmarkService) {
        this.benchmarkService = benchmarkService;
    }

    /**
     * Сравнительный бенчмарк BoundedBuffer (ReentrantLock + Condition) vs SynchronizedBoundedBuffer.
     */
    @PostMapping("/benchmark")
    public ResponseEntity<Map<String, Object>> runBenchmark(
            @RequestParam(defaultValue = "20") int capacity,
            @RequestParam(defaultValue = "8") int producers,
            @RequestParam(defaultValue = "8") int consumers,
            @RequestParam(defaultValue = "25000") int itemsPerProducer) throws InterruptedException {

        return ResponseEntity.ok(benchmarkService.runBenchmark(capacity, producers, consumers, itemsPerProducer));
    }

    /**
     * Пошаговая демонстрация взаимодействия producer и consumer.
     */
    @PostMapping("/demo")
    public ResponseEntity<List<String>> runDemo(
            @RequestParam(defaultValue = "5") int capacity,
            @RequestParam(defaultValue = "10") int itemsCount) throws InterruptedException {

        return ResponseEntity.ok(benchmarkService.runDemo(capacity, itemsCount));
    }
}
