import { http } from "./http";
import type { OrganizationUserOption } from "@/types/api";

interface UserOptionsResponse {
  data: OrganizationUserOption[];
  request_id: string;
}

export const userApi = {
  /**
   * 查询当前组织可选的项目负责人和成员
   *
   * @returns 组织有效用户列表
   */
  async listOptions(): Promise<OrganizationUserOption[]> {
    const response = await http.get<UserOptionsResponse>("/auth/users/options");
    return response.data.data;
  },
};
