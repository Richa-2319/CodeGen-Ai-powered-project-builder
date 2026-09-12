CREATE TABLE chat_sessions (
    project_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ,
    deleted_at TIMESTAMPTZ,
    PRIMARY KEY (project_id, user_id)
);

CREATE TABLE chat_messages (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role VARCHAR(32) NOT NULL,
    content TEXT,
    tokens_used INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_chat_message_session FOREIGN KEY (project_id, user_id)
        REFERENCES chat_sessions(project_id, user_id) ON DELETE CASCADE
);

CREATE TABLE chat_events (
    id BIGSERIAL PRIMARY KEY,
    chat_message_id BIGINT NOT NULL REFERENCES chat_messages(id) ON DELETE CASCADE,
    type VARCHAR(64) NOT NULL,
    sequence_order INTEGER NOT NULL,
    content TEXT,
    file_path VARCHAR(1024),
    metadata TEXT,
    saga_id VARCHAR(255),
    status VARCHAR(64)
);

CREATE INDEX idx_chat_events_message_sequence ON chat_events (chat_message_id, sequence_order);

CREATE TABLE usage_logs (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    date DATE NOT NULL,
    tokens_used INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT uq_usage_logs_user_date UNIQUE (user_id, date)
);
