import { http } from "./http";
import type { DataResponse, RegistrationInput, SessionUser } from "@/types/api";

interface CsrfPayload {
  csrf_token: string;
}

export const authApi = {
  async getCsrfToken(): Promise<string> {
    const response = await http.get<DataResponse<CsrfPayload>>("/auth/csrf");
    return response.data.data.csrf_token;
  },

  async getSession(): Promise<SessionUser> {
    const response = await http.get<DataResponse<SessionUser>>("/auth/session");
    return response.data.data;
  },

  async login(username: string, password: string, csrfToken: string): Promise<SessionUser> {
    const response = await http.post<DataResponse<SessionUser>>(
      "/auth/login",
      { username, password },
      { headers: { "X-CSRFToken": csrfToken } },
    );
    return response.data.data;
  },

  async logout(csrfToken: string): Promise<void> {
    await http.post("/auth/logout", undefined, {
      headers: { "X-CSRFToken": csrfToken },
    });
  },

  /** 使用管理员签发的邀请码注册普通组织用户，不自动登录。 */
  async register(payload: RegistrationInput, csrfToken: string): Promise<void> {
    await http.post("/auth/register", payload, {
      headers: { "X-CSRFToken": csrfToken },
    });
  },

  /** 校验旧密码并修改当前账号密码；服务端会同时失效当前会话。 */
  async changePassword(
    currentPassword: string,
    newPassword: string,
    csrfToken: string,
  ): Promise<void> {
    await http.post(
      "/auth/password",
      { current_password: currentPassword, new_password: newPassword },
      { headers: { "X-CSRFToken": csrfToken } },
    );
  },
};
