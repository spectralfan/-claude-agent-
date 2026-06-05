-- ============================================================
-- mcp_call_log.sql
-- MCP 工具调用记录表（精简版：不依赖 Server/Tool 元数据表）
-- 时间列使用 TIMESTAMP 以对齐实体的 LocalDateTime
-- ============================================================

CREATE TABLE IF NOT EXISTS t_mcp_tool_call (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    server_id       VARCHAR(100),                  -- MCP server / 连接名（来自 SDK getServerInfo）
    tool_name       VARCHAR(200) NOT NULL,
    arguments       JSONB,                         -- 调用参数
    result          JSONB,                         -- 调用结果（成功时）
    error_message   TEXT,                          -- 失败信息
    status          VARCHAR(20) NOT NULL,          -- 'success' | 'failed'
    duration_ms     INTEGER,                       -- 耗时
    session_id      VARCHAR(64),                   -- 关联会话（可空）
    agent_id        VARCHAR(100),                  -- 调用 Agent（可空）
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_mcp_call_server ON t_mcp_tool_call (server_id);
CREATE INDEX IF NOT EXISTS idx_mcp_call_tool ON t_mcp_tool_call (tool_name);
CREATE INDEX IF NOT EXISTS idx_mcp_call_created ON t_mcp_tool_call (created_at);
