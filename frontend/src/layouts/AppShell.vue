<script setup lang="ts">
import { Icon } from "@iconify/vue";
import { computed } from "vue";
import { useRoute, useRouter } from "vue-router";

import { useSessionStore } from "@/stores/session";

const route = useRoute();
const router = useRouter();
const sessionStore = useSessionStore();
const baseProductNavigation = [
  { path: "/dashboard", label: "项目总览" },
  { path: "/projects", label: "项目数据" },
  { path: "/eln", label: "电子实验记录本" },
];
const productNavigation = computed(() => [
  ...baseProductNavigation,
  ...(sessionStore.isSuperAdmin ? [{ path: "/system/users", label: "用户管理" }] : []),
]);

function isProductRoute(path: string): boolean {
  if (path === "/projects") return route.path.startsWith("/projects");
  if (path === "/system/users") return route.path.startsWith("/system");
  return route.path === path;
}

/** 注销当前会话并返回登录页。 */
async function logout(): Promise<void> {
  try {
    await sessionStore.logout();
  } finally {
    await router.replace("/login");
  }
}
</script>

<template>
  <div class="assistant-shell">
    <header class="assistant-header">
      <nav class="product-nav" aria-label="实验助手导航">
        <RouterLink
          v-for="item in productNavigation"
          :key="item.path"
          :to="item.path"
          :class="{ active: isProductRoute(item.path) }"
        >{{ item.label }}</RouterLink>
      </nav>
      <button class="logout-button" type="button" @click="logout">
        <Icon icon="tabler:logout" />退出登录
      </button>
    </header>
    <main class="route-content"><RouterView /></main>
  </div>
</template>

<style scoped>
.assistant-shell { display:flex; width:100%; height:100%; min-width:0; flex-direction:column; overflow:hidden; color:var(--color-ink); background:#f2f6fa; }
.assistant-header { z-index:2; display:flex; height:48px; flex:0 0 48px; align-items:stretch; padding:0 18px; background:#fff; border-bottom:1px solid #e6ebf2; }
.product-nav { display:flex; min-width:0; align-items:stretch; gap:30px; }
.product-nav a { position:relative; display:inline-flex; align-items:center; color:#4f5d70; font-size:13px; font-weight:600; white-space:nowrap; }
.product-nav a::after { position:absolute; right:0; bottom:-1px; left:0; height:2px; background:transparent; border-radius:2px 2px 0 0; content:""; }
.product-nav a:hover,.product-nav a.active { color:#172033; }
.product-nav a.active::after { background:#087cf0; }
.logout-button { display:inline-flex; align-items:center; margin-left:auto; padding:0 4px; color:#536174; font-size:12px; background:transparent; border:0; cursor:pointer; gap:5px; }
.logout-button:hover { color:#172033; }
.logout-button svg { width:15px; height:15px; }
.route-content { min-width:0; min-height:0; flex:1; overflow:hidden; padding:0 18px; }
@media(max-width:760px){.assistant-header{padding:0 12px}.product-nav{overflow-x:auto;gap:22px}.logout-button{font-size:0}.route-content{padding:0 10px}}
</style>
