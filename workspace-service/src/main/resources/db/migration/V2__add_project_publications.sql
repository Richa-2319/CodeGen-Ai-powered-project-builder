CREATE TABLE project_publications (
    project_id BIGINT PRIMARY KEY REFERENCES projects(id) ON DELETE CASCADE,
    slug UUID NOT NULL UNIQUE,
    active BOOLEAN NOT NULL DEFAULT FALSE,
    snapshot TEXT NOT NULL,
    published_at TIMESTAMPTZ NOT NULL
);
