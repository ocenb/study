import com.sun.management.HotSpotDiagnosticMXBean;
import java.lang.management.ManagementFactory;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

public class MemoryPressure {
    private static final int WORKERS = 2;
    private static final int OBJECTS_PER_WORKER = 4;
    private static final int PAYLOAD_BYTES = 64 * 1024;
    private static final int BATCH_INTERVAL_MS = 800;
    private static final int TOTAL_BATCHES = 125;
    private static final int MAX_RETAINED_BATCHES = 8;

    private static final Deque<List<Payload>> RETAINED_BATCHES = new ArrayDeque<>();

    private record Payload(byte[] bytes) {}

    private static List<Payload> allocate() {
        return IntStream.range(0, OBJECTS_PER_WORKER)
                .mapToObj(ignored -> new Payload(new byte[PAYLOAD_BYTES]))
                .toList();
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2 || !(args[0].equals("leak") || args[0].equals("bounded"))) {
            throw new IllegalArgumentException("Usage: MemoryPressure leak|bounded output-directory");
        }
        boolean bounded = args[0].equals("bounded");
        Path output = Path.of(args[1]);
        var diagnostics = ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class);
        var factory = Thread.ofPlatform().name("allocation-", 1).factory();
        List<Callable<List<Payload>>> tasks = Collections.nCopies(WORKERS, MemoryPressure::allocate);
        long start = System.nanoTime();

        try (var pool = Executors.newFixedThreadPool(WORKERS, factory)) {
            for (int batch = 1; batch <= TOTAL_BATCHES; batch++) {
                var payloads = new ArrayList<Payload>(WORKERS * OBJECTS_PER_WORKER);
                for (var result : pool.invokeAll(tasks)) {
                    payloads.addAll(result.get());
                }
                RETAINED_BATCHES.addLast(payloads);
                if (bounded && RETAINED_BATCHES.size() > MAX_RETAINED_BATCHES) {
                    RETAINED_BATCHES.removeFirst();
                }
                if (batch % 10 == 0 || batch == TOTAL_BATCHES) {
                    double elapsed = (System.nanoTime() - start) / 1_000_000_000.0;
                    System.out.printf(Locale.ROOT, "t=%.3fs mode=%s batches=%d retained_payloads=%d%n",
                            elapsed, args[0], batch, RETAINED_BATCHES.size() * WORKERS * OBJECTS_PER_WORKER);
                }
                if (batch == 32 || batch == 82) {
                    String filename = batch == 32 ? "early.hprof" : "late.hprof";
                    System.out.printf(Locale.ROOT, "dump=%s batch=%d retained_payloads=%d%n",
                            filename, batch, RETAINED_BATCHES.size() * WORKERS * OBJECTS_PER_WORKER);
                    diagnostics.dumpHeap(output.resolve(filename).toString(), true);
                }
                if (batch < TOTAL_BATCHES) {
                    Thread.sleep(BATCH_INTERVAL_MS);
                }
            }
        }
    }
}
