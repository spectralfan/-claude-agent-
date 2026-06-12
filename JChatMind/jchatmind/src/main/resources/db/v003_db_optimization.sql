-- v003_db_optimization.sql
-- 1. 添加核心查询索引
CREATE INDEX IF NOT EXISTS idx_chat_message_session ON chat_message (session_id);
CREATE INDEX IF NOT EXISTS idx_chat_session_type ON chat_session (type);
CREATE INDEX IF NOT EXISTS idx_chat_session_agent ON chat_session (agent_id);

-- 2. 清理无用电商表
DROP TABLE IF EXISTS t_product_category_relation CASCADE;
DROP TABLE IF EXISTS t_product_category CASCADE;
DROP TABLE IF EXISTS t_comment_topic_mapping CASCADE;
DROP TABLE IF EXISTS t_comment_summary_daily CASCADE;
DROP TABLE IF EXISTS t_comment_topic CASCADE;
DROP TABLE IF EXISTS t_comment CASCADE;
DROP TABLE IF EXISTS t_shipment CASCADE;
DROP TABLE IF EXISTS t_payment CASCADE;
DROP TABLE IF EXISTS t_order_item CASCADE;
DROP TABLE IF EXISTS t_order_header CASCADE;
DROP TABLE IF EXISTS t_product CASCADE;
DROP TABLE IF EXISTS t_user_role CASCADE;
DROP TABLE IF EXISTS t_role CASCADE;
DROP TABLE IF EXISTS t_app_user CASCADE;

-- 3. 统一字段类型
DELETE FROM t_coding_task WHERE session_id !~ '^[0-9a-f-]{36}$';
ALTER TABLE t_coding_task ALTER COLUMN session_id TYPE UUID USING session_id::uuid;
ALTER TABLE t_coding_task ALTER COLUMN agent_id TYPE UUID USING agent_id::uuid;