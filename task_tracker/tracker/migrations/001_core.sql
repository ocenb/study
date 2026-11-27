CREATE TABLE users (
    id INTEGER PRIMARY KEY,
    username TEXT NOT NULL UNIQUE,
    password_hash TEXT NOT NULL
);

CREATE TABLE sessions (
    token_hash TEXT PRIMARY KEY NOT NULL,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    expires_at INTEGER NOT NULL
);

CREATE TABLE statuses (
    code TEXT PRIMARY KEY NOT NULL,
    label TEXT NOT NULL UNIQUE
);
INSERT INTO statuses (code, label) VALUES
    ('new', 'Новая'), ('in_progress', 'В работе'),
    ('done', 'Сделано'), ('blocked', 'Заблокирована');

CREATE TABLE tasks (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    owner_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title TEXT NOT NULL CHECK(length(trim(title)) BETWEEN 1 AND 200),
    description TEXT NOT NULL DEFAULT '',
    status TEXT NOT NULL DEFAULT 'new' REFERENCES statuses(code),
    topic TEXT,
    assignee TEXT,
    created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
    updated_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now'))
);
