CREATE TABLE IF NOT EXISTS vacancies (
    id VARCHAR(20) PRIMARY KEY,
    url VARCHAR(150) NOT NULL,
    title VARCHAR(500) NOT NULL,
    company VARCHAR(500) NOT NULL,
    salary VARCHAR(500),
    requirements CLOB,
    city VARCHAR(1000),
    published_at DATE NOT NULL
);

CREATE TABLE IF NOT EXISTS parse_jobs (
    id UUID PRIMARY KEY,
    vacancy_id VARCHAR(20) NOT NULL,
    status VARCHAR(9) NOT NULL CHECK (status IN ('QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED')),
    submitted_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    error VARCHAR(500),
    active_vacancy_id VARCHAR(20) GENERATED ALWAYS AS (
        CASE WHEN status IN ('QUEUED', 'RUNNING') THEN vacancy_id END
    ) UNIQUE
);

CREATE INDEX IF NOT EXISTS parse_jobs_queue_idx ON parse_jobs (status, submitted_at, id);
