-- ============================================================
-- v001_coding_task + mcp_call_log.sql
-- Coding 任务表和 MCP 调用记录表
-- 执行顺序: v000 → v001 → v002 → v003 → memory_hub
-- ============================================================

-- Coding 任务表
CREATE TABLE IF NOT EXISTS t_coding_task (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id       VARCHAR(64) NOT NULL,
    agent_id         VARCHAR(100) NOT NULL,
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

-- MCP 工具调用记录表
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