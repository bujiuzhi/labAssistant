<script setup lang="ts">
import { Icon } from "@iconify/vue";
import { computed } from "vue";
import { useRoute, useRouter } from "vue-router";

import { useSessionStore } from "@/stores/session";

const route = useRoute();
const router = useRouter();
const sessionStore = useSessionStore();

const productNavigation = [
  { path: "/dashboard", label: "项目总览" },
  { path: "/projects", label: "项目数据" },
  { path: "/eln", label: "电子实验记录本" },
];

const prototypeUsers: Record<string, { displayName: string; roleName: string }> = {
  admin: { displayName: "刘李园", roleName: "超级管理员" },
  manager: { displayName: "张伟", roleName: "项目负责人" },
  researcher: { displayName: "李娜", roleName: "研究人员" },
  inspector: { displayName: "王强", roleName: "检验人员" },
};

const profile = computed(() => {
  const username = sessionStore.user?.username ?? "";
  const fallbackProfile = prototypeUsers[username];
  return {
    displayName:
      sessionStore.displayName || fallbackProfile?.displayName || "未登录用户",
    roleName: fallbackProfile?.roleName ?? "项目成员",
  };
});

const profileInitial = computed(() => profile.value.displayName.slice(-1));

/**
 * 判断当前业务路由是否属于指定一级导航
 *
 * @param path 一级导航路由
 * @returns 是否应显示为选中状态
 */
function isProductRoute(path: string): boolean {
  if (path === "/projects") {
    return route.path.startsWith("/projects");
  }
  return route.path === path;
}

/**
 * 退出当前会话并返回登录页
 */
async function logout(): Promise<void> {
  await sessionStore.logout();
  await router.replace("/login");
}
</script>

<template>
  <div class="assistant-shell">
    <header class="assistant-header">
      <RouterLink class="assistant-brand" to="/dashboard" aria-label="实验助手首页">
        <span class="brand-mark"><Icon icon="tabler:flask-2" /></span>
        <strong>实验助手</strong>
      </RouterLink>

      <nav class="product-nav" aria-label="实验助手导航">
        <RouterLink
          v-for="item in productNavigation"
          :key="item.path"
          :to="item.path"
          :class="{ active: isProductRoute(item.path) }"
        >
          {{ item.label }}
        </RouterLink>
      </nav>

      <el-dropdown class="profile-dropdown" trigger="click">
        <button class="profile-button" type="button" aria-label="打开账户菜单">
          <span class="profile-avatar">{{ profileInitial }}</span>
          <span class="profile-copy">
            <strong>{{ profile.displayName }}</strong>
            <small>{{ profile.roleName }}</small>
          </span>
          <Icon class="profile-chevron" icon="tabler:chevron-down" />
        </button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item @click="logout">
              <Icon icon="tabler:logout" />
              <span>退出登录</span>
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </header>

    <main class="route-content">
      <RouterView />
    </main>
  </div>
</template>

<style scoped>
.assistant-shell {
  display: flex;
  width: 100%;
  height: 100%;
  min-width: 960px;
  flex-direction: column;
  overflow: hidden;
  color: var(--color-ink);
  background: var(--color-shell);
}

.assistant-header {
  z-index: 2;
  display: flex;
  height: 64px;
  flex: 0 0 64px;
  align-items: center;
  padding: 0 24px;
  background: var(--color-paper-2);
  border-bottom: 1px solid var(--color-rule);
  box-shadow: 0 1px 4px rgb(15 23 42 / 4%);
}

.assistant-brand {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 10px;
  color: var(--color-ink);
  font-family: var(--font-outlier);
  font-size: 19px;
  letter-spacing: 0.01em;
  text-decoration: none;
}

.brand-mark {
  display: grid;
  width: 32px;
  height: 32px;
  color: #ffffff;
  background: var(--color-accent);
  border-radius: 9px;
  box-shadow: 0 4px 12px rgb(37 99 235 / 18%);
  place-items: center;
}

.brand-mark svg {
  width: 20px;
  height: 20px;
}

.product-nav {
  display: flex;
  height: 100%;
  align-items: stretch;
  margin-left: 48px;
  gap: 34px;
}

.product-nav a {
  position: relative;
  display: inline-flex;
  align-items: center;
  color: var(--color-ink-3);
  font-size: 14px;
  font-weight: 500;
  text-decoration: none;
  transition:
    color 160ms ease,
    font-weight 160ms ease;
}

.product-nav a::after {
  position: absolute;
  right: 0;
  bottom: -1px;
  left: 0;
  height: 2px;
  background: transparent;
  border-radius: 999px 999px 0 0;
  content: "";
}

.product-nav a:hover,
.product-nav a.active {
  color: var(--color-accent);
}

.product-nav a.active {
  font-weight: 600;
}

.product-nav a.active::after {
  background: var(--color-accent);
}

.profile-dropdown {
  margin-left: auto;
}

.profile-button {
  display: inline-flex;
  min-width: 156px;
  height: 46px;
  align-items: center;
  justify-content: flex-end;
  padding: 4px 8px;
  color: var(--color-ink);
  background: transparent;
  border: 1px solid transparent;
  border-radius: 10px;
  cursor: pointer;
  gap: 10px;
  text-align: left;
  transition:
    background 160ms ease,
    border-color 160ms ease;
}

.profile-button:hover {
  background: var(--color-paper-3);
  border-color: var(--color-rule);
}

.profile-avatar {
  display: grid;
  width: 34px;
  height: 34px;
  flex: 0 0 34px;
  color: #ffffff;
  background: var(--color-accent);
  border-radius: 50%;
  font-size: 13px;
  font-weight: 600;
  place-items: center;
}

.profile-copy {
  display: flex;
  min-width: 72px;
  flex-direction: column;
  line-height: 1.35;
}

.profile-copy strong {
  overflow: hidden;
  max-width: 96px;
  font-size: 13px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.profile-copy small {
  overflow: hidden;
  max-width: 96px;
  color: var(--color-ink-4);
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.profile-chevron {
  width: 16px;
  height: 16px;
  flex: 0 0 16px;
  color: var(--color-ink-4);
}

.route-content {
  min-width: 0;
  min-height: 0;
  flex: 1;
  overflow: hidden;
  padding: 0 24px;
}

@media (max-width: 1180px) {
  .assistant-header {
    padding: 0 18px;
  }

  .product-nav {
    margin-left: 30px;
    gap: 24px;
  }

  .route-content {
    padding: 0 18px;
  }
}
</style>
