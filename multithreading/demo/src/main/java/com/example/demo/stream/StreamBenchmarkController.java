package com.example.demo.stream;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST контроллер для запуска и получения результатов бенчмарка stream vs parallelStream.
 */
@RestController
@RequestMapping("/api/streams")
public class StreamBenchmarkController {

    private final StreamBenchmarkService benchmarkService;

    public StreamBenchmarkController(StreamBenchmarkService benchmarkService) {
        this.benchmarkService = benchmarkService;
    }

    /**
     * Запуск сравнительного бенчмарка.
     */
    @PostMapping("/benchmark")
    public ResponseEntity<Map<String, Object>> runBenchmark(
            @RequestParam(defaultValue = "1000000") int size,
            @RequestParam(defaultValue = "5") int iterations) {

        List<BenchmarkResultDto> results = benchmarkService.runBenchmark(size, iterations);

        return ResponseEntity.ok(Map.of(
                "status", "success",
                "elementCount", size,
                "iterations", iterations,
                "results", results
        ));
    }

    /**
     * Быстрый GET-запуск с дефолтными параметрами.
     */
    @GetMapping("/benchmark")
    public ResponseEntity<List<BenchmarkResultDto>> getBenchmark() {
        return ResponseEntity.ok(benchmarkService.runBenchmark());
    }
}
