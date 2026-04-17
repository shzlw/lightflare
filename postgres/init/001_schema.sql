CREATE EXTENSION IF NOT EXISTS vector;

DROP TABLE IF EXISTS document_chunk;
DROP TABLE IF EXISTS document;
DROP TABLE IF EXISTS memory;
DROP TABLE IF EXISTS chat_message;
DROP TABLE IF EXISTS chat_session;
DROP TABLE IF EXISTS skill;
DROP TABLE IF EXISTS app_user_identity;
DROP TABLE IF EXISTS app_user;
DROP TABLE IF EXISTS auth_session;
DROP TABLE IF EXISTS workflow_step_run;
DROP TABLE IF EXISTS workflow_run;
DROP TABLE IF EXISTS workflow_trigger;
DROP TABLE IF EXISTS workflow;
DROP TABLE IF EXISTS execution_checkpoint;

CREATE TABLE memory
(
    id                TEXT PRIMARY KEY,
    owner_user_id     TEXT,                 -- null only for public memory
    session_id        TEXT,                 -- set for session-scoped memory
    scope             VARCHAR(20) NOT NULL, -- session, user, public
    kind              VARCHAR(30) NOT NULL, -- chat_message, knowledge_note, summary, fact, tool_result, document
    source            VARCHAR(20) NOT NULL, -- user, agent, system, import
    retention_policy  VARCHAR(20) NOT NULL, -- compactable, preserve_raw
    status            VARCHAR(20) NOT NULL, -- active, archived, deleted
    status_reason     VARCHAR(30),          -- compacted, user_deleted, admin_deleted, expired, manual
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

CREATE TABLE chat_session
(
    id                  TEXT PRIMARY KEY,
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

CREATE TABLE workflow
(
    id                TEXT PRIMARY KEY,
    name              VARCHAR(255) NOT NULL,
    description       TEXT,
    status            VARCHAR(32)  NOT NULL DEFAULT 'draft',
    definition_json   TEXT         NOT NULL,
    created_by        VARCHAR(128),
    created_at        TIMESTAMPTZ DEFAULT NOW() NOT NULL,
    updated_at        TIMESTAMPTZ DEFAULT NOW() NOT NULL
);

CREATE TABLE workflow_trigger
(
    id              TEXT PRIMARY KEY,
    workflow_id     TEXT        NOT NULL REFERENCES workflow (id) ON DELETE CASCADE,
    trigger_type    VARCHAR(32) NOT NULL,
    name            VARCHAR(255),
    enabled         BOOLEAN     NOT NULL DEFAULT TRUE,
    config_json     TEXT        NOT NULL,
    created_at      TIMESTAMPTZ DEFAULT NOW() NOT NULL,
    updated_at      TIMESTAMPTZ DEFAULT NOW() NOT NULL
);

CREATE TABLE workflow_run
(
    id              TEXT PRIMARY KEY,
    workflow_id     TEXT        NOT NULL REFERENCES workflow (id) ON DELETE CASCADE,
    trigger_id      TEXT REFERENCES workflow_trigger (id) ON DELETE SET NULL,
    trigger_type    VARCHAR(32) NOT NULL,
    status          VARCHAR(32) NOT NULL,
    input_json      TEXT        NOT NULL,
    output_json     TEXT,
    error_message   TEXT,
    started_by      VARCHAR(128),
    source_id       TEXT,
    started_at      TIMESTAMPTZ DEFAULT NOW() NOT NULL,
    completed_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ DEFAULT NOW() NOT NULL
);

CREATE TABLE workflow_step_run
(
    id              TEXT PRIMARY KEY,
    workflow_run_id TEXT        NOT NULL REFERENCES workflow_run (id) ON DELETE CASCADE,
    step_id         TEXT        NOT NULL,
    step_name       VARCHAR(255),
    step_type       VARCHAR(32) NOT NULL,
    status          VARCHAR(32) NOT NULL,
    input_json      TEXT        NOT NULL,
    output_json     TEXT,
    error_message   TEXT,
    started_at      TIMESTAMPTZ DEFAULT NOW() NOT NULL,
    completed_at    TIMESTAMPTZ
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
