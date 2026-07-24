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

  function hasPermission(permissionCode: string): boolean {
    return user.value?.permissions.includes(permissionCode) ?? false;
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
      await authApi.logout();
    } finally {
      user.value = null;
      csrfToken.value = await authApi.getCsrfToken();
    }
  }

  return {
    user,
    initialized,
    isAuthenticated,
    displayName,
    hasPermission,
    initialize,
    login,
    logout,
  };
});
