import { http } from "./http";
import type {
  DataResponse,
  DashboardSummary,
  PageResponse,
  Project,
  ProjectCreateInput,
  ProjectDocument,
  ProjectDocumentFilters,
  ProjectDocumentListResponse,
  ProjectDocumentUploadInput,
  ProjectFilters,
} from "@/types/api";

export const projectApi = {
  /** 查询当前用户数据范围内的实时总览。 */
  async dashboard(): Promise<DashboardSummary> {
    const response = await http.get<DataResponse<DashboardSummary>>("/dashboard");
    return response.data.data;
  },

  async list(filters: ProjectFilters = {}): Promise<PageResponse<Project>> {
    const response = await http.get<PageResponse<Project>>("/projects", {
      params: filters,
    });
    return response.data;
  },

  async get(projectId: string): Promise<Project> {
    const response = await http.get<DataResponse<Project>>(`/projects/${projectId}`);
    return response.data.data;
  },

  async create(payload: ProjectCreateInput): Promise<Project> {
    const response = await http.post<DataResponse<Project>>("/projects", payload, {
      headers: { "Idempotency-Key": crypto.randomUUID() },
    });
    return response.data.data;
  },

  async update(
    projectId: string,
    version: number,
    payload: Partial<ProjectCreateInput>,
  ): Promise<Project> {
    const response = await http.patch<DataResponse<Project>>(
      `/projects/${projectId}`,
      payload,
      { headers: { "If-Match": `"${version}"` } },
    );
    return response.data.data;
  },

  async listDocuments(
    projectId: string,
    filters: ProjectDocumentFilters = {},
  ): Promise<ProjectDocumentListResponse> {
    const response = await http.get<ProjectDocumentListResponse>(
      `/projects/${projectId}/documents`,
      { params: filters },
    );
    return response.data;
  },

  async uploadDocument(
    projectId: string,
    payload: ProjectDocumentUploadInput,
  ): Promise<ProjectDocument> {
    const body = new FormData();
    body.append("file", payload.file);
    body.append("category", payload.category);
    body.append("related_content", payload.related_content);
    body.append("version_label", payload.version_label);
    const response = await http.post<DataResponse<ProjectDocument>>(
      `/projects/${projectId}/documents`,
      body,
    );
    return response.data.data;
  },

  documentContentUrl(
    projectId: string,
    documentId: string,
    download = false,
  ): string {
    const base = `/api/v1/projects/${encodeURIComponent(
      projectId,
    )}/documents/${encodeURIComponent(documentId)}/content`;
    return download ? `${base}?download=1` : base;
  },

  /** 获取文档在线预览地址，办公文档由服务端转换为真实 PDF。 */
  documentPreviewUrl(projectId: string, documentId: string): string {
    return `/api/v1/projects/${encodeURIComponent(
      projectId,
    )}/documents/${encodeURIComponent(documentId)}/preview`;
  },

  /** 关注或取消关注项目。 */
  async setFollow(projectId: string, isFollowed: boolean): Promise<boolean> {
    const response = isFollowed
      ? await http.post<DataResponse<{ is_followed: boolean }>>(`/projects/${projectId}/follow`)
      : await http.delete<DataResponse<{ is_followed: boolean }>>(`/projects/${projectId}/follow`);
    return response.data.data.is_followed;
  },
};
