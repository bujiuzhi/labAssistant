export interface SessionUser {
  id: string;
  organization_id: string;
  username: string;
  display_name: string;
  is_super_admin: boolean;
  permissions: string[];
  role_codes: string[];
  role_names: string[];
}

export type ProjectStatus =
  | "draft"
  | "not_started"
  | "active"
  | "at_risk"
  | "suspended"
  | "completed"
  | "archived";

export interface ProjectMilestone {
  date: string;
  name: string;
  state: "todo" | "current" | "done";
}

export interface ProjectMember {
  user_id: string;
  display_name: string;
  member_role: "owner" | "researcher" | "inspector" | "viewer";
}

export interface Project {
  id: string;
  project_no: string;
  name: string;
  project_type_code: string;
  description: string;
  current_stage: string;
  progress_percent: number;
  document_count: number;
  experiment_count: number;
  data_resource_count: number;
  objectives: string[];
  milestones: ProjectMilestone[];
  status: ProjectStatus;
  owner_id: string;
  owner_display_name: string;
  members: ProjectMember[];
  planned_start_date: string | null;
  planned_end_date: string | null;
  actual_end_at: string | null;
  archived_at: string | null;
  version: number;
  created_at: string;
  updated_at: string;
}

export interface PageMeta {
  page: number;
  page_size: number;
  total: number;
  total_pages: number;
}

export interface DataResponse<T> {
  data: T;
  request_id: string;
}

export interface PageResponse<T> {
  data: T[];
  meta: PageMeta;
  request_id: string;
}

export interface ProblemDetail {
  type: string;
  title: string;
  status: number;
  code: string;
  detail: string;
  request_id: string;
  field_errors?: Record<string, string[]>;
}

export interface ProjectCreateInput {
  name: string;
  project_type_code: string;
  description: string;
  owner_id: string;
  planned_start_date: string;
  planned_end_date: string;
  current_stage?: string;
  objectives: string[];
  milestones: ProjectMilestone[];
  member_ids?: string[];
}

export interface OrganizationUserOption {
  id: string;
  username: string;
  display_name: string;
  organization_id: string;
  organization_name: string;
}

export type ManagedUserStatus = "active" | "locked" | "disabled";

export interface ManagedUser {
  id: string;
  username: string;
  display_name: string;
  email: string;
  status: ManagedUserStatus;
  is_active: boolean;
  is_super_admin: boolean;
  role_codes: string[];
  role_names: string[];
  last_login: string | null;
  created_at: string;
  updated_at: string;
}

export interface ManagedRoleOption {
  role_code: string;
  name: string;
  description: string;
}

export interface ManagedUserFilters {
  page?: number;
  page_size?: number;
  search?: string;
  status?: ManagedUserStatus | "";
  role_code?: string;
}

export interface ManagedUserCreateInput {
  username: string;
  display_name: string;
  email: string;
  password: string;
  status: ManagedUserStatus;
  role_codes: string[];
}

export type ManagedUserUpdateInput = Omit<
  ManagedUserCreateInput,
  "password"
>;

export interface ProjectFilters {
  page?: number;
  page_size?: number;
  search?: string;
  status?: ProjectStatus | "running" | "ended" | "";
  project_type?: "聚酰亚胺" | "环氧树脂" | "";
  owner_id?: string;
  ordering?: string;
}

export interface ProjectOperationLog {
  id: string;
  action_type: string;
  description: string;
  actor_display_name: string;
  changes: Record<string, unknown>;
  created_at: string;
}

export type ProjectDocumentCategory =
  | "project_plan"
  | "literature"
  | "experiment_plan"
  | "stage_report"
  | "meeting_minutes"
  | "other";

export interface ProjectDocument {
  id: string;
  name: string;
  extension: string;
  mime_type: string;
  file_size: number;
  category: ProjectDocumentCategory;
  category_label: string;
  related_content: string;
  version_label: string;
  uploaded_by_name: string;
  created_at: string;
  updated_at: string;
}

