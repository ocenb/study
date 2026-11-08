package com.example.vacancyparser;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
class VacancyWorker {
    private static final Logger log = LoggerFactory.getLogger(VacancyWorker.class);
    private static final int WORKER_COUNT = 4;
    private static final int QUEUE_CAPACITY = 16;

    private final VacancyRepository repository;
    private final HabrVacancyParser parser;
    private final ThreadPoolExecutor workers;

    VacancyWorker(VacancyRepository repository, HabrVacancyParser parser) {
        this.repository = repository;
        this.parser = parser;
        AtomicInteger sequence = new AtomicInteger();
        this.workers = new ThreadPoolExecutor(WORKER_COUNT, WORKER_COUNT, 0, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(QUEUE_CAPACITY),
                task -> new Thread(task, "vacancy-parse-" + sequence.incrementAndGet()));
    }

    @PostConstruct
    void recoverInterruptedJobs() {
        repository.recoverInterruptedJobs();
    }

    @Scheduled(initialDelay = 1000, fixedDelay = 1000)
    void dispatch() {
        int capacity = workers.getQueue().remainingCapacity();
        if (capacity == 0 || workers.isShutdown()) {
            return;
        }
        for (ParseJob job : repository.queuedJobs(capacity)) {
            if (repository.markRunning(job.id())) {
                try {
                    workers.execute(() -> process(job));
                } catch (RejectedExecutionException exception) {
                    repository.requeue(job.id());
                    return;
                }
            }
        }
    }

    private void process(ParseJob job) {
        log.info("Parsing vacancy {} for job {}", job.vacancyId(), job.id());
        try {
            Vacancy vacancy = parser.fetch(new VacancyLink(job.vacancyId()));
            repository.complete(job.id(), vacancy);
        } catch (HabrVacancyParser.SourceException exception) {
            if (!Thread.currentThread().isInterrupted()) {
                repository.fail(job.id(), exception.getMessage());
            }
        } catch (RuntimeException exception) {
            log.error("Failed to process job {}", job.id(), exception);
            repository.fail(job.id(), "Внутренняя ошибка при обработке вакансии");
        }
    }

    @PreDestroy
    void stop() {
        workers.shutdown();
        try {
            if (!workers.awaitTermination(12, TimeUnit.SECONDS)) {
                workers.shutdownNow();
            }
        } catch (InterruptedException exception) {
            workers.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
