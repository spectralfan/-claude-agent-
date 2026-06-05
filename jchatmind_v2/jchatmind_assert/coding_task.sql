-- ============================================================
-- coding_task.sql
-- Coding Agent 任务表（后端 MVP）
-- ============================================================

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
