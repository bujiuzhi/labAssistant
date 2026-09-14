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
  RegistrationInvitation,
  RegistrationInvitationCreated,
  RegistrationInvitationCreateInput,
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

  /**
   * 逻辑删除当前组织普通用户
   *
   * 账号会立即失效并匿名化；仍负责活动资源时服务端要求先交接负责人。
   */
  async deleteManagedUser(userId: string): Promise<void> {
    await http.delete(`/auth/users/${userId}`);
  },

  /** 查询当前组织最近签发的邀请码元数据。 */
  async listRegistrationInvitations(): Promise<RegistrationInvitation[]> {
    const response = await http.get<DataResponse<RegistrationInvitation[]>>(
      "/auth/invitations",
    );
    return response.data.data;
  },

  /** 签发邀请码，邀请码明文仅随该响应返回一次。 */
  async createRegistrationInvitation(
    payload: RegistrationInvitationCreateInput,
  ): Promise<RegistrationInvitationCreated> {
    const response = await http.post<DataResponse<RegistrationInvitationCreated>>(
      "/auth/invitations",
      payload,
    );
    return response.data.data;
  },

  /** 撤销尚未使用的邀请码。 */
  async revokeRegistrationInvitation(invitationId: string): Promise<void> {
    await http.post(`/auth/invitations/${invitationId}/revoke`);
  },
};
