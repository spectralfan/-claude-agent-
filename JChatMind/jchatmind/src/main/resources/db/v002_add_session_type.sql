-- Add type column to chat_session (CHAT / CODING)
ALTER TABLE chat_session ADD COLUMN IF NOT EXISTS type VARCHAR(10) NOT NULL DEFAULT 'CHAT';

-- Backfill existing coding sessions (those with workspaceRoot in metadata)
UPDATE chat_session
SET type = 'CODING'
WHERE metadata IS NOT NULL
  AND metadata::jsonb->'coding'->>'workspaceRoot' IS NOT NULL;