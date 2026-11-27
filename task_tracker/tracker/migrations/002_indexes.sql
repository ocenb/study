CREATE INDEX tasks_owner ON tasks(owner_id, id);
CREATE INDEX tasks_owner_status ON tasks(owner_id, status);
CREATE INDEX tasks_owner_topic ON tasks(owner_id, topic);
CREATE INDEX tasks_owner_assignee ON tasks(owner_id, assignee);
CREATE INDEX sessions_expiry ON sessions(expires_at);
