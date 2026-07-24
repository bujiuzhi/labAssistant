import { http } from "./http";
import type {
  DataResponse,
  ManagedRoleOption,
  ManagedUser,
  ManagedUserCreateInput,
  ManagedUserFilters,
  ManagedUserUpdateInput,
  OrganizationUserOption,
  PageResponse,
} from "@/types/api";

interface UserOptionsResponse {
  data: OrganizationUserOption[];
  request_id: string;
}

interface RoleOptionsResponse {
  data: ManagedRoleOption[];
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

  /**
   * 超级管理员分页查询当前组织用户
   *
   * @param filters 用户筛选与分页参数
   * @returns 用户分页结果
   */
  async listManagedUsers(
    filters: ManagedUserFilters = {},
  ): Promise<PageResponse<ManagedUser>> {
    const response = await http.get<PageResponse<ManagedUser>>("/auth/users", {
      params: filters,
    });
    return response.data;
  },

  /**
   * 查询超级管理员可分配的系统角色
   *
   * @returns 角色选择项
   */
  async listManagedRoleOptions(): Promise<ManagedRoleOption[]> {
    const response = await http.get<RoleOptionsResponse>("/auth/roles/options");
    return response.data.data;
  },

  /**
   * 创建组织用户
   *
   * @param payload 用户基础信息、初始密码和角色
   * @returns 新建用户
   */
  async createManagedUser(payload: ManagedUserCreateInput): Promise<ManagedUser> {
    const response = await http.post<DataResponse<ManagedUser>>(
      "/auth/users",
      payload,
    );
    return response.data.data;
  },

  /**
   * 更新组织用户
   *
   * @param userId 用户主键
   * @param payload 用户基础信息、状态和角色
   * @returns 更新后的用户
   */
  async updateManagedUser(
    userId: string,
    payload: ManagedUserUpdateInput,
  ): Promise<ManagedUser> {
    const response = await http.patch<DataResponse<ManagedUser>>(
      `/auth/users/${userId}`,
      payload,
    );
    return response.data.data;
  },

  /**
   * 重置普通用户密码
   *
   * @param userId 用户主键
   * @param password 新密码
   */
  async resetManagedUserPassword(
    userId: string,
    password: string,
  ): Promise<void> {
    await http.post(`/auth/users/${userId}/reset-password`, { password });
  },
};
