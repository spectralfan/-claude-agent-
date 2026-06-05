-- 为已有库增加「任务级工作区根」列（网页选择本地 IDEA 工程）
ALTER TABLE t_coding_task
    ADD COLUMN IF NOT EXISTS workspace_root VARCHAR(500);
