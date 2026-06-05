-- ============================================================
-- V1__memory_hub.sql
-- Memory Hub 记忆管理系统 - 初始表结�?
-- ============================================================

-- 确保 pgvector 扩展
CREATE EXTENSION IF NOT EXISTS vector;

-- 1. 记忆条目�?
CREATE TABLE t_memory_entry (
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

CREATE INDEX idx_memory_session_type ON t_memory_entry (session_id, memory_type);
CREATE INDEX idx_memory_session_importance ON t_memory_entry (session_id, importance DESC);
CREATE INDEX idx_memory_session_created ON t_memory_entry (session_id, created_at DESC);

-- 2. 记忆向量�?
CREATE TABLE t_memory_embedding (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    memory_entry_id     UUID NOT NULL REFERENCES t_memory_entry(id) ON DELETE CASCADE,
    session_id          VARCHAR(64) NOT NULL,
    content_hash        VARCHAR(64) NOT NULL UNIQUE,
    embedding_model     VARCHAR(64) NOT NULL,
    embedding           VECTOR(1024),
    created_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_embedding_session ON t_memory_embedding (session_id);
CREATE INDEX idx_embedding_cosine ON t_memory_embedding USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);

-- 3. 记忆上下文表
CREATE TABLE t_memory_context (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id      VARCHAR(64) NOT NULL,
    context_key     VARCHAR(128) NOT NULL,
    context_value   TEXT,
    metadata        JSONB,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_context_session_key ON t_memory_context (session_id, context_key);

-- 4. 记忆会话�?
CREATE TABLE t_memory_session (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id          VARCHAR(64) UNIQUE NOT NULL,
    agent_id             VARCHAR(64),
    user_id              VARCHAR(64),
    status               VARCHAR(32) NOT NULL DEFAULT 'active',
    total_messages       INTEGER DEFAULT 0,
    total_tokens         INTEGER DEFAULT 0,
    last_activity_at     TIMESTAMP DEFAULT NOW(),
    metadata             JSONB,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_session_agent ON t_memory_session (agent_id);
CREATE INDEX idx_session_user ON t_memory_session (user_id);

-- 5. 记忆统计�?
CREATE TABLE t_memory_stats (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id      VARCHAR(64) NOT NULL,
    stat_date        DATE NOT NULL,
    memory_type      VARCHAR(32) NOT NULL,
    entry_count      INTEGER NOT NULL DEFAULT 0,
    total_tokens     INTEGER NOT NULL DEFAULT 0,
    query_count      INTEGER NOT NULL DEFAULT 0,
    hit_count        INTEGER NOT NULL DEFAULT 0,
    metadata         JSONB,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(session_id, stat_date, memory_type)
);

-- 6. 记忆任务�?
CREATE TABLE t_memory_task (
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

CREATE INDEX idx_task_status ON t_memory_task (status, priority DESC, created_at);