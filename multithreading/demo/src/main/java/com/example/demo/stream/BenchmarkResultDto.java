package com.example.demo.stream;

/**
 * DTO с метриками выполнения бенчмарка стримов.
 */
public class BenchmarkResultDto {
    private String operationName;
    private int elementCount;
    private long sequentialTimeNanos;
    private long parallelTimeNanos;
    private double sequentialTimeMillis;
    private double parallelTimeMillis;
    private double speedupFactor;
    private long resultSum;
    private boolean resultsMatch;

    public BenchmarkResultDto() {
    }

    public BenchmarkResultDto(String operationName, int elementCount, long sequentialTimeNanos,
                              long parallelTimeNanos, long resultSum, boolean resultsMatch) {
        this.operationName = operationName;
        this.elementCount = elementCount;
        this.sequentialTimeNanos = sequentialTimeNanos;
        this.parallelTimeNanos = parallelTimeNanos;
        this.sequentialTimeMillis = sequentialTimeNanos / 1_000_000.0;
        this.parallelTimeMillis = parallelTimeNanos / 1_000_000.0;
        this.speedupFactor = parallelTimeNanos > 0 ? (double) sequentialTimeNanos / parallelTimeNanos : 0.0;
        this.resultSum = resultSum;
        this.resultsMatch = resultsMatch;
    }

    public String getOperationName() {
        return operationName;
    }

    public int getElementCount() {
        return elementCount;
    }

    public long getSequentialTimeNanos() {
        return sequentialTimeNanos;
    }

    public long getParallelTimeNanos() {
        return parallelTimeNanos;
    }

    public double getSequentialTimeMillis() {
        return sequentialTimeMillis;
    }

    public double getParallelTimeMillis() {
        return parallelTimeMillis;
    }

    public double getSpeedupFactor() {
        return speedupFactor;
    }

    public long getResultSum() {
        return resultSum;
    }

    public boolean isResultsMatch() {
        return resultsMatch;
    }

    @Override
    public String toString() {
        return String.format("%s [N=%d]: Seq=%.2f ms, Par=%.2f ms, Speedup=%.2fx, Sum=%d, Match=%s",
                operationName, elementCount, sequentialTimeMillis, parallelTimeMillis,
                speedupFactor, resultSum, resultsMatch);
    }
}
