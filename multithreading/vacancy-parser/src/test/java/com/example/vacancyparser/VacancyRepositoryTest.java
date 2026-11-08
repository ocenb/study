package com.example.vacancyparser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

class VacancyRepositoryTest {
    @Test
    void duplicateLinksKeepOneJobIdEvenIfAnEarlierJobFinishesDuringSubmission() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        ParseJob running = new ParseJob(UUID.randomUUID(), "101", ParseJob.Status.RUNNING, null);
        when(jdbc.query(anyString(), ArgumentMatchers.<RowMapper<ParseJob>>any(), eq("101")))
                .thenReturn(List.of(running))
                .thenReturn(List.of());

        VacancyRepository repository = new VacancyRepository(jdbc, manager);
        VacancyLink first = VacancyLink.parse("https://career.habr.com/vacancies/101");
        VacancyLink duplicate = VacancyLink.parse("https://career.habr.com/vacancies/101?from=list");

        assertThat(repository.enqueue(List.of(first, duplicate))).containsExactly(running, running);
    }
}
