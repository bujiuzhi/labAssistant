import { http } from "./http";
import type {
  DataResponse,
  PageResponse,
  Project,
  ProjectCreateInput,
  ProjectFilters,
} from "@/types/api";

export const projectApi = {
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
};
