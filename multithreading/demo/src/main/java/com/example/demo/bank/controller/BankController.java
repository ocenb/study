package com.example.demo.bank.controller;

import com.example.demo.bank.model.Item;
import com.example.demo.bank.service.BankService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST контроллер для банковских операций, нагрузочного тестирования и демонстрации DataCollector.
 */
@RestController
@RequestMapping("/api/bank")
public class BankController {

    private final BankService bankService;

    public BankController(BankService bankService) {
        this.bankService = bankService;
    }

    /**
     * Запуск стресс-теста безопасных переводов (проверка предотвращения Deadlock и сохранения инварианта).
     */
    @PostMapping("/transfer-stress-test")
    public ResponseEntity<Map<String, Object>> runStressTest(
            @RequestParam(defaultValue = "10") int threads,
            @RequestParam(defaultValue = "1000") int transfersPerThread) throws InterruptedException {
        return ResponseEntity.ok(bankService.runTransferStressTest(threads, transfersPerThread));
    }

    /**
     * Сравнение синхронизированного блока vs несинхронизированного (демонстрация Race Condition).
     */
    @PostMapping("/race-condition-comparison")
    public ResponseEntity<Map<String, Object>> compareRaceCondition(
            @RequestParam(defaultValue = "8") int threads,
            @RequestParam(defaultValue = "10000") int increments) throws InterruptedException {
        return ResponseEntity.ok(bankService.compareSynchronizedVsUnsynchronized(threads, increments));
    }

    /**
     * Демонстрация работы wait() и notifyAll() при списании и пополнении счёта.
     */
    @PostMapping("/wait-notify-demo")
    public ResponseEntity<Map<String, Object>> runWaitNotify() throws InterruptedException {
        return ResponseEntity.ok(bankService.runWaitNotifyDemo());
    }

    /**
     * Получить собранные элементы из DataCollector.
     */
    @GetMapping("/collector-items")
    public ResponseEntity<List<Item>> getCollectedItems() {
        return ResponseEntity.ok(bankService.getDataCollector().getCollectedItems());
    }
}
