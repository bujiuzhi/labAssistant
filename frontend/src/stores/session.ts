import { defineStore } from "pinia";
import { computed, ref } from "vue";

import { authApi } from "@/api/auth";
import type { SessionUser } from "@/types/api";

export const useSessionStore = defineStore("session", () => {
  const user = ref<SessionUser | null>(null);
  const csrfToken = ref("");
  const initialized = ref(false);

  const isAuthenticated = computed(() => user.value !== null);
  const displayName = computed(() => user.value?.display_name ?? "");
  const isSuperAdmin = computed(() => user.value?.is_super_admin ?? false);
  const isPlatformAdmin = computed(() => user.value?.is_platform_admin ?? false);

  function hasPermission(permissionCode: string): boolean {
    return isSuperAdmin.value || (user.value?.permissions.includes(permissionCode) ?? false);
  }

  async function initialize(): Promise<void> {
    if (initialized.value) {
      return;
    }
    try {
      csrfToken.value = await authApi.getCsrfToken();
      user.value = await authApi.getSession();
    } catch {
      user.value = null;
    } finally {
      initialized.value = true;
    }
  }

  async function login(username: string, password: string): Promise<void> {
    csrfToken.value ||= await authApi.getCsrfToken();
    user.value = await authApi.login(username, password, csrfToken.value);
  }

  async function logout(): Promise<void> {
    try {
      csrfToken.value ||= await authApi.getCsrfToken();
      await authApi.logout(csrfToken.value);
    } finally {
      await clearSession();
    }
  }

  /** 清除本地会话缓存，并尽力刷新下一次匿名写请求需要的 CSRF 令牌。 */
  async function clearSession(): Promise<void> {
    user.value = null;
    try {
      csrfToken.value = await authApi.getCsrfToken();
    } catch {
      csrfToken.value = "";
    }
  }

  return {
    user,
    initialized,
    isAuthenticated,
    displayName,
    isSuperAdmin,
    isPlatformAdmin,
    hasPermission,
    initialize,
    login,
    logout,
    clearSession,
  };
});
