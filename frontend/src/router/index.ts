import { createRouter, createWebHistory } from "vue-router";

import AppShell from "@/layouts/AppShell.vue";
import { useSessionStore } from "@/stores/session";

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: "/login",
      name: "login",
      component: () => import("@/views/LoginView.vue"),
      meta: { public: true, title: "登录" },
    },
    {
      path: "/",
      component: AppShell,
      children: [
        {
          path: "",
          redirect: "/dashboard",
        },
        {
          path: "dashboard",
          name: "dashboard",
          component: () => import("@/views/DashboardView.vue"),
          meta: { title: "项目总览" },
        },
        {
          path: "projects",
          name: "projects",
          component: () => import("@/views/projects/ProjectListView.vue"),
          meta: { title: "项目数据" },
        },
        {
          path: "eln",
          name: "eln",
          component: () => import("@/views/PlaceholderView.vue"),
          meta: {
            title: "电子实验记录本",
            description: "电子实验记录本页面正在按原型复刻。",
          },
        },
        {
          path: "projects/:projectId",
          name: "project-detail",
          component: () => import("@/views/projects/ProjectDetailView.vue"),
          meta: { title: "项目详情" },
        },
        {
          path: "experiments",
          name: "experiments",
          component: () => import("@/views/PlaceholderView.vue"),
          meta: { title: "实验管理", description: "实验计划与电子实验记录将在下一纵切实现。" },
        },
        {
          path: "testing",
          name: "testing",
          component: () => import("@/views/PlaceholderView.vue"),
          meta: { title: "检测管理", description: "检测委托、结果审核和报告将在后续迭代实现。" },
        },
        {
          path: "materials",
          name: "materials",
          component: () => import("@/views/PlaceholderView.vue"),
          meta: { title: "基础资料", description: "材料、批次、单位和工艺模板将在后续迭代实现。" },
        },
        {
          path: "system",
          name: "system",
          redirect: "/system/users",
          meta: { requiresSuperAdmin: true },
        },
        {
          path: "system/users",
          name: "system-users",
          component: () => import("@/views/system/UserManagementView.vue"),
          meta: { title: "用户管理", requiresSuperAdmin: true },
        },
      ],
    },
  ],
});

router.beforeEach((to) => {
  const sessionStore = useSessionStore();
  if (!to.meta.public && !sessionStore.isAuthenticated) {
    return { name: "login", query: { redirect: to.fullPath } };
  }
  if (to.name === "login" && sessionStore.isAuthenticated) {
    return { name: "dashboard" };
  }
  if (to.meta.requiresSuperAdmin && !sessionStore.isSuperAdmin) {
    return { name: "dashboard" };
  }
  return true;
});

router.afterEach((to) => {
  document.title = `${String(to.meta.title ?? "实验助手")} · 实验助手`;
});

export default router;
