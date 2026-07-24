<script setup lang="ts">
import {
  ArrowDown,
  Bell,
  Box,
  DataAnalysis,
  Expand,
  Fold,
  FolderOpened,
  House,
  Moon,
  Notebook,
  Setting,
  Sunny,
} from "@element-plus/icons-vue";
import { computed, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";

import { useSessionStore } from "@/stores/session";

interface RouteTab {
  path: string;
  title: string;
  closable: boolean;
}

const route = useRoute();
const router = useRouter();
const sessionStore = useSessionStore();

const collapsed = ref(false);
const isDark = ref(localStorage.getItem("materials-lab-theme") === "dark");
const tabs = ref<RouteTab[]>([
  { path: "/dashboard", title: "工作台", closable: false },
]);

const menuItems = [
  { path: "/dashboard", label: "工作台", icon: House },
  { path: "/projects", label: "项目管理", icon: FolderOpened },
  { path: "/experiments", label: "实验管理", icon: Notebook },
  { path: "/testing", label: "检测管理", icon: DataAnalysis },
  { path: "/materials", label: "基础资料", icon: Box },
  { path: "/system", label: "系统管理", icon: Setting },
];

const activeMenu = computed(() => {
  if (route.path.startsWith("/projects")) {
    return "/projects";
  }
  return route.path;
});

const currentTitle = computed(() => String(route.meta.title ?? "材料实验助手"));

function applyTheme(): void {
  document.documentElement.classList.toggle("dark", isDark.value);
  localStorage.setItem("materials-lab-theme", isDark.value ? "dark" : "light");
}

function toggleTheme(): void {
  isDark.value = !isDark.value;
  applyTheme();
}

async function handleUserCommand(command: string): Promise<void> {
  if (command === "logout") {
    await sessionStore.logout();
    await router.replace("/login");
  }
}

function closeTab(tab: RouteTab): void {
  const tabIndex = tabs.value.findIndex((item) => item.path === tab.path);
  if (tabIndex < 0 || !tab.closable) {
    return;
  }
  const isCurrent = route.path === tab.path;
  tabs.value.splice(tabIndex, 1);
  if (isCurrent) {
    const fallbackTab = tabs.value[Math.max(0, tabIndex - 1)];
    void router.push(fallbackTab.path);
  }
}

watch(
  () => route.fullPath,
  () => {
    if (!route.meta.title || route.path === "/login") {
      return;
    }
    const existingTab = tabs.value.find((item) => item.path === route.fullPath);
    if (!existingTab) {
      tabs.value.push({
        path: route.fullPath,
        title: String(route.meta.title),
        closable: route.path !== "/dashboard",
      });
    }
  },
  { immediate: true },
);

applyTheme();
</script>

<template>
  <div class="app-shell" :class="{ 'is-collapsed': collapsed }">
    <aside class="app-sidebar">
      <div class="brand">
        <div class="brand-mark" aria-hidden="true">
          <DataAnalysis />
        </div>
        <div v-if="!collapsed" class="brand-copy">
          <strong>材料实验助手</strong>
          <span>Materials Lab</span>
        </div>
      </div>

      <el-menu
        :default-active="activeMenu"
        :collapse="collapsed"
        :collapse-transition="false"
        router
        class="sidebar-menu"
      >
        <el-menu-item v-for="item in menuItems" :key="item.path" :index="item.path">
          <el-icon><component :is="item.icon" /></el-icon>
          <template #title>{{ item.label }}</template>
        </el-menu-item>
      </el-menu>

      <button class="collapse-button" type="button" @click="collapsed = !collapsed">
        <el-icon>
          <Expand v-if="collapsed" />
          <Fold v-else />
        </el-icon>
        <span v-if="!collapsed">收起导航</span>
      </button>
    </aside>

    <section class="app-workspace">
      <header class="app-header">
        <div class="header-context">
          <el-breadcrumb separator="/">
            <el-breadcrumb-item>材料实验助手</el-breadcrumb-item>
            <el-breadcrumb-item>{{ currentTitle }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>

        <div class="header-actions">
          <el-tooltip :content="isDark ? '切换浅色主题' : '切换深色主题'">
            <button class="icon-button" type="button" @click="toggleTheme">
              <el-icon><Sunny v-if="isDark" /><Moon v-else /></el-icon>
            </button>
          </el-tooltip>
          <el-tooltip content="通知">
            <button class="icon-button" type="button" aria-label="通知">
              <el-badge is-dot>
                <el-icon><Bell /></el-icon>
              </el-badge>
            </button>
          </el-tooltip>
          <span class="header-divider" />
          <el-dropdown trigger="click" @command="handleUserCommand">
            <button class="user-menu" type="button">
              <span class="user-avatar">{{ sessionStore.displayName.slice(0, 1) }}</span>
              <span class="user-name">{{ sessionStore.displayName }}</span>
              <el-icon><ArrowDown /></el-icon>
            </button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

      <nav class="route-tabs" aria-label="已打开页面">
        <button
          v-for="tab in tabs"
          :key="tab.path"
          class="route-tab"
          :class="{ active: route.fullPath === tab.path }"
          type="button"
          @click="router.push(tab.path)"
        >
          <span>{{ tab.title }}</span>
          <span
            v-if="tab.closable"
            class="tab-close"
            role="button"
            tabindex="0"
            aria-label="关闭页面"
            @click.stop="closeTab(tab)"
            @keydown.enter.stop="closeTab(tab)"
          >
            ×
          </span>
        </button>
      </nav>

      <main class="app-main">
        <router-view />
      </main>
    </section>
  </div>
</template>

<style scoped>
.app-shell {
  display: grid;
  grid-template-columns: var(--sidebar-width) minmax(0, 1fr);
  width: 100%;
  height: 100%;
  background: var(--color-bg-page);
  transition: grid-template-columns 160ms ease;
}

.app-shell.is-collapsed {
  grid-template-columns: var(--sidebar-collapsed-width) minmax(0, 1fr);
}

.app-sidebar {
  display: flex;
  flex-direction: column;
  min-width: 0;
  overflow: hidden;
  color: #d8deea;
  background: var(--color-bg-sidebar);
}

.brand {
  display: flex;
  flex: none;
  align-items: center;
  height: var(--header-height);
  padding: 0 15px;
  border-bottom: 1px solid rgb(255 255 255 / 9%);
}

.brand-mark {
  display: grid;
  flex: none;
  width: 34px;
  height: 34px;
  color: #fff;
  background: #4169dd;
  border-radius: 8px;
  place-items: center;
}

.brand-mark :deep(svg) {
  width: 20px;
  height: 20px;
}

.brand-copy {
  display: flex;
  flex-direction: column;
  min-width: 0;
  margin-left: 10px;
}

.brand-copy strong {
  overflow: hidden;
  color: #fff;
  font-size: 14px;
  font-weight: 650;
  white-space: nowrap;
}

.brand-copy span {
  color: #8692a6;
  font-size: 10px;
  letter-spacing: 0.06em;
  text-transform: uppercase;
}

.sidebar-menu {
  flex: 1;
  min-height: 0;
  padding: 12px 8px;
  overflow-x: hidden;
  overflow-y: auto;
  background: transparent;
  border-right: 0;
}

.sidebar-menu:not(.el-menu--collapse) {
  width: 220px;
}

.sidebar-menu :deep(.el-menu-item) {
  height: 42px;
  margin: 3px 0;
  color: #aeb8c8;
  font-size: 13px;
  border-radius: 6px;
}

.sidebar-menu :deep(.el-menu-item .el-icon) {
  font-size: 18px;
}

.sidebar-menu :deep(.el-menu-item:hover) {
  color: #fff;
  background: rgb(255 255 255 / 7%);
}

.sidebar-menu :deep(.el-menu-item.is-active) {
  color: #fff;
  background: #3157c8;
}

.collapse-button {
  display: flex;
  flex: none;
  align-items: center;
  height: 46px;
  padding: 0 22px;
  color: #8e9aae;
  background: transparent;
  border: 0;
  border-top: 1px solid rgb(255 255 255 / 8%);
  cursor: pointer;
}

.collapse-button:hover {
  color: #fff;
}

.collapse-button span {
  margin-left: 10px;
  font-size: 12px;
  white-space: nowrap;
}

.is-collapsed .collapse-button {
  justify-content: center;
  padding: 0;
}

.app-workspace {
  display: grid;
  grid-template-rows: var(--header-height) var(--route-tabs-height) minmax(0, 1fr);
  min-width: 0;
  min-height: 0;
}

.app-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 18px;
  background: var(--color-bg-card);
  border-bottom: 1px solid var(--color-border);
}

.header-context :deep(.el-breadcrumb__inner) {
  color: var(--color-text-secondary);
  font-size: 13px;
  font-weight: 400;
}

.header-context :deep(.el-breadcrumb__item:last-child .el-breadcrumb__inner) {
  color: var(--color-text-primary);
  font-weight: 550;
}

.header-actions,
.user-menu {
  display: flex;
  align-items: center;
}

.header-actions {
  gap: 5px;
}

.icon-button {
  display: grid;
  width: 34px;
  height: 34px;
  color: var(--color-text-secondary);
  background: transparent;
  border: 0;
  border-radius: 6px;
  cursor: pointer;
  place-items: center;
}

.icon-button:hover {
  color: var(--color-text-primary);
  background: var(--color-bg-page);
}

.icon-button .el-icon {
  font-size: 17px;
}

.header-divider {
  width: 1px;
  height: 22px;
  margin: 0 7px;
  background: var(--color-border);
}

.user-menu {
  gap: 8px;
  padding: 2px 4px;
  color: var(--color-text-primary);
  background: transparent;
  border: 0;
  cursor: pointer;
}

.user-avatar {
  display: grid;
  width: 30px;
  height: 30px;
  color: #fff;
  font-size: 13px;
  font-weight: 650;
  background: #3157c8;
  border-radius: 50%;
  place-items: center;
}

.user-name {
  max-width: 100px;
  overflow: hidden;
  font-size: 13px;
  font-weight: 550;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.route-tabs {
  display: flex;
  align-items: flex-end;
  min-width: 0;
  padding: 0 14px;
  overflow-x: auto;
  background: var(--color-bg-card);
  border-bottom: 1px solid var(--color-border);
}

.route-tab {
  position: relative;
  display: flex;
  flex: none;
  align-items: center;
  gap: 7px;
  height: 35px;
  padding: 0 12px;
  color: var(--color-text-secondary);
  font-size: 12px;
  background: transparent;
  border: 0;
  cursor: pointer;
}

.route-tab:hover,
.route-tab.active {
  color: var(--color-primary);
}

.route-tab.active::after {
  position: absolute;
  right: 10px;
  bottom: -1px;
  left: 10px;
  height: 2px;
  background: var(--color-primary);
  content: "";
}

.tab-close {
  display: grid;
  width: 16px;
  height: 16px;
  color: var(--color-text-tertiary);
  font-size: 15px;
  border-radius: 3px;
  place-items: center;
}

.tab-close:hover {
  color: var(--color-text-primary);
  background: var(--color-border);
}

.app-main {
  min-width: 0;
  min-height: 0;
  padding: 16px;
  overflow: hidden;
}

@media (width <= 1279px) {
  .app-shell {
    grid-template-columns: var(--sidebar-collapsed-width) minmax(0, 1fr);
  }

  .brand-copy,
  .collapse-button span {
    display: none;
  }

  .sidebar-menu:not(.el-menu--collapse) {
    width: 64px;
  }

  .sidebar-menu :deep(.el-menu-item span) {
    display: none;
  }

  .collapse-button {
    justify-content: center;
    padding: 0;
  }
}
</style>
