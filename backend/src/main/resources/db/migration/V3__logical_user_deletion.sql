-- 普通组织用户逻辑删除：保留历史外键与审计链，禁止已删除账号继续认证或恢复使用原身份。
ALTER TABLE user_account
  ADD COLUMN deleted_at TIMESTAMPTZ,
  ADD COLUMN deleted_by_id UUID REFERENCES user_account(id);

-- 首次引入状态约束时，将早期数据收敛为既有启用标记所表达的状态，避免留下双重状态歧义。
UPDATE user_account
SET status = CASE WHEN is_active THEN 'active' ELSE 'disabled' END
WHERE status NOT IN ('active', 'locked', 'disabled', 'deleted');

UPDATE user_account
SET is_active = (status = 'active')
WHERE is_active IS DISTINCT FROM (status = 'active');

ALTER TABLE user_account
  ADD CONSTRAINT ck_user_account_status
    CHECK (status IN ('active', 'locked', 'disabled', 'deleted')),
  ADD CONSTRAINT ck_user_account_active_state
    CHECK (is_active = (status = 'active')),
  ADD CONSTRAINT ck_user_account_deleted_state
    CHECK ((status = 'deleted') = (deleted_at IS NOT NULL));

CREATE INDEX idx_user_account_org_status ON user_account (organization_id, status);

COMMENT ON COLUMN user_account.deleted_at IS '逻辑删除时刻；非空表示账号已删除且不可登录';
COMMENT ON COLUMN user_account.deleted_by_id IS '执行逻辑删除的当前组织超级管理员主键';
