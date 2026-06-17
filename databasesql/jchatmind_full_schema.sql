-- ============================================================
-- JChatMindv2 数据库完整架构（一次性还原）
-- 数据库: PostgreSQL 16 + pgvector
-- 生成时间: 2026-06-17 10:45
-- 执行: psql -U postgres -d jchatmind -f jchatmind_full_schema.sql
-- 表数量: 14
-- ============================================================

-- ============================================================
-- 1. 扩展
-- ============================================================
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS vector;

-- ============================================================
-- 2. 核心表
-- ============================================================
CREATE TABLE agent (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name TEXT NOT NULL,
    description TEXT,
    system_prompt TEXT,
    model TEXT,
    allowed_tools JSONB,
    allowed_kbs JSONB,
    chat_options JSONB,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE chat_session (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    agent_id UUID REFERENCES agent(id) ON DELETE SET NULL,
    title TEXT,
    type VARCHAR(10) NOT NULL DEFAULT 'CHAT',
    metadata JSONB,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE chat_message (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID NOT NULL REFERENCES chat_session(id) ON DELETE CASCADE,
    role TEXT NOT NULL,
    content TEXT,
    metadata JSONB,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE knowledge_base (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name TEXT NOT NULL,
    description TEXT,
    metadata JSONB,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE document (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    kb_id UUID NOT NULL REFERENCES knowledge_base(id) ON DELETE CASCADE,
    filename TEXT NOT NULL,
    filetype TEXT,
    size BIGINT,
    metadata JSONB,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE chunk_bge_m3 (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    kb_id UUID NOT NULL REFERENCES knowledge_base(id) ON DELETE CASCADE,
    doc_id UUID NOT NULL REFERENCES document(id) ON DELETE CASCADE,
    content TEXT NOT NULL,
    metadata JSONB,
    embedding VECTOR(1024) NOT NULL,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_chunk_embedding
ON chunk_bge_m3 USING ivfflat (embedding vector_l2_ops) WITH (lists = 100);

-- ============================================================
-- 3. Coding 任务表
-- ============================================================
CREATE TABLE IF NOT EXISTS t_coding_task (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id       UUID NOT NULL,
    agent_id         UUID NOT NULL,
    status           VARCHAR(32) NOT NULL DEFAULT 'pending',
    workspace_path   VARCHAR(500),
    workspace_root   VARCHAR(500),
    started_at       TIMESTAMP DEFAULT NOW(),
    finished_at      TIMESTAMP,
    command          VARCHAR(200),
    result_summary   TEXT,
    metadata         JSONB,
    pending_action   VARCHAR(64),
    pending_payload  JSONB,
    approval_reason  TEXT
);

CREATE INDEX IF NOT EXISTS idx_coding_task_session ON t_coding_task(session_id);
CREATE INDEX IF NOT EXISTS idx_coding_task_status ON t_coding_task(status);

-- ============================================================
-- 4. MCP 工具调用记录表
-- ============================================================
CREATE TABLE IF NOT EXISTS t_mcp_tool_call (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    server_id       VARCHAR(100),
    tool_name       VARCHAR(200) NOT NULL,
    arguments       JSONB,
    result          JSONB,
    error_message   TEXT,
    status          VARCHAR(20) NOT NULL,
    duration_ms     INTEGER,
    session_id      VARCHAR(64),
    agent_id        VARCHAR(100),
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_mcp_call_server ON t_mcp_tool_call (server_id);
CREATE INDEX IF NOT EXISTS idx_mcp_call_tool ON t_mcp_tool_call (tool_name);
CREATE INDEX IF NOT EXISTS idx_mcp_call_created ON t_mcp_tool_call (created_at);

-- ============================================================
-- 5. 记忆系统表 (Memory Hub)
-- ============================================================
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

-- ============================================================
-- 6. 额外索引（原 v003 迁移）
-- ============================================================
CREATE INDEX IF NOT EXISTS idx_chat_message_session ON chat_message (session_id);
CREATE INDEX IF NOT EXISTS idx_chat_session_type ON chat_session (type);
CREATE INDEX IF NOT EXISTS idx_chat_session_agent ON chat_session (agent_id);

-- ============================================================
-- 表清单
-- ============================================================
-- agent                  - Agent 定义
-- chat_session           - 聊天会话 (type: CHAT|CODING)
-- chat_message           - 聊天消息 (role: user|assistant|system|tool)
-- knowledge_base         - 知识库
-- document               - 文档 (文件)
-- chunk_bge_m3           - 知识库向量片段 (pgvector 1024d)
-- t_coding_task          - Coding 任务 (status: pending|running|completed|failed|timeout|waiting_approval|rejected)
-- t_mcp_tool_call        - MCP 工具调用记录
-- t_memory_entry         - 记忆条目
-- t_memory_embedding     - 记忆向量
-- t_memory_context       - 记忆上下文
-- t_memory_session       - 记忆会话
-- t_memory_stats         - 记忆统计
-- t_memory_task          - 记忆整理任务
-- ============================================================