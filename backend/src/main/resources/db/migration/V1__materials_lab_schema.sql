-- 材料实验助手 v1.0.0 首次正式发布的完整物理结构。
-- 本迁移仅适用于首次部署的空数据库；发布后不得改写，后续变更以 V2 及更高版本追加。
CREATE TABLE organization (
  id UUID PRIMARY KEY, organization_code VARCHAR(32) NOT NULL UNIQUE, name VARCHAR(200) NOT NULL,
  parent_id UUID REFERENCES organization(id), status VARCHAR(16) NOT NULL DEFAULT 'active', is_platform BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE user_account (
  id UUID PRIMARY KEY, organization_id UUID NOT NULL REFERENCES organization(id), password VARCHAR(128) NOT NULL,
  last_login TIMESTAMPTZ, is_superuser BOOLEAN NOT NULL DEFAULT FALSE, username VARCHAR(64) NOT NULL,
  first_name VARCHAR(150) NOT NULL DEFAULT '', last_name VARCHAR(150) NOT NULL DEFAULT '', email VARCHAR(254) NOT NULL DEFAULT '',
  is_staff BOOLEAN NOT NULL DEFAULT FALSE, is_active BOOLEAN NOT NULL DEFAULT TRUE, date_joined TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  display_name VARCHAR(100) NOT NULL, status VARCHAR(16) NOT NULL DEFAULT 'active', is_super_admin BOOLEAN NOT NULL DEFAULT FALSE,
  is_platform_admin BOOLEAN NOT NULL DEFAULT FALSE, session_version BIGINT NOT NULL DEFAULT 0,
  deleted_at TIMESTAMPTZ, deleted_by_id UUID REFERENCES user_account(id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_user_account_org_username UNIQUE (organization_id, username),
  CONSTRAINT uk_user_account_username UNIQUE (username),
  CONSTRAINT ck_user_account_platform_not_super CHECK (NOT (is_platform_admin AND is_super_admin)),
  CONSTRAINT ck_user_account_status CHECK (status IN ('active', 'locked', 'disabled', 'deleted')),
  CONSTRAINT ck_user_account_active_state CHECK (is_active = (status = 'active')),
  CONSTRAINT ck_user_account_deleted_state CHECK ((status = 'deleted') = (deleted_at IS NOT NULL))
);
CREATE TABLE role (
  id UUID PRIMARY KEY, organization_id UUID NOT NULL REFERENCES organization(id), role_code VARCHAR(64) NOT NULL,
  name VARCHAR(100) NOT NULL, description VARCHAR(500) NOT NULL DEFAULT '', is_system BOOLEAN NOT NULL DEFAULT FALSE,
  status VARCHAR(16) NOT NULL DEFAULT 'active', created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_role_org_code UNIQUE (organization_id, role_code)
);
CREATE TABLE permission (
  id UUID PRIMARY KEY, permission_code VARCHAR(100) NOT NULL UNIQUE, name VARCHAR(100) NOT NULL,
  module_code VARCHAR(64) NOT NULL, description VARCHAR(500) NOT NULL DEFAULT ''
);
CREATE TABLE user_role (
  id UUID PRIMARY KEY, organization_id UUID NOT NULL REFERENCES organization(id), user_id UUID NOT NULL REFERENCES user_account(id),
  role_id UUID NOT NULL REFERENCES role(id), created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, created_by_id UUID REFERENCES user_account(id),
  CONSTRAINT uk_user_role_user_role UNIQUE (user_id, role_id)
);
CREATE TABLE registration_invitation (
  id UUID PRIMARY KEY, organization_id UUID NOT NULL REFERENCES organization(id), role_id UUID NOT NULL REFERENCES role(id),
  created_by_id UUID NOT NULL REFERENCES user_account(id), code_hash CHAR(64) NOT NULL UNIQUE,
  status VARCHAR(16) NOT NULL DEFAULT 'active', expires_at TIMESTAMPTZ NOT NULL,
  used_at TIMESTAMPTZ, used_by_id UUID REFERENCES user_account(id), created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT ck_registration_invitation_status CHECK (status IN ('active', 'used', 'revoked')),
  CONSTRAINT ck_registration_invitation_used_state CHECK ((status = 'used') = (used_at IS NOT NULL AND used_by_id IS NOT NULL))
);
CREATE TABLE role_permission (
  id UUID PRIMARY KEY, role_id UUID NOT NULL REFERENCES role(id), permission_id UUID NOT NULL REFERENCES permission(id),
  CONSTRAINT uk_role_permission_role_permission UNIQUE (role_id, permission_id)
);
CREATE TABLE business_number_sequence (
  id UUID PRIMARY KEY, organization_id UUID NOT NULL REFERENCES organization(id), business_type VARCHAR(32) NOT NULL,
  period_key VARCHAR(16) NOT NULL, current_value BIGINT NOT NULL DEFAULT 0, updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_business_number_sequence_scope UNIQUE (organization_id, business_type, period_key)
);
CREATE TABLE project (
  id UUID PRIMARY KEY, organization_id UUID NOT NULL REFERENCES organization(id), project_no VARCHAR(32) NOT NULL,
  name VARCHAR(200) NOT NULL, project_type_code VARCHAR(64) NOT NULL, description TEXT NOT NULL DEFAULT '', current_stage VARCHAR(64) NOT NULL DEFAULT '方案设计',
  progress_percent SMALLINT NOT NULL DEFAULT 0, document_count INTEGER NOT NULL DEFAULT 0, experiment_count INTEGER NOT NULL DEFAULT 0, data_resource_count INTEGER NOT NULL DEFAULT 0,
  objectives JSONB NOT NULL DEFAULT '[]', milestones JSONB NOT NULL DEFAULT '[]', status VARCHAR(24) NOT NULL DEFAULT 'not_started', owner_id UUID NOT NULL REFERENCES user_account(id),
  planned_start_date TIMESTAMPTZ, planned_end_date TIMESTAMPTZ, actual_end_at TIMESTAMPTZ, archived_at TIMESTAMPTZ, version INTEGER NOT NULL DEFAULT 1,
  created_by_id UUID REFERENCES user_account(id), updated_by_id UUID REFERENCES user_account(id), created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_project_org_project_no UNIQUE (organization_id, project_no),
  CONSTRAINT ck_project_progress_percent_range CHECK (progress_percent BETWEEN 0 AND 100)
);
CREATE TABLE project_member (
  id UUID PRIMARY KEY, organization_id UUID NOT NULL REFERENCES organization(id), project_id UUID NOT NULL REFERENCES project(id), user_id UUID NOT NULL REFERENCES user_account(id),
  member_role VARCHAR(24) NOT NULL, joined_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, created_by_id UUID REFERENCES user_account(id),
  CONSTRAINT uk_project_member_project_user UNIQUE (project_id, user_id)
);
CREATE TABLE project_follow (
  id UUID PRIMARY KEY, project_id UUID NOT NULL REFERENCES project(id), user_id UUID NOT NULL REFERENCES user_account(id), created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_project_follow_project_user UNIQUE (project_id, user_id)
);
CREATE TABLE project_document (
  id UUID PRIMARY KEY, organization_id UUID NOT NULL REFERENCES organization(id), project_id UUID NOT NULL REFERENCES project(id), category VARCHAR(32) NOT NULL,
  name VARCHAR(255) NOT NULL, version_label VARCHAR(32) NOT NULL, file VARCHAR(500) NOT NULL, mime_type VARCHAR(120) NOT NULL DEFAULT '', file_size BIGINT NOT NULL,
  uploaded_by_id UUID NOT NULL REFERENCES user_account(id), preview_file VARCHAR(500), preview_mime_type VARCHAR(120) NOT NULL DEFAULT '',
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE project_document_content (
  document_id UUID PRIMARY KEY REFERENCES project_document(id) ON DELETE CASCADE,
  content BYTEA NOT NULL
);
CREATE TABLE experiment (
  id UUID PRIMARY KEY, organization_id UUID NOT NULL REFERENCES organization(id), project_id UUID NOT NULL REFERENCES project(id), experiment_no VARCHAR(32) NOT NULL,
  name VARCHAR(200) NOT NULL, experiment_type VARCHAR(64) NOT NULL, phase VARCHAR(64) NOT NULL DEFAULT '方案设计', status VARCHAR(24) NOT NULL DEFAULT 'not_started', purpose TEXT NOT NULL DEFAULT '',
  estimated_start TIMESTAMPTZ, estimated_end TIMESTAMPTZ, started_at TIMESTAMPTZ, completed_at TIMESTAMPTZ, owner_id UUID NOT NULL REFERENCES user_account(id), version INTEGER NOT NULL DEFAULT 1,
  created_by_id UUID REFERENCES user_account(id), updated_by_id UUID REFERENCES user_account(id), created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_experiment_org_no UNIQUE (organization_id, experiment_no)
);
CREATE TABLE experiment_record (
  id UUID PRIMARY KEY, experiment_id UUID NOT NULL UNIQUE REFERENCES experiment(id), formula_columns JSONB NOT NULL DEFAULT '[]', formula_rows JSONB NOT NULL DEFAULT '[]',
  extra_tables JSONB NOT NULL DEFAULT '[]', process_text TEXT NOT NULL DEFAULT '', extra_processes JSONB NOT NULL DEFAULT '[]', result_text TEXT NOT NULL DEFAULT '',
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE experiment_participant (
  id UUID PRIMARY KEY, organization_id UUID NOT NULL REFERENCES organization(id), experiment_id UUID NOT NULL REFERENCES experiment(id), user_id UUID NOT NULL REFERENCES user_account(id),
  participant_role VARCHAR(24) NOT NULL DEFAULT 'participant', joined_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, created_by_id UUID REFERENCES user_account(id),
  CONSTRAINT uk_experiment_participant_user UNIQUE (experiment_id, user_id)
);
CREATE TABLE experiment_attachment (
  id UUID PRIMARY KEY, organization_id UUID NOT NULL REFERENCES organization(id), experiment_id UUID NOT NULL REFERENCES experiment(id), kind VARCHAR(24) NOT NULL,
  name VARCHAR(255) NOT NULL, file VARCHAR(500) NOT NULL, mime_type VARCHAR(120) NOT NULL DEFAULT '', file_size BIGINT NOT NULL, uploaded_by_id UUID NOT NULL REFERENCES user_account(id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE experiment_attachment_content (
  attachment_id UUID PRIMARY KEY REFERENCES experiment_attachment(id) ON DELETE CASCADE,
  content BYTEA NOT NULL
);
CREATE TABLE idempotency_request (
  id UUID PRIMARY KEY, organization_id UUID NOT NULL, user_id UUID NOT NULL REFERENCES user_account(id), idempotency_key VARCHAR(128) NOT NULL, route_key VARCHAR(200) NOT NULL,
  request_hash VARCHAR(64) NOT NULL, status VARCHAR(16) NOT NULL, response_status SMALLINT, response_body JSONB, created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, expires_at TIMESTAMPTZ NOT NULL,
  CONSTRAINT uk_idempotency_request_scope_key UNIQUE (organization_id, user_id, route_key, idempotency_key)
);
CREATE TABLE business_operation_log (
  id UUID PRIMARY KEY, organization_id UUID NOT NULL, actor_id UUID REFERENCES user_account(id), domain VARCHAR(32) NOT NULL, object_id UUID NOT NULL,
  object_no VARCHAR(64) NOT NULL DEFAULT '', action_type VARCHAR(64) NOT NULL, description VARCHAR(1000) NOT NULL, changes JSONB NOT NULL DEFAULT '{}', created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_project_org_status_updated ON project (organization_id, status, updated_at DESC);
CREATE INDEX idx_experiment_org_status ON experiment (organization_id, status, updated_at DESC);
CREATE INDEX idx_operation_log_object ON business_operation_log (organization_id, domain, object_id, created_at DESC);
CREATE INDEX idx_experiment_participant_experiment_id ON experiment_participant (experiment_id);
CREATE INDEX idx_experiment_participant_user_id ON experiment_participant (user_id);
CREATE INDEX idx_experiment_attachment_experiment_id ON experiment_attachment (experiment_id);
CREATE INDEX idx_registration_invitation_org_status_expires ON registration_invitation (organization_id, status, expires_at);
CREATE INDEX idx_user_account_org_status ON user_account (organization_id, status);
CREATE UNIQUE INDEX uk_organization_single_platform ON organization ((is_platform)) WHERE is_platform;
CREATE UNIQUE INDEX uk_organization_id_is_platform ON organization (id, is_platform);
ALTER TABLE user_account
  ADD CONSTRAINT fk_user_account_platform_membership
  FOREIGN KEY (organization_id, is_platform_admin)
  REFERENCES organization (id, is_platform);

COMMENT ON TABLE organization IS '组织及组织层级';
COMMENT ON COLUMN organization.id IS '组织主键';
COMMENT ON COLUMN organization.organization_code IS '组织业务编码';
COMMENT ON COLUMN organization.name IS '组织名称';
COMMENT ON COLUMN organization.parent_id IS '上级组织主键';
COMMENT ON COLUMN organization.status IS '组织状态';
COMMENT ON COLUMN organization.is_platform IS '是否内部平台控制面组织；平台组织不属于业务租户且不显示在租户目录';
COMMENT ON COLUMN organization.created_at IS '创建时刻';
COMMENT ON COLUMN organization.updated_at IS '最近更新时刻';

COMMENT ON TABLE user_account IS '本地登录用户账号';
COMMENT ON COLUMN user_account.id IS '用户主键';
COMMENT ON COLUMN user_account.organization_id IS '所属组织主键';
COMMENT ON COLUMN user_account.password IS '密码哈希';
COMMENT ON COLUMN user_account.last_login IS '最后登录时刻';
COMMENT ON COLUMN user_account.is_superuser IS '兼容身份超级用户标记';
COMMENT ON COLUMN user_account.username IS '全局唯一登录名';
COMMENT ON COLUMN user_account.first_name IS '名字';
COMMENT ON COLUMN user_account.last_name IS '姓氏';
COMMENT ON COLUMN user_account.email IS '邮箱地址';
COMMENT ON COLUMN user_account.is_staff IS '兼容身份后台人员标记';
COMMENT ON COLUMN user_account.is_active IS '账号是否启用';
COMMENT ON COLUMN user_account.date_joined IS '账号注册时刻';
COMMENT ON COLUMN user_account.display_name IS '显示名称';
COMMENT ON COLUMN user_account.status IS '业务状态';
COMMENT ON COLUMN user_account.is_super_admin IS '业务超级管理员标记';
COMMENT ON COLUMN user_account.is_platform_admin IS '平台管理员标记，仅用于组织开通等控制面操作';
COMMENT ON COLUMN user_account.session_version IS '会话版本；权限或口令变更后递增以失效旧会话';
COMMENT ON COLUMN user_account.deleted_at IS '逻辑删除时刻；非空表示账号已删除且不可登录';
COMMENT ON COLUMN user_account.deleted_by_id IS '执行逻辑删除的当前组织超级管理员主键';
COMMENT ON COLUMN user_account.created_at IS '创建时刻';
COMMENT ON COLUMN user_account.updated_at IS '最近更新时刻';

COMMENT ON TABLE role IS '组织内角色';
COMMENT ON COLUMN role.id IS '角色主键';
COMMENT ON COLUMN role.organization_id IS '所属组织主键';
COMMENT ON COLUMN role.role_code IS '角色编码';
COMMENT ON COLUMN role.name IS '角色名称';
COMMENT ON COLUMN role.description IS '角色说明';
COMMENT ON COLUMN role.is_system IS '是否系统内置角色';
COMMENT ON COLUMN role.status IS '角色状态';
COMMENT ON COLUMN role.created_at IS '创建时刻';
COMMENT ON COLUMN role.updated_at IS '最近更新时刻';

COMMENT ON TABLE permission IS '权限字典';
COMMENT ON COLUMN permission.id IS '权限主键';
COMMENT ON COLUMN permission.permission_code IS '权限编码';
COMMENT ON COLUMN permission.name IS '权限名称';
COMMENT ON COLUMN permission.module_code IS '所属模块编码';
COMMENT ON COLUMN permission.description IS '权限说明';

COMMENT ON TABLE user_role IS '用户与角色关系';
COMMENT ON COLUMN user_role.id IS '关系主键';
COMMENT ON COLUMN user_role.organization_id IS '所属组织主键';
COMMENT ON COLUMN user_role.user_id IS '用户主键';
COMMENT ON COLUMN user_role.role_id IS '角色主键';
COMMENT ON COLUMN user_role.created_at IS '创建时刻';
COMMENT ON COLUMN user_role.created_by_id IS '创建人主键';

COMMENT ON TABLE registration_invitation IS '组织管理员签发的一次性注册邀请码';
COMMENT ON COLUMN registration_invitation.id IS '邀请码主键';
COMMENT ON COLUMN registration_invitation.organization_id IS '目标组织主键';
COMMENT ON COLUMN registration_invitation.role_id IS '注册后绑定的角色主键';
COMMENT ON COLUMN registration_invitation.created_by_id IS '签发管理员主键';
COMMENT ON COLUMN registration_invitation.code_hash IS '邀请码 SHA-256 哈希，不保存明文';
COMMENT ON COLUMN registration_invitation.status IS '邀请码状态：active、used、revoked';
COMMENT ON COLUMN registration_invitation.expires_at IS '过期时刻';
COMMENT ON COLUMN registration_invitation.used_at IS '使用时刻';
COMMENT ON COLUMN registration_invitation.used_by_id IS '使用后创建的用户主键';
COMMENT ON COLUMN registration_invitation.created_at IS '签发时刻';

COMMENT ON TABLE role_permission IS '角色与权限关系';
COMMENT ON COLUMN role_permission.id IS '关系主键';
COMMENT ON COLUMN role_permission.role_id IS '角色主键';
COMMENT ON COLUMN role_permission.permission_id IS '权限主键';

COMMENT ON TABLE business_number_sequence IS '业务编号序列';
COMMENT ON COLUMN business_number_sequence.id IS '序列主键';
COMMENT ON COLUMN business_number_sequence.organization_id IS '所属组织主键';
COMMENT ON COLUMN business_number_sequence.business_type IS '业务类型';
COMMENT ON COLUMN business_number_sequence.period_key IS '编号周期键';
COMMENT ON COLUMN business_number_sequence.current_value IS '当前序号';
COMMENT ON COLUMN business_number_sequence.updated_at IS '最近更新时刻';

COMMENT ON TABLE project IS '项目主数据';
COMMENT ON COLUMN project.id IS '项目主键';
COMMENT ON COLUMN project.organization_id IS '所属组织主键';
COMMENT ON COLUMN project.project_no IS '组织内项目编号';
COMMENT ON COLUMN project.name IS '项目名称';
COMMENT ON COLUMN project.project_type_code IS '项目类型编码';
COMMENT ON COLUMN project.description IS '项目说明';
COMMENT ON COLUMN project.current_stage IS '当前阶段';
COMMENT ON COLUMN project.progress_percent IS '进度百分比';
COMMENT ON COLUMN project.document_count IS '文档数量';
COMMENT ON COLUMN project.experiment_count IS '实验数量';
COMMENT ON COLUMN project.data_resource_count IS '数据资源数量';
COMMENT ON COLUMN project.objectives IS '项目目标 JSON 数组';
COMMENT ON COLUMN project.milestones IS '里程碑 JSON 数组';
COMMENT ON COLUMN project.status IS '项目状态';
COMMENT ON COLUMN project.owner_id IS '项目负责人主键';
COMMENT ON COLUMN project.planned_start_date IS '计划开始时刻';
COMMENT ON COLUMN project.planned_end_date IS '计划结束时刻';
COMMENT ON COLUMN project.actual_end_at IS '实际结束时刻';
COMMENT ON COLUMN project.archived_at IS '归档时刻';
COMMENT ON COLUMN project.version IS '乐观锁版本';
COMMENT ON COLUMN project.created_by_id IS '创建人主键';
COMMENT ON COLUMN project.updated_by_id IS '更新人主键';
COMMENT ON COLUMN project.created_at IS '创建时刻';
COMMENT ON COLUMN project.updated_at IS '最近更新时刻';

COMMENT ON TABLE project_member IS '项目成员关系';
COMMENT ON COLUMN project_member.id IS '关系主键';
COMMENT ON COLUMN project_member.organization_id IS '所属组织主键';
COMMENT ON COLUMN project_member.project_id IS '项目主键';
COMMENT ON COLUMN project_member.user_id IS '用户主键';
COMMENT ON COLUMN project_member.member_role IS '项目成员角色';
COMMENT ON COLUMN project_member.joined_at IS '加入时刻';
COMMENT ON COLUMN project_member.created_by_id IS '创建人主键';

COMMENT ON TABLE project_follow IS '项目关注关系';
COMMENT ON COLUMN project_follow.id IS '关系主键';
COMMENT ON COLUMN project_follow.project_id IS '项目主键';
COMMENT ON COLUMN project_follow.user_id IS '用户主键';
COMMENT ON COLUMN project_follow.created_at IS '创建时刻';

COMMENT ON TABLE project_document IS '项目文档元数据';
COMMENT ON COLUMN project_document.id IS '文档主键';
COMMENT ON COLUMN project_document.organization_id IS '所属组织主键';
COMMENT ON COLUMN project_document.project_id IS '项目主键';
COMMENT ON COLUMN project_document.category IS '文档分类';
COMMENT ON COLUMN project_document.name IS '原始文件名';
COMMENT ON COLUMN project_document.version_label IS '业务版本标签';
COMMENT ON COLUMN project_document.file IS '文件存储标识';
COMMENT ON COLUMN project_document.mime_type IS '服务端识别的媒体类型';
COMMENT ON COLUMN project_document.file_size IS '文件字节数';
COMMENT ON COLUMN project_document.uploaded_by_id IS '上传人主键';
COMMENT ON COLUMN project_document.preview_file IS '预览文件存储标识';
COMMENT ON COLUMN project_document.preview_mime_type IS '预览媒体类型';
COMMENT ON COLUMN project_document.created_at IS '创建时刻';
COMMENT ON COLUMN project_document.updated_at IS '最近更新时刻';

COMMENT ON TABLE project_document_content IS '项目文档历史二进制正文；首版起新上传正文保存于 RustFS';
COMMENT ON COLUMN project_document_content.document_id IS '文档主键';
COMMENT ON COLUMN project_document_content.content IS '文档二进制正文';

COMMENT ON TABLE experiment IS '实验计划主数据';
COMMENT ON COLUMN experiment.id IS '实验主键';
COMMENT ON COLUMN experiment.organization_id IS '所属组织主键';
COMMENT ON COLUMN experiment.project_id IS '所属项目主键';
COMMENT ON COLUMN experiment.experiment_no IS '组织内实验编号';
COMMENT ON COLUMN experiment.name IS '实验名称';
COMMENT ON COLUMN experiment.experiment_type IS '实验类型';
COMMENT ON COLUMN experiment.phase IS '实验阶段';
COMMENT ON COLUMN experiment.status IS '实验状态';
COMMENT ON COLUMN experiment.purpose IS '实验目的';
COMMENT ON COLUMN experiment.estimated_start IS '预计开始时刻';
COMMENT ON COLUMN experiment.estimated_end IS '预计结束时刻';
COMMENT ON COLUMN experiment.started_at IS '实际开始时刻';
COMMENT ON COLUMN experiment.completed_at IS '实际完成时刻';
COMMENT ON COLUMN experiment.owner_id IS '实验负责人主键';
COMMENT ON COLUMN experiment.version IS '乐观锁版本';
COMMENT ON COLUMN experiment.created_by_id IS '创建人主键';
COMMENT ON COLUMN experiment.updated_by_id IS '更新人主键';
COMMENT ON COLUMN experiment.created_at IS '创建时刻';
COMMENT ON COLUMN experiment.updated_at IS '最近更新时刻';

COMMENT ON TABLE experiment_record IS '实验电子记录正文';
COMMENT ON COLUMN experiment_record.id IS '记录主键';
COMMENT ON COLUMN experiment_record.experiment_id IS '实验主键';
COMMENT ON COLUMN experiment_record.formula_columns IS '配方列 JSON 数组';
COMMENT ON COLUMN experiment_record.formula_rows IS '配方行 JSON 数组';
COMMENT ON COLUMN experiment_record.extra_tables IS '附加表格 JSON 数组';
COMMENT ON COLUMN experiment_record.process_text IS '实验过程文本';
COMMENT ON COLUMN experiment_record.extra_processes IS '附加过程 JSON 数组';
COMMENT ON COLUMN experiment_record.result_text IS '实验结果文本';
COMMENT ON COLUMN experiment_record.created_at IS '创建时刻';
COMMENT ON COLUMN experiment_record.updated_at IS '最近更新时刻';

COMMENT ON TABLE experiment_participant IS '实验参与人关系';
COMMENT ON COLUMN experiment_participant.id IS '关系主键';
COMMENT ON COLUMN experiment_participant.organization_id IS '所属组织主键';
COMMENT ON COLUMN experiment_participant.experiment_id IS '实验主键';
COMMENT ON COLUMN experiment_participant.user_id IS '用户主键';
COMMENT ON COLUMN experiment_participant.participant_role IS '参与角色';
COMMENT ON COLUMN experiment_participant.joined_at IS '加入时刻';
COMMENT ON COLUMN experiment_participant.created_by_id IS '创建人主键';

COMMENT ON TABLE experiment_attachment IS '实验附件元数据';
COMMENT ON COLUMN experiment_attachment.id IS '附件主键';
COMMENT ON COLUMN experiment_attachment.organization_id IS '所属组织主键';
COMMENT ON COLUMN experiment_attachment.experiment_id IS '实验主键';
COMMENT ON COLUMN experiment_attachment.kind IS '附件用途';
COMMENT ON COLUMN experiment_attachment.name IS '原始文件名';
COMMENT ON COLUMN experiment_attachment.file IS '文件存储标识';
COMMENT ON COLUMN experiment_attachment.mime_type IS '服务端识别的媒体类型';
COMMENT ON COLUMN experiment_attachment.file_size IS '文件字节数';
COMMENT ON COLUMN experiment_attachment.uploaded_by_id IS '上传人主键';
COMMENT ON COLUMN experiment_attachment.created_at IS '创建时刻';
COMMENT ON COLUMN experiment_attachment.updated_at IS '最近更新时刻';

COMMENT ON TABLE experiment_attachment_content IS '实验附件历史二进制正文；首版起新上传正文保存于 RustFS';
COMMENT ON COLUMN experiment_attachment_content.attachment_id IS '实验附件主键';
COMMENT ON COLUMN experiment_attachment_content.content IS '附件二进制正文';

COMMENT ON TABLE idempotency_request IS '请求幂等记录';
COMMENT ON COLUMN idempotency_request.id IS '幂等记录主键';
COMMENT ON COLUMN idempotency_request.organization_id IS '所属组织主键';
COMMENT ON COLUMN idempotency_request.user_id IS '请求用户主键';
COMMENT ON COLUMN idempotency_request.idempotency_key IS '客户端幂等键';
COMMENT ON COLUMN idempotency_request.route_key IS '请求路由键';
COMMENT ON COLUMN idempotency_request.request_hash IS '请求内容摘要';
COMMENT ON COLUMN idempotency_request.status IS '处理状态';
COMMENT ON COLUMN idempotency_request.response_status IS '响应状态码';
COMMENT ON COLUMN idempotency_request.response_body IS '响应正文';
COMMENT ON COLUMN idempotency_request.created_at IS '创建时刻';
COMMENT ON COLUMN idempotency_request.expires_at IS '过期时刻';

COMMENT ON TABLE business_operation_log IS '业务操作审计日志';
COMMENT ON COLUMN business_operation_log.id IS '日志主键';
COMMENT ON COLUMN business_operation_log.organization_id IS '所属组织主键';
COMMENT ON COLUMN business_operation_log.actor_id IS '操作者主键';
COMMENT ON COLUMN business_operation_log.domain IS '业务领域';
COMMENT ON COLUMN business_operation_log.object_id IS '对象主键';
COMMENT ON COLUMN business_operation_log.object_no IS '对象业务编号';
COMMENT ON COLUMN business_operation_log.action_type IS '操作类型';
COMMENT ON COLUMN business_operation_log.description IS '操作说明';
COMMENT ON COLUMN business_operation_log.changes IS '变更内容 JSON';
COMMENT ON COLUMN business_operation_log.created_at IS '创建时刻';
