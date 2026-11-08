package com.example.vacancyparser;

import java.util.UUID;

record ParseJob(UUID id, String vacancyId, Status status, String error) {
    public String getUrl() {
        return new VacancyLink(vacancyId).url();
    }

    enum Status {
        QUEUED, RUNNING, SUCCEEDED, FAILED
    }
}
