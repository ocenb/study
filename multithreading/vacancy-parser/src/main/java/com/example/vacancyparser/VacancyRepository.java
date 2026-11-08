package com.example.vacancyparser;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Repository
class VacancyRepository {
    private static final RowMapper<ParseJob> JOB_MAPPER = (row, index) -> new ParseJob(
            row.getObject("id", UUID.class), row.getString("vacancy_id"),
            ParseJob.Status.valueOf(row.getString("status")), row.getString("error"));

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;

    VacancyRepository(JdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    synchronized List<ParseJob> enqueue(List<VacancyLink> links) {
        return transaction.execute(status -> {
            Map<VacancyLink, ParseJob> jobsByLink = new HashMap<>();
            List<ParseJob> jobs = new ArrayList<>(links.size());
            for (VacancyLink link : links) {
                jobs.add(jobsByLink.computeIfAbsent(link, this::enqueueOne));
            }
            return List.copyOf(jobs);
        });
    }

    private ParseJob enqueueOne(VacancyLink link) {
        var active = jdbc.query("SELECT * FROM parse_jobs WHERE active_vacancy_id = ?", JOB_MAPPER, link.id());
        if (!active.isEmpty()) {
            return active.get(0);
        }
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO parse_jobs (id, vacancy_id, status) VALUES (?, ?, 'QUEUED')", id, link.id());
        return new ParseJob(id, link.id(), ParseJob.Status.QUEUED, null);
    }

    List<ParseJob> queuedJobs(int limit) {
        return jdbc.query("""
                SELECT * FROM parse_jobs WHERE status = 'QUEUED' ORDER BY submitted_at, id LIMIT ?
                """, JOB_MAPPER, limit);
    }

    boolean markRunning(UUID id) {
        return jdbc.update("UPDATE parse_jobs SET status = 'RUNNING' WHERE id = ? AND status = 'QUEUED'", id) == 1;
    }

    void requeue(UUID id) {
        jdbc.update("UPDATE parse_jobs SET status = 'QUEUED' WHERE id = ? AND status = 'RUNNING'", id);
    }

    void recoverInterruptedJobs() {
        jdbc.update("UPDATE parse_jobs SET status = 'QUEUED' WHERE status = 'RUNNING'");
    }

    void complete(UUID id, Vacancy vacancy) {
        transaction.executeWithoutResult(status -> {
            jdbc.update("""
                    MERGE INTO vacancies (id, url, title, company, salary, requirements, city, published_at)
                    KEY (id) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """, vacancy.id(), vacancy.url(), vacancy.title(), vacancy.company(), vacancy.salary(),
                    vacancy.requirements(), vacancy.city(), vacancy.publishedAt());
            jdbc.update("UPDATE parse_jobs SET status = 'SUCCEEDED' WHERE id = ?", id);
        });
    }

    void fail(UUID id, String error) {
        jdbc.update("UPDATE parse_jobs SET status = 'FAILED', error = ? WHERE id = ?", error, id);
    }

    Optional<ParseJob> job(UUID id) {
        return jdbc.query("SELECT * FROM parse_jobs WHERE id = ?", JOB_MAPPER, id).stream().findFirst();
    }

    List<Vacancy> vacancies() {
        return jdbc.query("SELECT * FROM vacancies", (row, index) -> new Vacancy(
                row.getString("id"), row.getString("url"), row.getString("title"), row.getString("company"),
                row.getString("salary"), row.getString("requirements"), row.getString("city"),
                row.getObject("published_at", LocalDate.class)));
    }
}
