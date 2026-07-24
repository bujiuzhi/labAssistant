<script setup lang="ts">
import { Icon } from "@iconify/vue";
import { computed, ref } from "vue";
import { useRoute, useRouter } from "vue-router";

import { useSessionStore } from "@/stores/session";

interface SideNavigationItem {
  label: string;
  icon: string;
  active?: boolean;
  badge?: string;
}

const route = useRoute();
const router = useRouter();
const sessionStore = useSessionStore();
const sidebarCollapsed = ref(false);

const sideNavigation: SideNavigationItem[] = [
  { label: "新对话", icon: "tabler:plus" },
  { label: "技能广场", icon: "tabler:hammer" },
  { label: "知识空间", icon: "tabler:notebook" },
  { label: "多端协同", icon: "tabler:link" },
  { label: "任务管理", icon: "tabler:clock", badge: "46" },
  { label: "实验助手", icon: "tabler:tool", active: true },
  { label: "科研数据", icon: "tabler:files" },
  { label: "聚合物性质探索", icon: "tabler:cube" },
  { label: "配方库", icon: "tabler:flask" },
];

const historyItems = [
  "人工智能产业链...",
  "【综述生成】高...",
  "【实验总结】请...",
  "【实验图表生成...",
  "【论文检索】请...",
  "【性质预测】[*]...",
];

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
  return (
    prototypeUsers[username] ?? {
      displayName: sessionStore.displayName || "未登录用户",
      roleName: "项目成员",
    }
  );
});

const profileInitial = computed(() => profile.value.displayName.slice(-1));

function isProductRoute(path: string): boolean {
  if (path === "/projects") {
    return route.path.startsWith("/projects");
  }
  return route.path === path;
}

async function logout(): Promise<void> {
  await sessionStore.logout();
  await router.replace("/login");
}
</script>

<template>
  <div class="prototype-shell" :class="{ 'sidebar-collapsed': sidebarCollapsed }">
    <header class="window-bar">
      <div class="traffic-lights" aria-hidden="true">
        <i />
        <i />
        <i />
      </div>
      <RouterLink class="brand" to="/dashboard" aria-label="玄鉴首页">
        <span class="brand-mark"><Icon icon="tabler:flask-2" /></span>
        <strong>玄鉴</strong>
      </RouterLink>
      <button
        class="icon-button collapse-button"
        type="button"
        :aria-label="sidebarCollapsed ? '展开侧边栏' : '收起侧边栏'"
        @click="sidebarCollapsed = !sidebarCollapsed"
      >
        <Icon
          :icon="
            sidebarCollapsed
              ? 'tabler:layout-sidebar-left-expand'
              : 'tabler:layout-sidebar-left-collapse'
          "
        />
      </button>
      <div class="window-actions">
        <button class="text-button" type="button">
          <Icon icon="tabler:message-circle-question" />
          <span>问题反馈</span>
        </button>
        <span class="online"><Icon icon="tabler:robot" />在线</span>
      </div>
    </header>

    <aside class="sidebar" aria-label="玄鉴主导航">
      <nav class="side-nav">
        <button
          v-for="item in sideNavigation"
          :key="item.label"
          class="side-nav-item"
          :class="{ active: item.active }"
          type="button"
        >
          <Icon :icon="item.icon" />
          <span>{{ item.label }}</span>
          <b v-if="item.badge" class="nav-badge">{{ item.badge }}</b>
        </button>
      </nav>

      <section class="history" aria-label="历史会话">
        <p>历史会话</p>
        <button v-for="item in historyItems" :key="item" type="button">
          <Icon icon="tabler:pin" />
          <span>{{ item }}</span>
        </button>
      </section>

      <div class="profile">
        <span class="profile-avatar">{{ profileInitial }}</span>
        <span class="profile-copy">
          <strong>{{ profile.displayName }}</strong>
          <small>{{ profile.roleName }}</small>
        </span>
        <el-dropdown trigger="click">
          <button class="icon-button profile-settings" type="button" aria-label="账户设置">
            <Icon icon="tabler:chevrons-up" />
          </button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item @click="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </aside>

    <main class="main-workspace">
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
      <div class="route-content">
        <RouterView />
      </div>
    </main>
  </div>
</template>

<style scoped>
.prototype-shell {
  display: grid;
  width: 100%;
  height: 100%;
  grid-template-columns: 220px minmax(0, 1fr);
  grid-template-rows: 48px minmax(0, 1fr);
  background: var(--color-shell);
  transition: grid-template-columns 180ms ease;
}

.prototype-shell.sidebar-collapsed {
  grid-template-columns: 72px minmax(0, 1fr);
}

.window-bar {
  z-index: 2;
  display: flex;
  grid-column: 1 / -1;
  align-items: center;
  padding: 0 14px;
  background: var(--color-paper-2);
  border-bottom: 1px solid var(--color-rule);
}

.traffic-lights {
  display: flex;
  width: 86px;
  gap: 10px;
}

.traffic-lights i {
  width: 14px;
  height: 14px;
  border-radius: 999px;
}

.traffic-lights i:nth-child(1) {
  background: var(--color-traffic-red);
}

.traffic-lights i:nth-child(2) {
  background: var(--color-traffic-yellow);
}

.traffic-lights i:nth-child(3) {
  background: var(--color-traffic-green);
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  font-family: var(--font-outlier);
  font-size: 18px;
}

