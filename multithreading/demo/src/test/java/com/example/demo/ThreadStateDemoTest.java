package com.example.demo;

import com.example.demo.states.StateTransitionEvent;
import com.example.demo.states.ThreadStateDemoService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ThreadStateDemoTest {

    @Autowired
    private ThreadStateDemoService threadStateDemoService;

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Демонстрация должна охватывать все 6 состояний потока (NEW, RUNNABLE, WAITING, BLOCKED, TIMED_WAITING, TERMINATED)")
    void testAllSixStatesObserved() {
        List<StateTransitionEvent> events = threadStateDemoService.runStateDemo();

        assertNotNull(events);
        assertFalse(events.isEmpty());

        Set<String> observedStates = events.stream()
                .map(StateTransitionEvent::getState)
                .collect(Collectors.toSet());

        assertTrue(observedStates.contains("NEW"), "Должно быть зафиксировано состояние NEW");
        assertTrue(observedStates.contains("RUNNABLE"), "Должно быть зафиксировано состояние RUNNABLE");
        assertTrue(observedStates.contains("TIMED_WAITING"), "Должно быть зафиксировано состояние TIMED_WAITING");
        assertTrue(observedStates.contains("WAITING"), "Должно быть зафиксировано состояние WAITING");
        assertTrue(observedStates.contains("BLOCKED"), "Должно быть зафиксировано состояние BLOCKED");
        assertTrue(observedStates.contains("TERMINATED"), "Должно быть зафиксировано состояние TERMINATED");

        // Проверяем наличие всех 3 целевых потоков
        Set<String> threadNames = events.stream()
                .map(StateTransitionEvent::getThreadName)
                .collect(Collectors.toSet());

        assertTrue(threadNames.contains("TimedWaitingWorker"));
        assertTrue(threadNames.contains("WaitingWorker"));
        assertTrue(threadNames.contains("BlockedWorker"));
    }

    @Test
    @DisplayName("REST API /api/thread-states/run выполняет демонстрацию и возвращает JSON")
    void testRestEndpointRun() throws Exception {
        mockMvc.perform(post("/api/thread-states/run")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.events").isArray());
    }

    @Test
    @DisplayName("REST API /api/thread-states/history возвращает сохраненную историю событий")
    void testRestEndpointHistory() throws Exception {
        mockMvc.perform(get("/api/thread-states/history")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }
}
