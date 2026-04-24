CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

DROP TABLE IF EXISTS document_chunk;
DROP TABLE IF EXISTS document;
DROP TABLE IF EXISTS memory;
DROP TABLE IF EXISTS chat_artifact;
DROP TABLE IF EXISTS chat_message;
DROP TABLE IF EXISTS chat_session;
DROP TABLE IF EXISTS project;
DROP TABLE IF EXISTS skill;
DROP TABLE IF EXISTS app_user_identity;
DROP TABLE IF EXISTS app_user;
DROP TABLE IF EXISTS auth_session;
DROP TABLE IF EXISTS application_step_run;
DROP TABLE IF EXISTS application_run;
DROP TABLE IF EXISTS application_edge;
DROP TABLE IF EXISTS application_trigger;
DROP TABLE IF EXISTS application_step;
DROP TABLE IF EXISTS application_version;
DROP TABLE IF EXISTS application;
DROP TABLE IF EXISTS execution_checkpoint;

CREATE TABLE memory
(
    id                TEXT PRIMARY KEY,
    owner_user_id     TEXT,
    session_id        TEXT,
    scope             VARCHAR(20) NOT NULL,
    kind              VARCHAR(30) NOT NULL,
    source            VARCHAR(20) NOT NULL,
    retention_policy  VARCHAR(20) NOT NULL,
    status            VARCHAR(20) NOT NULL,
    status_reason     VARCHAR(30),
    status_changed_at TIMESTAMPTZ,
    status_changed_by TEXT,
    content           TEXT        NOT NULL,
    search_vector     TSVECTOR GENERATED ALWAYS AS (to_tsvector('simple', content)) STORED,
    embedding_vector  vector,
    created_at        TIMESTAMPTZ DEFAULT NOW() NOT NULL,
    updated_at        TIMESTAMPTZ DEFAULT NOW() NOT NULL
);

CREATE TABLE document
(
    id                TEXT PRIMARY KEY,
    memory_id         TEXT NOT NULL REFERENCES memory (id) ON DELETE CASCADE,
    file_name         TEXT,
    file_path         TEXT,
    file_size         BIGINT,
    file_content_type TEXT,
    created_at        TIMESTAMPTZ DEFAULT NOW() NOT NULL,
    updated_at        TIMESTAMPTZ DEFAULT NOW() NOT NULL
);

CREATE TABLE document_chunk
(
    id               TEXT PRIMARY KEY,
    document_id      TEXT NOT NULL REFERENCES document (id) ON DELETE CASCADE,
    chunk_index      INT  NOT NULL,
    content          TEXT NOT NULL,
    search_vector    TSVECTOR GENERATED ALWAYS AS (to_tsvector('simple', content)) STORED,
    embedding_vector vector,
    created_at       TIMESTAMPTZ DEFAULT NOW() NOT NULL,
    updated_at       TIMESTAMPTZ DEFAULT NOW() NOT NULL,

    UNIQUE (document_id, chunk_index)
);

CREATE TABLE chat_message
(
    id         TEXT PRIMARY KEY,
    session_id TEXT        NOT NULL,
    source     VARCHAR(20) NOT NULL,
    content    TEXT        NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW() NOT NULL
);

CREATE TABLE chat_artifact
(
    id            TEXT PRIMARY KEY,
    session_id    TEXT        NOT NULL,
    message_id    TEXT,
    artifact_type VARCHAR(50) NOT NULL,
    title         TEXT,
    content       TEXT        NOT NULL,
    metadata      TEXT,
    pinned        BOOLEAN     NOT NULL DEFAULT FALSE,
    display_order INTEGER     NOT NULL DEFAULT 0,
    created_by    TEXT,
    created_at    TIMESTAMPTZ DEFAULT NOW() NOT NULL,
    updated_at    TIMESTAMPTZ DEFAULT NOW() NOT NULL
);

CREATE TABLE project
(
    id          TEXT PRIMARY KEY,
    title       TEXT,
    description TEXT,
    user_id     TEXT,
    status      VARCHAR(20) NOT NULL DEFAULT 'active',
    created_at  TIMESTAMPTZ DEFAULT NOW() NOT NULL,
    updated_at  TIMESTAMPTZ DEFAULT NOW() NOT NULL
);