.brand-mark {
  display: grid;
  width: 22px;
  height: 22px;
  color: #ffffff;
  background: var(--color-ink);
  border-radius: 6px;
  place-items: center;
}

.brand-mark svg {
  width: 15px;
  height: 15px;
}

.collapse-button {
  margin-left: 14px;
}

.window-actions {
  display: flex;
  align-items: center;
  margin-left: auto;
  gap: 22px;
}

.text-button,
.icon-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: var(--color-ink-2);
  background: transparent;
  border: 0;
  cursor: pointer;
}

.text-button {
  height: 36px;
  gap: 7px;
}

.text-button svg {
  width: 17px;
  height: 17px;
}

.icon-button {
  width: 34px;
  height: 34px;
  border-radius: 6px;
}

.icon-button svg {
  width: 18px;
  height: 18px;
}

.online {
  display: inline-flex;
  align-items: center;
  padding: 4px 10px;
  color: #078545;
  background: var(--color-success-soft);
  border-radius: 999px;
  gap: 7px;
}

.sidebar {
  display: flex;
  grid-row: 2;
  min-width: 0;
  min-height: 0;
  flex-direction: column;
  overflow: hidden;
  background: var(--color-paper-2);
  border-right: 1px solid var(--color-rule);
}

.side-nav {
  display: grid;
  flex: none;
  padding: 16px 8px 10px;
  gap: 3px;
}

.side-nav-item {
  display: flex;
  align-items: center;
  width: 100%;
  height: 36px;
  padding: 0 12px;
  overflow: hidden;
  color: var(--color-ink-2);
  font-weight: 600;
  text-align: left;
  white-space: nowrap;
  background: transparent;
  border: 0;
  border-radius: 6px;
  cursor: pointer;
  gap: 12px;
}

.side-nav-item svg {
  flex: 0 0 auto;
  width: 18px;
  height: 18px;
}

.side-nav-item.active {
  color: var(--color-accent);
  background: #e2f0ff;
}

.nav-badge {
  min-width: 24px;
  padding: 1px 5px;
  margin-left: auto;
  color: var(--color-muted);
  font-size: 11px;
  font-weight: 700;
  text-align: center;
  background: #edf1f6;
  border-radius: 999px;
}

.history {
  min-height: 0;
  padding: 0 8px;
  overflow: hidden;
}

.history p {
  margin: 8px 4px 7px;
  color: var(--color-faint);
  font-size: 12px;
}

.history button {
  display: flex;
  align-items: center;
  width: 100%;
  padding: 6px 4px;
  overflow: hidden;
  color: var(--color-muted);
  font-size: 12px;
  text-align: left;
  white-space: nowrap;
  background: transparent;
  border: 0;
  cursor: pointer;
  gap: 8px;
}

.history button span {
  overflow: hidden;
  text-overflow: ellipsis;
}

.history svg {
  flex: 0 0 auto;
  width: 13px;
  height: 13px;
}

.profile {
  display: flex;
  flex: none;
  align-items: center;
  min-height: 58px;
  padding: 9px 12px;
  margin-top: auto;
  border-top: 1px solid var(--color-rule);
  gap: 9px;
}

.profile-avatar {
  display: grid;
  flex: 0 0 auto;
  width: 32px;
  height: 32px;
  background: #f1f5f9;
  border: 1px solid var(--color-rule);
  border-radius: 999px;
  place-items: center;
}

.profile-copy {
  min-width: 0;
}

.profile-copy strong,
.profile-copy small {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.profile-copy strong {
  font-size: 13px;
}

.profile-copy small {
  margin-top: 1px;
  color: var(--color-muted);
  font-size: 11px;
}

.profile-settings {
  margin-left: auto;
}

.main-workspace {
  display: flex;
  grid-row: 2;
  min-width: 0;
  min-height: 0;
  flex-direction: column;
  padding: 24px 20px 0;
  overflow: hidden;
}

.product-nav {
  display: flex;
  flex: none;
  align-items: center;
  height: 44px;
  padding: 0 20px;
  background: var(--color-paper);
  border-radius: 8px;
  box-shadow: var(--shadow-whisper);
  gap: 30px;
}

.product-nav a {
  position: relative;
  display: flex;
  align-items: center;
  align-self: stretch;
  color: var(--color-muted);
  font-weight: 650;
}

.product-nav a.active {
  color: var(--color-ink);
}

.product-nav a.active::after {
  position: absolute;
  right: 0;
  bottom: 0;
  left: 0;
  height: 2px;
  background: var(--color-accent);
  content: "";
}

.route-content {
  min-width: 0;
  min-height: 0;
  flex: 1;
  overflow: hidden;
}

.sidebar-collapsed .side-nav-item {
  justify-content: center;
  padding: 0;
}

.sidebar-collapsed .side-nav-item span,
.sidebar-collapsed .nav-badge,
.sidebar-collapsed .history,
.sidebar-collapsed .profile-copy,
.sidebar-collapsed .profile-settings {
  display: none;
}

.sidebar-collapsed .profile {
  justify-content: center;
}

@media (hover: hover) and (pointer: fine) {
  .side-nav-item:hover,
  .history button:hover,
  .icon-button:hover,
  .text-button:hover {
    background: var(--color-paper-3);
  }

  .side-nav-item.active:hover {
    background: #e2f0ff;
  }
}

@media (max-width: 1180px) {
  .history {
    display: none;
  }

  .main-workspace {
    padding-inline: 14px;
  }
}
</style>
