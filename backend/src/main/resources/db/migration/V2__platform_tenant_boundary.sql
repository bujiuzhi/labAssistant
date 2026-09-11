-- 平台控制面与业务租户分离：平台管理员必须归属唯一内部平台组织，不能兼任租户超级管理员。
ALTER TABLE organization ADD COLUMN is_platform BOOLEAN NOT NULL DEFAULT FALSE;
CREATE UNIQUE INDEX uk_organization_single_platform ON organization ((is_platform)) WHERE is_platform;
ALTER TABLE user_account ADD CONSTRAINT ck_user_account_platform_not_super CHECK (NOT (is_platform_admin AND is_super_admin)) NOT VALID;
COMMENT ON COLUMN organization.is_platform IS '是否内部平台控制面组织；平台组织不属于业务租户且不显示在租户目录';
