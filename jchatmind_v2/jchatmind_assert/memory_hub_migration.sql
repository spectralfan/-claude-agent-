-- ============================================================
-- memory_hub_migration.sql
-- 将现有 chat_message 历史数据迁移到 Memory Hub 的 t_memory_entry
-- ------------------------------------------------------------
-- 说明：
--   1. chat_message.session_id 为 uuid，t_memory_entry.session_id 为 varchar(64)，迁移时转 text。
--   2. 全部迁移为 WORKING 层，后续由 MemoryAgent 整理升级/归档。
--   3. 通过 NOT EXISTS 保证幂等：已迁移过的 session 不会重复导入。
--   4. tool_calls 从 chat_message.metadata 的 toolCalls 字段抽取。
-- 执行（Docker 容器内）：
--   docker exec -e PGPASSWORD=123456 postgres \
--     psql -U postgres -d jchatmind -f /tmp/memory_hub_migration.sql
-- ============================================================

INSERT INTO t_memory_entry
    (session_id,
     memory_type,
     role,
     content,
     importance,
     tool_calls,
     metadata,
     created_at,
     updated_at)
SELECT cm.session_id::text                AS session_id,
       'WORKING'                          AS memory_type,
       cm.role                            AS role,
       COALESCE(cm.content, '')           AS content,
       0                                  AS importance,
       cm.metadata -> 'toolCalls'         AS tool_calls,
       cm.metadata                        AS metadata,
       cm.created_at                      AS created_at,
       cm.updated_at                      AS updated_at
FROM chat_message cm
WHERE NOT EXISTS (
    SELECT 1
    FROM t_memory_entry m
    WHERE m.session_id = cm.session_id::text
);

-- 为已迁移的 session 建立 Memory Hub 会话记录（若不存在）
INSERT INTO t_memory_session (session_id, status, total_messages)
SELECT cm.session_id::text,
       'active',
       COUNT(*)
FROM chat_message cm
WHERE NOT EXISTS (
    SELECT 1 FROM t_memory_session s WHERE s.session_id = cm.session_id::text
)
GROUP BY cm.session_id;
