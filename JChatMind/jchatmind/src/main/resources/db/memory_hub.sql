-- Memory Hub 初始表结构（需 PostgreSQL + pgvector）
-- 执行：psql -U postgres -d jchatmind -f src/main/resources/db/memory_hub.sql

CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS t_memory_entry (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id      VARCHAR(64) NOT NULL,
    memory_type     VARCHAR(32) NOT NULL,
    role            VARCHAR(32) NOT NULL,
    content         TEXT NOT NULL,
    summary         VARCHAR(512),
    importance      INTEGER DEFAULT 0,
    memory_tags     TEXT[],
    tool_calls      JSONB,
    metadata        JSONB,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    archived_at     TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_memory_session_type ON t_memory_entry (session_id, memory_type);
CREATE INDEX IF NOT EXISTS idx_memory_session_importance ON t_memory_entry (session_id, importance DESC);
CREATE INDEX IF NOT EXISTS idx_memory_session_created ON t_memory_entry (session_id, created_at DESC);

CREATE TABLE IF NOT EXISTS t_memory_embedding (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    memory_entry_id     UUID NOT NULL REFERENCES t_memory_entry(id) ON DELETE CASCADE,
    session_id          VARCHAR(64) NOT NULL,
    content_hash        VARCHAR(64) NOT NULL UNIQUE,
    embedding_model     VARCHAR(64) NOT NULL,
    embedding           VECTOR(1024),
    created_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_embedding_session ON t_memory_embedding (session_id);

CREATE TABLE IF NOT EXISTS t_memory_context (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id      VARCHAR(64) NOT NULL,
    context_key     VARCHAR(128) NOT NULL,
    context_value   TEXT,
    metadata        JSONB,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_context_session_key ON t_memory_context (session_id, context_key);

CREATE TABLE IF NOT EXISTS t_memory_session (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id          VARCHAR(64) UNIQUE NOT NULL,
    agent_id            VARCHAR(64),
    user_id             VARCHAR(64),
    status              VARCHAR(32) NOT NULL DEFAULT 'active',
    total_messages      INTEGER DEFAULT 0,
    total_tokens        INTEGER DEFAULT 0,
    last_activity_at    TIMESTAMP DEFAULT NOW(),
    metadata            JSONB,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_session_agent ON t_memory_session (agent_id);

CREATE TABLE IF NOT EXISTS t_memory_stats (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id      VARCHAR(64) NOT NULL,
    stat_date       DATE NOT NULL,
    memory_type     VARCHAR(32) NOT NULL,
    entry_count     INTEGER NOT NULL DEFAULT 0,
    total_tokens    INTEGER NOT NULL DEFAULT 0,
    query_count     INTEGER NOT NULL DEFAULT 0,
    hit_count       INTEGER NOT NULL DEFAULT 0,
    metadata        JSONB,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(session_id, stat_date, memory_type)
);

CREATE TABLE IF NOT EXISTS t_memory_task (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    task_type       VARCHAR(64) NOT NULL,
    session_id      VARCHAR(64) NOT NULL,
    status          VARCHAR(32) NOT NULL DEFAULT 'pending',
    priority        INTEGER DEFAULT 5,
    input_data      JSONB,
    result_data     JSONB,
    error_message   TEXT,
    started_at      TIMESTAMP,
    completed_at    TIMESTAMP,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_task_status ON t_memory_task (status, priority DESC, created_at);
