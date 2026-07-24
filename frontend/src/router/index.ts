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
          meta: { title: "工作台" },
        },
        {
          path: "projects",
          name: "projects",
          component: () => import("@/views/projects/ProjectListView.vue"),
          meta: { title: "项目管理" },
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
          component: () => import("@/views/PlaceholderView.vue"),
          meta: { title: "系统管理", description: "用户、角色和审计查询将在后续迭代实现。" },
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
  return true;
});

router.afterEach((to) => {
  document.title = `${String(to.meta.title ?? "材料实验助手")} · 材料实验助手`;
});

export default router;
