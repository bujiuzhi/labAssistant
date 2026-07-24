export interface SessionUser {
  id: string;
  organization_id: string;
  username: string;
  display_name: string;
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
  planned_start_date: string | null;
  planned_end_date: string | null;
  current_stage?: string;
  objectives?: string[];
  milestones?: ProjectMilestone[];
  member_ids?: string[];
}

export interface OrganizationUserOption {
  id: string;
  username: string;
  display_name: string;
}

export interface ProjectFilters {
  page?: number;
  page_size?: number;
  search?: string;
  status?: ProjectStatus | "";
  owner_id?: string;
  ordering?: string;
}