CREATE TABLE chat_session
(
    id                  TEXT PRIMARY KEY,
    project_id          TEXT        NOT NULL REFERENCES project (id),
    title               TEXT,
    user_id             TEXT,
    total_tokens        INT                  DEFAULT 0,
    total_input_tokens  INT                  DEFAULT 0,
    total_output_tokens INT                  DEFAULT 0,
    status              VARCHAR(20) NOT NULL DEFAULT 'active',
    created_at          TIMESTAMPTZ DEFAULT NOW() NOT NULL,
    updated_at          TIMESTAMPTZ DEFAULT NOW() NOT NULL
);

CREATE TABLE skill
(
    id               TEXT PRIMARY KEY,
    name             TEXT        NOT NULL,
    description      TEXT,
    visibility       TEXT,
    user_id          TEXT,
    source           VARCHAR(20) NOT NULL,
    content          TEXT        NOT NULL,
    embedding_vector vector,
    created_at       TIMESTAMPTZ DEFAULT NOW() NOT NULL,
    updated_at       TIMESTAMPTZ DEFAULT NOW() NOT NULL
);

CREATE TABLE app_user
(
    id                   VARCHAR(255) PRIMARY KEY,
    username             VARCHAR(255) NOT NULL UNIQUE,
    email                VARCHAR(255) UNIQUE,
    display_name         VARCHAR(255),
    password_hash        VARCHAR(255) NOT NULL,
    status               VARCHAR(64)  NOT NULL,
    role                 VARCHAR(64)  NOT NULL,
    must_change_password BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at           TIMESTAMPTZ DEFAULT NOW() NOT NULL,
    updated_at           TIMESTAMPTZ DEFAULT NOW() NOT NULL
);

CREATE TABLE app_user_identity
(
    id               TEXT PRIMARY KEY,
    app_user_id      VARCHAR(255) NOT NULL,
    provider         VARCHAR(64)  NOT NULL,
    external_user_id VARCHAR(255) NOT NULL,
    created_at       TIMESTAMPTZ DEFAULT NOW() NOT NULL,
    updated_at       TIMESTAMPTZ DEFAULT NOW() NOT NULL,

    UNIQUE (provider, external_user_id)
);

CREATE TABLE auth_session
(
    id                 VARCHAR(255) PRIMARY KEY,
    user_id            VARCHAR(255) NOT NULL,
    session_token_hash VARCHAR(255) NOT NULL,
    csrf_token         VARCHAR(255) NOT NULL,
    expires_at         TIMESTAMPTZ  NOT NULL,
    created_at         TIMESTAMPTZ DEFAULT NOW() NOT NULL,
    updated_at         TIMESTAMPTZ DEFAULT NOW() NOT NULL
);

CREATE TABLE application
(
    id                     TEXT PRIMARY KEY,
    name                   TEXT        NOT NULL,
    description            TEXT,
    created_by             TEXT,
    published_version_id   TEXT,
    created_at             TIMESTAMPTZ DEFAULT NOW() NOT NULL,
    updated_at             TIMESTAMPTZ DEFAULT NOW() NOT NULL
);

CREATE TABLE application_version
(
    id               TEXT PRIMARY KEY,
    application_id   TEXT        NOT NULL,
    version_number   INTEGER     NOT NULL,
    status           VARCHAR(20) NOT NULL DEFAULT 'draft',
    created_at       TIMESTAMPTZ DEFAULT NOW() NOT NULL,

    UNIQUE (application_id, version_number)
);

CREATE TABLE application_step
(
    id                     TEXT PRIMARY KEY,
    application_version_id TEXT        NOT NULL,
    step_key               TEXT        NOT NULL,
    name                   TEXT        NOT NULL,
    step_type              VARCHAR(50) NOT NULL,
    config_json            TEXT        NOT NULL DEFAULT '{}',

    UNIQUE (application_version_id, step_key)
);

CREATE TABLE application_trigger
(
    id                     TEXT PRIMARY KEY,
    application_version_id TEXT        NOT NULL,
    trigger_type           VARCHAR(50) NOT NULL,
    start_step_id          TEXT        NOT NULL,
    config_json            TEXT        NOT NULL DEFAULT '{}'
);

