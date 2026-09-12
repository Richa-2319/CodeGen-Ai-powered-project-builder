CREATE TABLE projects (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    is_public BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMPTZ
);

CREATE INDEX idx_projects_updated_at_desc ON projects (updated_at DESC, deleted_at);
CREATE INDEX idx_projects_deleted_at_updated_at_desc ON projects (deleted_at, updated_at DESC);
CREATE INDEX idx_project_deleted_at ON projects (deleted_at);

CREATE TABLE project_members (
    project_id BIGINT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL,
    project_role VARCHAR(64) NOT NULL,
    invited_at TIMESTAMPTZ,
    accepted_at TIMESTAMPTZ,
    PRIMARY KEY (project_id, user_id)
);

CREATE TABLE project_files (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    path VARCHAR(1024) NOT NULL,
    minio_object_key VARCHAR(1024),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (project_id, path)
);

CREATE TABLE processed_events (
    saga_id VARCHAR(255) PRIMARY KEY,
    processed_at TIMESTAMP NOT NULL
);
