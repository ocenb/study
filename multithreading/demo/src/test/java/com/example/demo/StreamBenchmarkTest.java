package com.example.demo;

import com.example.demo.stream.BenchmarkResultDto;
import com.example.demo.stream.StreamBenchmarkService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class StreamBenchmarkTest {

    @Autowired
    private StreamBenchmarkService benchmarkService;

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Результаты вычислений через stream() и parallelStream() должны быть идентичны на 1 000 000 элементов")
    void testStreamAndParallelStreamProduceIdenticalResults() {
        List<BenchmarkResultDto> results = benchmarkService.runBenchmark(1_000_000, 3);

        assertNotNull(results);
        assertEquals(2, results.size());

        for (BenchmarkResultDto r : results) {
            assertTrue(r.isResultsMatch(), "Результаты вычислений должны совпадать для " + r.getOperationName());
            assertTrue(r.getSequentialTimeNanos() > 0);
            assertTrue(r.getParallelTimeNanos() > 0);
        }
    }

    @Test
    @DisplayName("REST API POST /api/streams/benchmark корректно выполняет бенчмарк")
    void testBenchmarkEndpointPost() throws Exception {
        mockMvc.perform(post("/api/streams/benchmark")
                        .param("size", "10000")
                        .param("iterations", "1")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.results").isArray());
    }

    @Test
    @DisplayName("REST API GET /api/streams/benchmark возвращает результаты")
    void testBenchmarkEndpointGet() throws Exception {
        mockMvc.perform(get("/api/streams/benchmark")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }
}
