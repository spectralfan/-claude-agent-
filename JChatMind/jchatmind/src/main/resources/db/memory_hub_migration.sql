-- 将现有 chat_message 历史迁移到 Memory Hub（幂等）
-- 执行：psql -U postgres -d jchatmind -f src/main/resources/db/memory_hub_migration.sql

INSERT INTO t_memory_entry
    (session_id, memory_type, role, content, importance, tool_calls, metadata, created_at, updated_at)
SELECT cm.session_id::text,
       'WORKING',
       cm.role,
       COALESCE(cm.content, ''),
       0,
       cm.metadata -> 'toolCalls',
       cm.metadata,
       cm.created_at,
       cm.updated_at
FROM chat_message cm
WHERE NOT EXISTS (
    SELECT 1 FROM t_memory_entry m WHERE m.session_id = cm.session_id::text
);

INSERT INTO t_memory_session (session_id, status, total_messages)
SELECT cm.session_id::text, 'active', COUNT(*)
FROM chat_message cm
WHERE NOT EXISTS (
    SELECT 1 FROM t_memory_session s WHERE s.session_id = cm.session_id::text
)
GROUP BY cm.session_id;