CREATE TABLE application_edge
(
    id                     TEXT PRIMARY KEY,
    application_version_id TEXT        NOT NULL,
    from_step_id           TEXT        NOT NULL,
    to_step_id             TEXT        NOT NULL,
    condition_type         VARCHAR(50) NOT NULL DEFAULT 'always', -- 'always', 'if', 'else', 'success', 'failure'
    condition_json         TEXT        NOT NULL DEFAULT '{}'
);

CREATE TABLE application_run
(
    id                     TEXT PRIMARY KEY,
    application_id         TEXT        NOT NULL,
    application_version_id TEXT        NOT NULL,
    trigger_id             TEXT,
    status                 VARCHAR(20) NOT NULL DEFAULT 'running',
    input_json             TEXT        NOT NULL DEFAULT '{}',
    output_json            TEXT        NOT NULL DEFAULT '{}',
    error_message          TEXT,
    started_by             TEXT,
    started_at             TIMESTAMPTZ DEFAULT NOW() NOT NULL,
    completed_at           TIMESTAMPTZ
);

CREATE TABLE application_step_run
(
    id                 TEXT PRIMARY KEY,
    application_run_id TEXT        NOT NULL,
    step_id            TEXT        NOT NULL,
    status             VARCHAR(20) NOT NULL DEFAULT 'pending',
    input_json         TEXT        NOT NULL DEFAULT '{}',
    output_json        TEXT        NOT NULL DEFAULT '{}',
    error_message      TEXT,
    started_at         TIMESTAMPTZ,
    completed_at       TIMESTAMPTZ
);

CREATE TABLE execution_checkpoint
(
    id              TEXT PRIMARY KEY,
    execution_id    TEXT        NOT NULL,
    execution_type  VARCHAR(64) NOT NULL,
    status          VARCHAR(32) NOT NULL,
    reference_type  VARCHAR(64),
    reference_id    TEXT,
    payload         TEXT       NOT NULL,
    created_at      TIMESTAMPTZ DEFAULT NOW() NOT NULL,
    updated_at      TIMESTAMPTZ DEFAULT NOW() NOT NULL
);

CREATE INDEX idx_memory_search_vector
    ON memory USING GIN (search_vector);

CREATE INDEX idx_memory_content_trgm
    ON memory USING GIN (content gin_trgm_ops);

CREATE INDEX idx_document_file_name_trgm
    ON document USING GIN (file_name gin_trgm_ops);

CREATE INDEX idx_document_chunk_search_vector
    ON document_chunk USING GIN (search_vector);

CREATE INDEX idx_document_chunk_content_trgm
    ON document_chunk USING GIN (content gin_trgm_ops);

CREATE INDEX idx_project_user_status_updated
    ON project (user_id, status, updated_at DESC);

CREATE INDEX idx_chat_session_project_status_updated
    ON chat_session (project_id, status, updated_at DESC);

CREATE INDEX idx_chat_artifact_session_order
    ON chat_artifact (session_id, pinned DESC, display_order ASC, updated_at DESC);

CREATE INDEX idx_chat_artifact_message_id
    ON chat_artifact (message_id);

CREATE UNIQUE INDEX idx_app_user_username_lower
    ON app_user (LOWER(username));

CREATE UNIQUE INDEX idx_app_user_email_lower
    ON app_user (LOWER(email))
    WHERE email IS NOT NULL;

CREATE UNIQUE INDEX idx_app_user_identity_provider_external_lower
    ON app_user_identity (LOWER(provider), LOWER(external_user_id));

CREATE INDEX idx_application_created_at
    ON application (created_at DESC);

CREATE INDEX idx_application_version_application_version_number
    ON application_version (application_id, version_number DESC);

CREATE INDEX idx_application_step_version
    ON application_step (application_version_id);

CREATE INDEX idx_application_trigger_version
    ON application_trigger (application_version_id);

CREATE INDEX idx_application_edge_version
    ON application_edge (application_version_id);

CREATE INDEX idx_application_run_application_started
    ON application_run (application_id, started_at DESC);

CREATE INDEX idx_application_step_run_run
    ON application_step_run (application_run_id);
