package com.example.demo.forkjoin;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.RecursiveTask;

/** Дерево задач: каждый лист обрабатывает ровно один батч, узлы суммируют результаты. */
final class AggregatedCountTask extends RecursiveTask<ForkJoinElementCounter.CountResult> {
    private static final long serialVersionUID = 1L;

    private final List<?> items;
    private final int batchSize;
    private final int fromBatch;
    private final int toBatch;

    AggregatedCountTask(List<?> items, int batchSize, int fromBatch, int toBatch) {
        this.items = items;
        this.batchSize = batchSize;
        this.fromBatch = fromBatch;
        this.toBatch = toBatch;
    }

    @Override
    protected ForkJoinElementCounter.CountResult compute() {
        if (toBatch - fromBatch == 1) {
            int from = (int) ((long) fromBatch * batchSize);
            int to = (int) Math.min((long) items.size(), (long) from + batchSize);
            long count = 0;
            for (int i = from; i < to; i++) {
                // Считаем существующие объекты агрегированных данных, а не исходные ID.
                Objects.requireNonNull(items.get(i));
                count++;
            }
            return new ForkJoinElementCounter.CountResult(count, 1, 1);
        }
        int middle = fromBatch + (toBatch - fromBatch) / 2;
        AggregatedCountTask left = new AggregatedCountTask(items, batchSize, fromBatch, middle);
        AggregatedCountTask right = new AggregatedCountTask(items, batchSize, middle, toBatch);
        left.fork();
        ForkJoinElementCounter.CountResult rightResult = right.compute();
        ForkJoinElementCounter.CountResult leftResult = left.join();
        return new ForkJoinElementCounter.CountResult(
                leftResult.elements() + rightResult.elements(),
                leftResult.batches() + rightResult.batches(),
                leftResult.tasks() + rightResult.tasks() + 1);
    }
}
