import { http } from "./http";
import type {
  DataResponse,
  PlatformOrganization,
  PlatformOrganizationCreateInput,
} from "@/types/api";

export const organizationApi = {
  /** 读取平台组织目录，仅平台管理员可调用。 */
  async list(): Promise<PlatformOrganization[]> {
    const response = await http.get<DataResponse<PlatformOrganization[]>>("/organizations");
    return response.data.data;
  },

  /** 原子开通组织、内置角色和首个组织管理员。 */
  async create(payload: PlatformOrganizationCreateInput): Promise<PlatformOrganization> {
    const response = await http.post<DataResponse<PlatformOrganization>>("/organizations", payload);
    return response.data.data;
  },
};
