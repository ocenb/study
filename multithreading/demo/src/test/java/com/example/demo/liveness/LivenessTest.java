package com.example.demo.liveness;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(20)
class LivenessTest {
    @TempDir
    Path temporary;

    @Test
    void oppositeOrderReallyDeadlocksInSeparateJvm() throws Exception {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        String classes = Path.of(DeadlockDemo.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI()).toString();
        Path output = temporary.resolve("deadlock.log");
        Process process = new ProcessBuilder(java, "-cp", classes, DeadlockDemo.class.getName())
                .redirectErrorStream(true).redirectOutput(output.toFile()).start();
        try {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
            while (process.isAlive() && System.nanoTime() < deadline
                    && !Files.readString(output).contains("DEADLOCK_CONFIRMED")) {
                Thread.sleep(10);
            }
            assertTrue(Files.readString(output).contains("DEADLOCK_CONFIRMED"),
                    () -> "Дочерняя JVM не подтвердила deadlock");
            assertTrue(process.isAlive(), "Deadlock должен удерживать процесс");
            assertFalse(process.waitFor(100, TimeUnit.MILLISECONDS));
        } finally {
            process.destroyForcibly();
            assertTrue(process.waitFor(5, TimeUnit.SECONDS), "Дочерняя JVM должна быть завершена");
        }
    }

    @Test
    void commonResourceOrderLetsBothThreadsFinish() throws Exception {
        assertEquals(List.of(10_000, 10_000), LivenessDemo.orderedResources(2, 10_000));
    }

    @Test
    void repeatedRetriesProduceActivityWithoutProgress() throws Exception {
        LivenessDemo.LivelockResult result = LivenessDemo.livelock(1_000, false);
        assertEquals(2_000, result.attempts());
        assertEquals(0, result.completed());
    }

    @Test
    void retryBudgetAndOrderedFallbackRestoreProgress() throws Exception {
        LivenessDemo.LivelockResult result = LivenessDemo.livelock(10, true);
        assertEquals(20, result.attempts());
        assertEquals(2, result.completed());
    }

    @Test
    void monopolizingResourcePreventsWaitingWorkerFromMakingProgress() throws Exception {
        LivenessDemo.StarvationResult result = LivenessDemo.starvation(10_000);
        assertEquals(10_000, result.monopolistOperations());
        assertTrue(result.victimQueued());
        assertEquals(0, result.victimWhileMonopolized());
        assertEquals(1, result.victimAfterRelease());
    }

    @Test
    void fairAccessLetsEveryWorkerFinish() throws Exception {
        assertEquals(List.of(1_000, 1_000, 1_000, 1_000), LivenessDemo.fairAccess(4, 1_000));
    }

    @Test
    void invalidParametersAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> LivenessDemo.orderedResources(0, 1));
        assertThrows(IllegalArgumentException.class, () -> LivenessDemo.livelock(0, true));
        assertThrows(IllegalArgumentException.class, () -> LivenessDemo.starvation(0));
        assertThrows(IllegalArgumentException.class, () -> LivenessDemo.fairAccess(33, 1));
    }
}
