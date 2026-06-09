-- Orchestration DAG tasks (Scheduler / Worker / Reviewer)
CREATE TABLE IF NOT EXISTS t_orchestration_task (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    parent_session_id UUID NOT NULL,
    parent_coding_task_id UUID,
    role TEXT NOT NULL,
    title TEXT NOT NULL,
    goal TEXT NOT NULL,
    constraints TEXT,
    context_files JSONB DEFAULT '[]'::jsonb,
    depends_on JSONB DEFAULT '[]'::jsonb,
    status TEXT NOT NULL DEFAULT 'PENDING',
    depth INT NOT NULL DEFAULT 1,
    spawned_from_task_id UUID,
    worker_agent_id UUID,
    result_summary TEXT,
    error_message TEXT,
    metadata JSONB DEFAULT '{}'::jsonb,
    created_at TIMESTAMP DEFAULT NOW(),
    started_at TIMESTAMP,
    finished_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_orch_task_session ON t_orchestration_task(parent_session_id);
CREATE INDEX IF NOT EXISTS idx_orch_task_status ON t_orchestration_task(parent_session_id, status);
