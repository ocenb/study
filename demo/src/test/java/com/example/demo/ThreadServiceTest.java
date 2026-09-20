package com.example.demo;

import com.example.demo.model.ThreadInfoDto;
import com.example.demo.service.ThreadService;
import com.example.demo.task.Task;
import com.example.demo.task.WorkerThread;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ThreadServiceTest {

    @Autowired
    private ThreadService threadService;

    @Test
    @DisplayName("Проверка выполнения потока через наследование Thread (WorkerThread)")
    void testWorkerThreadExecution() throws InterruptedException {
        WorkerThread worker = new WorkerThread("TestWorker", 3, 20);
        assertEquals("TestWorker", worker.getName());
        worker.start();
        assertTrue(worker.isAlive());
        worker.join();
        assertFalse(worker.isAlive());
    }

    @Test
    @DisplayName("Проверка выполнения задачи через Runnable (Task)")
    void testRunnableTaskExecution() throws InterruptedException {
        Task task = new Task(3, 20);
        Thread thread = new Thread(task, "TestLogger");
        assertEquals("TestLogger", thread.getName());
        thread.start();
        assertTrue(thread.isAlive());
        thread.join();
        assertFalse(thread.isAlive());
    }

    @Test
    @DisplayName("Проверка получения списка активных потоков")
    void testGetActiveThreads() {
        List<ThreadInfoDto> activeThreads = threadService.getActiveThreads();
        assertNotNull(activeThreads);
        assertFalse(activeThreads.isEmpty());

        // Проверяем, что текущий поток есть в списке активных
        String currentThreadName = Thread.currentThread().getName();
        boolean containsCurrent = activeThreads.stream()
                .anyMatch(t -> t.getName().equals(currentThreadName));
        assertTrue(containsCurrent, "Список активных потоков должен содержать текущий поток");
    }

    @Test
    @DisplayName("Проверка полной демонстрации создания и запуска потоков")
    void testExecuteThreadsDemo() {
        assertDoesNotThrow(() -> threadService.executeThreadsDemo());
    }
}
