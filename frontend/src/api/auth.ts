import { http } from "./http";
import type { DataResponse, SessionUser } from "@/types/api";

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

  async logout(): Promise<void> {
    await http.post("/auth/logout");
  },
};
