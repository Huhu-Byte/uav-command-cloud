-- V10: 成果归属到工作空间
ALTER TABLE inspection_results ADD COLUMN IF NOT EXISTS workspace_id BIGINT DEFAULT 1;
