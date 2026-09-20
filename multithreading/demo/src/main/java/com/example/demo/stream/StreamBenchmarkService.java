package com.example.demo.stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Сервис бенчмаркинга последовательных (stream) и параллельных (parallelStream) потоков.
 *
 * Выполняет над списком из 1 000 000 случайных чисел операции:
 * 1. Фильтрация: выбор только чётных чисел (n % 2 == 0)
 * 2. Преобразование: умножение на 2 (n * 2)
 * 3. Агрегация: подсчёт суммы всех обработанных чисел
 */
@Service
public class StreamBenchmarkService {

    private static final Logger log = LoggerFactory.getLogger(StreamBenchmarkService.class);

    private static final int DEFAULT_SIZE = 1_000_000;
    private static final int DEFAULT_ITERATIONS = 5;

    /**
     * Генерация списка случайных целых чисел.
     */
    public List<Integer> generateRandomList(int size) {
        Random random = new Random(42); // Фиксированный seed для воспроизводимости
        List<Integer> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(random.nextInt(1000));
        }
        return list;
    }

    /**
     * Запуск полного сравнительного тестирования с выводом в консоль.
     */
    public List<BenchmarkResultDto> runBenchmark() {
        return runBenchmark(DEFAULT_SIZE, DEFAULT_ITERATIONS);
    }

    /**
     * Запуск бенчмарка с настраиваемым размером и количеством повторений.
     */
    public List<BenchmarkResultDto> runBenchmark(int size, int iterations) {
        log.info("Генерация списка из {} случайных чисел...", size);
        List<Integer> numbers = generateRandomList(size);

        System.out.println("\n==========================================================================================");
        System.out.println("             СРАВНЕНИЕ ПРОИЗВОДИТЕЛЬНОСТИ: stream() VS parallelStream()");
        System.out.printf("  Количество элементов: %,d | Доступно логических ядер CPU: %d%n",
                size, Runtime.getRuntime().availableProcessors());
        System.out.println("==========================================================================================\n");

        // 1. Прогрев JVM (JIT Warm-up), чтобы исключить влияние компиляции C2 на замеры
        warmUpJvm(numbers);

        List<BenchmarkResultDto> results = new ArrayList<>();

        // Сценарий 1: Базовая вычислительная нагрузка (фильтрация четных -> умножение на 2 -> сумма)
        BenchmarkResultDto lightResult = benchmarkPipeline(
                "Базовый пайплайн (filter even -> map * 2 -> sum)",
                numbers,
                iterations,
                false
        );
        results.add(lightResult);

        // Сценарий 2: Вычислительно-тяжелая нагрузка (сложная математика в map для проверки NQ-модели)
        BenchmarkResultDto heavyResult = benchmarkPipeline(
                "Тяжелый пайплайн (filter even -> map heavyMath -> sum)",
                numbers,
                iterations,
                true
        );
        results.add(heavyResult);

        // Итоговый табличный вывод в консоль
        printSummaryReport(results);

        return results;
    }

    /**
     * Прогон цепочки операций (stream vs parallelStream) с замером времени.
     */
    private BenchmarkResultDto benchmarkPipeline(String scenarioName, List<Integer> numbers,
                                                 int iterations, boolean heavyCompute) {
        System.out.printf(">>> Тестирование сценария: %s...%n", scenarioName);

        long totalSeqNanos = 0;
        long totalParNanos = 0;
        long lastSeqSum = 0;
        long lastParSum = 0;

        for (int iter = 1; iter <= iterations; iter++) {
            // Последовательный stream()
            long startSeq = System.nanoTime();
            long seqSum;
            if (heavyCompute) {
                seqSum = numbers.stream()
                        .filter(n -> n % 2 == 0)
                        .mapToLong(n -> heavyCalculation(n))
                        .sum();
            } else {
                seqSum = numbers.stream()
                        .filter(n -> n % 2 == 0)
                        .mapToLong(n -> (long) n * 2)
                        .sum();
            }
            long endSeq = System.nanoTime();
            long seqDuration = endSeq - startSeq;
            totalSeqNanos += seqDuration;
            lastSeqSum = seqSum;

            // Параллельный parallelStream()
            long startPar = System.nanoTime();
            long parSum;
            if (heavyCompute) {
                parSum = numbers.parallelStream()
                        .filter(n -> n % 2 == 0)
                        .mapToLong(n -> heavyCalculation(n))
                        .sum();
            } else {
                parSum = numbers.parallelStream()
                        .filter(n -> n % 2 == 0)
                        .mapToLong(n -> (long) n * 2)
                        .sum();
            }
            long endPar = System.nanoTime();
            long parDuration = endPar - startPar;
            totalParNanos += parDuration;
            lastParSum = parSum;

            System.out.printf("  Итерация %d: stream() = %.2f ms | parallelStream() = %.2f ms | Суммы совпадают: %s%n",
                    iter,
                    seqDuration / 1_000_000.0,
                    parDuration / 1_000_000.0,
                    (seqSum == parSum));
        }

        long avgSeqNanos = totalSeqNanos / iterations;
        long avgParNanos = totalParNanos / iterations;
        boolean matches = (lastSeqSum == lastParSum);

        return new BenchmarkResultDto(scenarioName, numbers.size(), avgSeqNanos, avgParNanos, lastSeqSum, matches);
    }

    /**
     * Имитация нетривиальной вычислительной нагрузки для проверки формулы N*Q.
     */
    private long heavyCalculation(int n) {
        long res = (long) n * 2;
        // Цикл тригонометрических вычислений для загрузки CPU
        for (int i = 0; i < 20; i++) {
            res = (long) (Math.sin(res) * 1000) + res;
        }
        return res;
    }

    /**
     * Прогрев JIT компилятора.
     */
    private void warmUpJvm(List<Integer> list) {
        log.info("Прогрев JVM (JIT компиляция)...");
        for (int i = 0; i < 3; i++) {
            list.stream().filter(n -> n % 2 == 0).mapToLong(n -> (long) n * 2).sum();
            list.parallelStream().filter(n -> n % 2 == 0).mapToLong(n -> (long) n * 2).sum();
        }
    }

    /**
     * Печать итогового отчета в консоль.
     */
    private void printSummaryReport(List<BenchmarkResultDto> results) {
        System.out.println("\n-----------------------------------------------------------------------------------------------------------------");
        System.out.println(" СВОДНЫЙ ОТЧЁТ ПО ВРЕМЕНИ ВЫПОЛНЕНИЯ (СРЕДНЕЕ ПО ИТЕРАЦИЯМ)");
        System.out.println("-----------------------------------------------------------------------------------------------------------------");
        System.out.printf(" %-50s | %-12s | %-15s | %-12s | %-10s%n",
                "СЦЕНАРИЙ", "stream()", "parallelStream()", "УСКОРЕНИЕ", "КОРРЕКТНОСТЬ");
        System.out.println("-----------------------------------------------------------------------------------------------------------------");

        for (BenchmarkResultDto r : results) {
            String winner = r.getSpeedupFactor() >= 1.0
                    ? String.format("%.2fx (par)", r.getSpeedupFactor())
                    : String.format("%.2fx (seq)", 1.0 / r.getSpeedupFactor());

            System.out.printf(" %-50s | %8.2f ms | %10.2f ms    | %-12s | %-10s%n",
                    r.getOperationName(),
                    r.getSequentialTimeMillis(),
                    r.getParallelTimeMillis(),
                    winner,
                    r.isResultsMatch() ? "OK (совпадают)" : "ERR");
        }
        System.out.println("-----------------------------------------------------------------------------------------------------------------\n");
    }
}
