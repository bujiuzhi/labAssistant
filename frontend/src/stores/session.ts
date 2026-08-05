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
      user.value = null;
      try {
        csrfToken.value = await authApi.getCsrfToken();
      } catch {
        csrfToken.value = "";
      }
    }
  }

  return {
    user,
    initialized,
    isAuthenticated,
    displayName,
    isSuperAdmin,
    hasPermission,
    initialize,
    login,
    logout,
  };
});
