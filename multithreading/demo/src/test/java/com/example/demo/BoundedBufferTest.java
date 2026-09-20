package com.example.demo;

import com.example.demo.buffer.model.BoundedBuffer;
import com.example.demo.buffer.service.BufferBenchmarkService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class BoundedBufferTest {

    @Autowired
    private BufferBenchmarkService benchmarkService;

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Базовые операции put и take, соблюдение порядка FIFO")
    void testBasicPutAndTake() throws InterruptedException {
        BoundedBuffer<String> buffer = new BoundedBuffer<>(3);
        assertTrue(buffer.isEmpty());

        buffer.put("A");
        buffer.put("B");
        buffer.put("C");

        assertTrue(buffer.isFull());
        assertEquals(3, buffer.size());

        assertEquals("A", buffer.take());
        assertEquals("B", buffer.take());
        assertEquals("C", buffer.take());

        assertTrue(buffer.isEmpty());
        assertEquals(0, buffer.size());
    }

    @Test
    @DisplayName("Producer блокируется на full буфере и просыпается при take")
    void testProducerBlocksWhenFull() throws InterruptedException {
        BoundedBuffer<Integer> buffer = new BoundedBuffer<>(1);
        buffer.put(100);

        AtomicBoolean putCompleted = new AtomicBoolean(false);
        Thread producer = new Thread(() -> {
            try {
                buffer.put(200); // Должен заблокироваться, так как capacity = 1
                putCompleted.set(true);
            } catch (InterruptedException ignored) {}
        });

        producer.start();
        Thread.sleep(100);
        assertFalse(putCompleted.get(), "Producer должен быть заблокирован");

        // Освобождаем место
        assertEquals(100, buffer.take());
        producer.join(1000);

        assertTrue(putCompleted.get(), "Producer должен был успешно проснуться и положить элемент");
        assertEquals(200, buffer.take());
    }

    @Test
    @DisplayName("Consumer блокируется на пустом буфере и просыпается при put")
    void testConsumerBlocksWhenEmpty() throws InterruptedException {
        BoundedBuffer<String> buffer = new BoundedBuffer<>(2);

        BlockingQueue<String> resultQueue = new ArrayBlockingQueue<>(1);
        Thread consumer = new Thread(() -> {
            try {
                String item = buffer.take(); // Блокируется
                resultQueue.put(item);
            } catch (InterruptedException ignored) {}
        });

        consumer.start();
        Thread.sleep(100);
        assertTrue(resultQueue.isEmpty(), "Consumer должен ждать появления элементов");

        buffer.put("Hello Condition");
        String consumed = resultQueue.poll(1, TimeUnit.SECONDS);

        assertEquals("Hello Condition", consumed);
        consumer.join();
    }

    @Test
    @DisplayName("Нагрузочное тестирование BoundedBuffer: 8 producers, 8 consumers, 20 000 элементов")
    void testHighConcurrencyStress() throws InterruptedException {
        Map<String, Object> res = benchmarkService.runBenchmark(10, 4, 4, 5000);

        assertNotNull(res);
        assertEquals(20000, res.get("totalItemsTransferred"));

        @SuppressWarnings("unchecked")
        Map<String, Object> lockMetrics = (Map<String, Object>) res.get("reentrantLockCondition");
        assertTrue((Boolean) lockMetrics.get("checksumMatches"), "Контрольные суммы produced и consumed должны совпасть");
    }

    @Test
    @DisplayName("Операции с таймаутом put(timeout) и take(timeout)")
    void testTimeoutOperations() throws InterruptedException {
        BoundedBuffer<Integer> buffer = new BoundedBuffer<>(1);
        buffer.put(1);

        // Буфер полон: попытка put с таймаутом должна вернуть false
        boolean putSuccess = buffer.put(2, 50, TimeUnit.MILLISECONDS);
        assertFalse(putSuccess);

        // Забираем элемент
        assertEquals(1, buffer.take());

        // Буфер пуст: попытка take с таймаутом должна вернуть null
        Integer item = buffer.take(50, TimeUnit.MILLISECONDS);
        assertNull(item);
    }

    @Test
    @DisplayName("REST API POST /api/buffer/benchmark")
    void testRestBenchmarkEndpoint() throws Exception {
        mockMvc.perform(post("/api/buffer/benchmark")
                        .param("capacity", "5")
                        .param("producers", "2")
                        .param("consumers", "2")
                        .param("itemsPerProducer", "500")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.winner").exists())
                .andExpect(jsonPath("$.reentrantLockCondition.checksumMatches").value(true));
    }

    @Test
    @DisplayName("REST API POST /api/buffer/demo")
    void testRestDemoEndpoint() throws Exception {
        mockMvc.perform(post("/api/buffer/demo")
                        .param("capacity", "3")
                        .param("itemsCount", "5")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }
}