export interface ProjectDocumentFilters {
  search?: string;
  category?: ProjectDocumentCategory | "";
  file_type?:
    | "word"
    | "pdf"
    | "excel"
    | "powerpoint"
    | "image"
    | "text"
    | "";
  updated_range?: "week" | "month" | "";
}

export interface ProjectDocumentListMeta {
  total: number;
  filtered_total: number;
  category_counts: Record<ProjectDocumentCategory, number>;
}

export interface ProjectDocumentListResponse {
  data: ProjectDocument[];
  meta: ProjectDocumentListMeta;
  request_id: string;
}

export interface ProjectDocumentUploadInput {
  file: File;
  category: ProjectDocumentCategory;
  related_content: string;
  version_label: string;
}

export interface DashboardMetrics {
  total: number;
  active?: number;
  archived?: number;
  at_risk?: number;
  in_progress?: number;
  completed?: number;
}

export interface DashboardTypeDistributionItem {
  name: string;
  project_count: number;
  experiment_count: number;
}

export interface DashboardTrendSeries {
  name: string;
  values: number[];
}

export interface DashboardActiveProject {
  id: string;
  project_no: string;
  name: string;
  project_type: string;
  owner_name: string;
  objectives: string[];
  planned_start_date: string | null;
  planned_end_date: string | null;
  milestone: ProjectMilestone | null;
  progress_percent: number;
  is_followed: boolean;
  risk_level: "normal" | "countdown" | "overdue";
  risk_days: number | null;
}

export interface DashboardProjectOption {
  id: string;
  project_no: string;
  name: string;
}

export interface DashboardSummary {
  project_metrics: DashboardMetrics;
  experiment_metrics: DashboardMetrics;
  type_distribution: DashboardTypeDistributionItem[];
  trend: {
    dates: string[];
    series: DashboardTrendSeries[];
    selected_project_id: string;
    project_options: DashboardProjectOption[];
  };
  active_projects: DashboardActiveProject[];
}

export type ExperimentStatus = "not_started" | "in_progress" | "completed";

export interface FormulaColumn {
  id: string;
  label: string;
}

export interface ExtraFormulaTable {
  id: string;
  name: string;
  columns: FormulaColumn[];
  rows: Record<string, string>[];
}

export interface ExtraProcess {
  id: string;
  name: string;
  content: string;
}

export interface ProcessImage {
  id?: string;
  name: string;
  url: string;
  size?: string;
}

export interface ResultFile {
  id?: string;
  name: string;
  size: string;
  url?: string;
}

export interface ExperimentRecord {
  formula_columns: FormulaColumn[];
  formula_rows: Record<string, string>[];
  extra_tables: ExtraFormulaTable[];
  process_text: string;
  extra_processes: ExtraProcess[];
  process_images: ProcessImage[];
  result_text: string;
  result_files: ResultFile[];
}

export interface Experiment {
  id: string;
  experiment_no: string;
  name: string;
  project_id: string;
  project_no: string;
  project_name: string;
  experiment_type: string;
  phase: string;
  status: ExperimentStatus;
  purpose: string;
  estimated_start: string | null;
  estimated_end: string | null;
  started_at: string | null;
  completed_at: string | null;
  owner_id: string;
  owner_display_name: string;
  participant_ids: string[];
  participant_names: string[];
  record: ExperimentRecord;
  version: number;
  created_at: string;
  updated_at: string;
}

export interface ExperimentFilters {
  page?: number;
  page_size?: number;
  search?: string;
  status?: ExperimentStatus | "";
  project_id?: string;
}

export interface ExperimentWriteInput {
  project_id?: string;
  name?: string;
  experiment_type?: string;
  purpose?: string;
  estimated_start?: string | null;
  estimated_end?: string | null;
  owner_id?: string;
  participant_ids?: string[];
  formula_columns?: FormulaColumn[];
  formula_rows?: Record<string, string>[];
  extra_tables?: ExtraFormulaTable[];
  process_text?: string;
  extra_processes?: ExtraProcess[];
  result_text?: string;
}
