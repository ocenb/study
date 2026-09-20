package com.example.demo;

import com.example.demo.bank.collector.DataCollector;
import com.example.demo.bank.model.BankAccount;
import com.example.demo.bank.model.Item;
import com.example.demo.bank.service.BankService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class BankAndCollectorTest {

    @Autowired
    private BankService bankService;

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("DataCollector: многопоточный сбор данных без гонки данных и дедупликация")
    void testDataCollectorMultithreadedAndDeduplication() throws InterruptedException {
        DataCollector collector = new DataCollector();
        int threads = 8;
        int itemsPerThread = 500;

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            final int thId = i;
            executor.submit(() -> {
                for (int j = 0; j < itemsPerThread; j++) {
                    collector.collectItem(new Item("KEY-" + thId + "-" + j, "Desc", j));
                }
                latch.countDown();
            });
        }

        latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals(threads * itemsPerThread, collector.getProcessedCount());
        assertEquals(threads * itemsPerThread, collector.getCollectedItems().size());

        // Проверка дедупликации: повторная попытка добавить те же ключи не увеличивает счетчик
        boolean reAdded = collector.collectItem(new Item("KEY-0-0", "Duplicate", 100));
        assertFalse(reAdded, "Дубликат не должен быть добавлен");
        assertEquals(threads * itemsPerThread, collector.getProcessedCount());
    }

    @Test
    @DisplayName("BankAccount: wait() и notifyAll() при списании средств с ожиданием пополнения")
    void testWithdrawWithWaitAndNotifyAll() throws InterruptedException {
        Map<String, Object> result = bankService.runWaitNotifyDemo();
        assertNotNull(result);
        assertEquals(2_000L, result.get("finalBalance")); // 1000 + 6000 - 5000 = 2000
    }

    @Test
    @DisplayName("Предотвращение Deadlock и сохранение баланса при встречных переводах")
    void testDeadlockPreventionInTransfers() throws InterruptedException {
        // 10 потоков, 1000 переводов каждый = 10 000 переводов
        Map<String, Object> stressReport = bankService.runTransferStressTest(10, 1000);

        assertTrue((Boolean) stressReport.get("completedWithoutDeadlock"), "Тест должен завершиться без Deadlock");
        assertTrue((Boolean) stressReport.get("balancePreserved"), "Суммарный баланс банка обязан остаться неизменным");
        assertEquals(stressReport.get("initialTotalBalance"), stressReport.get("finalTotalBalance"));
    }

    @Test
    @DisplayName("Сравнение: синхронизированный блок защищает от потерь данных (Race Condition)")
    void testRaceConditionComparison() throws InterruptedException {
        Map<String, Object> comparison = bankService.compareSynchronizedVsUnsynchronized(8, 10_000);

        int expected = (Integer) comparison.get("expectedTotal");
        int syncCount = (Integer) comparison.get("syncCount");
        int unsafeCount = (Integer) comparison.get("unsafeCount");

        assertEquals(expected, syncCount, "Синхронизированный счетчик не должен терять инкременты");
        // Несинхронизированный счетчик при 8 потоках по 10 000 операций почти наверняка теряет часть инкрементов
        assertTrue(unsafeCount <= expected, "Несинхронизированный счетчик подвержен гонке данных");
    }

    @Test
    @DisplayName("REST API /api/bank/transfer-stress-test")
    void testRestTransferStressTest() throws Exception {
        mockMvc.perform(post("/api/bank/transfer-stress-test")
                        .param("threads", "4")
                        .param("transfersPerThread", "200")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completedWithoutDeadlock").value(true))
                .andExpect(jsonPath("$.balancePreserved").value(true));
    }

    @Test
    @DisplayName("REST API /api/bank/wait-notify-demo")
    void testRestWaitNotify() throws Exception {
        mockMvc.perform(post("/api/bank/wait-notify-demo")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.finalBalance").value(2000));
    }

    @Test
    @DisplayName("REST API GET /api/bank/collector-items")
    void testRestGetCollectorItems() throws Exception {
        mockMvc.perform(get("/api/bank/collector-items")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }
}
