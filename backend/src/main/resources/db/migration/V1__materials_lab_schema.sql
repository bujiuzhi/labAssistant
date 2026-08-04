-- 材料实验助手 Java 后端初始物理结构；表名与 Django 版本保持兼容。
CREATE TABLE IF NOT EXISTS organization (
  id UUID PRIMARY KEY, organization_code VARCHAR(32) NOT NULL UNIQUE, name VARCHAR(200) NOT NULL,
  parent_id UUID REFERENCES organization(id), status VARCHAR(16) NOT NULL DEFAULT 'active',
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS user_account (
  id UUID PRIMARY KEY, organization_id UUID NOT NULL REFERENCES organization(id), password VARCHAR(128) NOT NULL,
  last_login TIMESTAMPTZ, is_superuser BOOLEAN NOT NULL DEFAULT FALSE, username VARCHAR(64) NOT NULL,
  first_name VARCHAR(150) NOT NULL DEFAULT '', last_name VARCHAR(150) NOT NULL DEFAULT '', email VARCHAR(254) NOT NULL DEFAULT '',
  is_staff BOOLEAN NOT NULL DEFAULT FALSE, is_active BOOLEAN NOT NULL DEFAULT TRUE, date_joined TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  display_name VARCHAR(100) NOT NULL, status VARCHAR(16) NOT NULL DEFAULT 'active', is_super_admin BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_user_account_org_username UNIQUE (organization_id, username)
);
CREATE TABLE IF NOT EXISTS role (
  id UUID PRIMARY KEY, organization_id UUID NOT NULL REFERENCES organization(id), role_code VARCHAR(64) NOT NULL,
  name VARCHAR(100) NOT NULL, description VARCHAR(500) NOT NULL DEFAULT '', is_system BOOLEAN NOT NULL DEFAULT FALSE,
  status VARCHAR(16) NOT NULL DEFAULT 'active', created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_role_org_code UNIQUE (organization_id, role_code)
);
CREATE TABLE IF NOT EXISTS permission (
  id UUID PRIMARY KEY, permission_code VARCHAR(100) NOT NULL UNIQUE, name VARCHAR(100) NOT NULL,
  module_code VARCHAR(64) NOT NULL, description VARCHAR(500) NOT NULL DEFAULT ''
);
CREATE TABLE IF NOT EXISTS user_role (
  id UUID PRIMARY KEY, organization_id UUID NOT NULL REFERENCES organization(id), user_id UUID NOT NULL REFERENCES user_account(id),
  role_id UUID NOT NULL REFERENCES role(id), created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, created_by_id UUID REFERENCES user_account(id),
  CONSTRAINT uk_user_role_user_role UNIQUE (user_id, role_id)
);
CREATE TABLE IF NOT EXISTS role_permission (
  id UUID PRIMARY KEY, role_id UUID NOT NULL REFERENCES role(id), permission_id UUID NOT NULL REFERENCES permission(id),
  CONSTRAINT uk_role_permission_role_permission UNIQUE (role_id, permission_id)
);
CREATE TABLE IF NOT EXISTS business_number_sequence (
  id UUID PRIMARY KEY, organization_id UUID NOT NULL REFERENCES organization(id), business_type VARCHAR(32) NOT NULL,
  period_key VARCHAR(16) NOT NULL, current_value BIGINT NOT NULL DEFAULT 0, updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_business_number_sequence_scope UNIQUE (organization_id, business_type, period_key)
);
CREATE TABLE IF NOT EXISTS project (
  id UUID PRIMARY KEY, organization_id UUID NOT NULL REFERENCES organization(id), project_no VARCHAR(32) NOT NULL,
  name VARCHAR(200) NOT NULL, project_type_code VARCHAR(64) NOT NULL, description TEXT NOT NULL DEFAULT '', current_stage VARCHAR(64) NOT NULL DEFAULT '方案设计',
  progress_percent SMALLINT NOT NULL DEFAULT 0, document_count INTEGER NOT NULL DEFAULT 0, experiment_count INTEGER NOT NULL DEFAULT 0, data_resource_count INTEGER NOT NULL DEFAULT 0,
  objectives JSONB NOT NULL DEFAULT '[]', milestones JSONB NOT NULL DEFAULT '[]', status VARCHAR(24) NOT NULL DEFAULT 'not_started', owner_id UUID NOT NULL REFERENCES user_account(id),
  planned_start_date TIMESTAMPTZ, planned_end_date TIMESTAMPTZ, actual_end_at TIMESTAMPTZ, archived_at TIMESTAMPTZ, version INTEGER NOT NULL DEFAULT 1,
  created_by_id UUID REFERENCES user_account(id), updated_by_id UUID REFERENCES user_account(id), created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_project_org_project_no UNIQUE (organization_id, project_no),
  CONSTRAINT ck_project_progress_percent_range CHECK (progress_percent BETWEEN 0 AND 100)
);
CREATE TABLE IF NOT EXISTS project_member (
  id UUID PRIMARY KEY, organization_id UUID NOT NULL REFERENCES organization(id), project_id UUID NOT NULL REFERENCES project(id), user_id UUID NOT NULL REFERENCES user_account(id),
  member_role VARCHAR(24) NOT NULL, joined_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, created_by_id UUID REFERENCES user_account(id),
  CONSTRAINT uk_project_member_project_user UNIQUE (project_id, user_id)
);
CREATE TABLE IF NOT EXISTS project_follow (
  id UUID PRIMARY KEY, project_id UUID NOT NULL REFERENCES project(id), user_id UUID NOT NULL REFERENCES user_account(id), created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_project_follow_project_user UNIQUE (project_id, user_id)
);
CREATE TABLE IF NOT EXISTS project_document (
  id UUID PRIMARY KEY, organization_id UUID NOT NULL REFERENCES organization(id), project_id UUID NOT NULL REFERENCES project(id), category VARCHAR(32) NOT NULL,
  name VARCHAR(255) NOT NULL, version_label VARCHAR(32) NOT NULL, file VARCHAR(500) NOT NULL, mime_type VARCHAR(120) NOT NULL DEFAULT '', file_size BIGINT NOT NULL,
  uploaded_by_id UUID NOT NULL REFERENCES user_account(id), preview_file VARCHAR(500), preview_mime_type VARCHAR(120) NOT NULL DEFAULT '',
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS experiment (
  id UUID PRIMARY KEY, organization_id UUID NOT NULL REFERENCES organization(id), project_id UUID NOT NULL REFERENCES project(id), experiment_no VARCHAR(32) NOT NULL,
  name VARCHAR(200) NOT NULL, experiment_type VARCHAR(64) NOT NULL, phase VARCHAR(64) NOT NULL DEFAULT '方案设计', status VARCHAR(24) NOT NULL DEFAULT 'not_started', purpose TEXT NOT NULL DEFAULT '',
  estimated_start TIMESTAMPTZ, estimated_end TIMESTAMPTZ, started_at TIMESTAMPTZ, completed_at TIMESTAMPTZ, owner_id UUID NOT NULL REFERENCES user_account(id), version INTEGER NOT NULL DEFAULT 1,
  created_by_id UUID REFERENCES user_account(id), updated_by_id UUID REFERENCES user_account(id), created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_experiment_org_no UNIQUE (organization_id, experiment_no)
);
CREATE TABLE IF NOT EXISTS experiment_record (
  id UUID PRIMARY KEY, experiment_id UUID NOT NULL UNIQUE REFERENCES experiment(id), formula_columns JSONB NOT NULL DEFAULT '[]', formula_rows JSONB NOT NULL DEFAULT '[]',
  extra_tables JSONB NOT NULL DEFAULT '[]', process_text TEXT NOT NULL DEFAULT '', extra_processes JSONB NOT NULL DEFAULT '[]', result_text TEXT NOT NULL DEFAULT '',
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS experiment_participant (
  id UUID PRIMARY KEY, organization_id UUID NOT NULL REFERENCES organization(id), experiment_id UUID NOT NULL REFERENCES experiment(id), user_id UUID NOT NULL REFERENCES user_account(id),
  participant_role VARCHAR(24) NOT NULL DEFAULT 'participant', joined_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, created_by_id UUID REFERENCES user_account(id),
  CONSTRAINT uk_experiment_participant_user UNIQUE (experiment_id, user_id)
);
CREATE TABLE IF NOT EXISTS experiment_attachment (
  id UUID PRIMARY KEY, organization_id UUID NOT NULL REFERENCES organization(id), experiment_id UUID NOT NULL REFERENCES experiment(id), kind VARCHAR(24) NOT NULL,
  name VARCHAR(255) NOT NULL, file VARCHAR(500) NOT NULL, mime_type VARCHAR(120) NOT NULL DEFAULT '', file_size BIGINT NOT NULL, uploaded_by_id UUID NOT NULL REFERENCES user_account(id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS idempotency_request (
  id UUID PRIMARY KEY, organization_id UUID NOT NULL, user_id UUID NOT NULL REFERENCES user_account(id), idempotency_key VARCHAR(128) NOT NULL, route_key VARCHAR(200) NOT NULL,
  request_hash VARCHAR(64) NOT NULL, status VARCHAR(16) NOT NULL, response_status SMALLINT, response_body JSONB, created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, expires_at TIMESTAMPTZ NOT NULL,
  CONSTRAINT uk_idempotency_request_scope_key UNIQUE (organization_id, user_id, route_key, idempotency_key)
);
CREATE TABLE IF NOT EXISTS business_operation_log (
  id UUID PRIMARY KEY, organization_id UUID NOT NULL, actor_id UUID REFERENCES user_account(id), domain VARCHAR(32) NOT NULL, object_id UUID NOT NULL,
  object_no VARCHAR(64) NOT NULL DEFAULT '', action_type VARCHAR(64) NOT NULL, description VARCHAR(1000) NOT NULL, changes JSONB NOT NULL DEFAULT '{}', created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_project_org_status_updated ON project (organization_id, status, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_experiment_org_status ON experiment (organization_id, status, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_operation_log_object ON business_operation_log (organization_id, domain, object_id, created_at DESC);
