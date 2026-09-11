<script setup lang="ts">
import { Icon } from "@iconify/vue/offline";
import { ElMessage } from "element-plus";
import { computed, reactive, ref } from "vue";
import { useRoute, useRouter } from "vue-router";

import { authApi } from "@/api/auth";
import { getProblemDetail } from "@/api/http";
import { useSessionStore } from "@/stores/session";

const route = useRoute();
const router = useRouter();
const sessionStore = useSessionStore();
const passwordDialogVisible = ref(false);
const passwordSubmitting = ref(false);
const passwordForm = reactive({
  currentPassword: "",
  newPassword: "",
  newPasswordConfirm: "",
});
const baseProductNavigation = [
  { path: "/dashboard", label: "项目总览" },
  { path: "/projects", label: "项目数据" },
  { path: "/eln", label: "电子实验记录本" },
];
const productNavigation = computed(() => [
  ...(sessionStore.isPlatformAdmin && !sessionStore.isSuperAdmin ? [] : baseProductNavigation),
  ...(sessionStore.isSuperAdmin ? [{ path: "/system/users", label: "用户管理" }] : []),
  ...(sessionStore.isPlatformAdmin ? [{ path: "/system/organizations", label: "组织管理" }] : []),
]);

function isProductRoute(path: string): boolean {
  if (path === "/projects") return route.path.startsWith("/projects");
  if (path.startsWith("/system/")) return route.path === path;
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

/** 打开当前用户自助改密窗口。 */
function openPasswordDialog(): void {
  passwordForm.currentPassword = "";
  passwordForm.newPassword = "";
  passwordForm.newPasswordConfirm = "";
  passwordDialogVisible.value = true;
}

/** 校验旧密码并提交新密码；服务端完成后当前会话会立即失效。 */
async function changePassword(): Promise<void> {
  if (passwordSubmitting.value) return;
  const validationMessage = passwordValidationMessage(passwordForm.newPassword, sessionStore.user?.username ?? "");
  if (validationMessage) {
    ElMessage.warning(validationMessage);
    return;
  }
  if (!passwordForm.currentPassword) {
    ElMessage.warning("请输入当前密码");
    return;
  }
  if (passwordForm.newPassword !== passwordForm.newPasswordConfirm) {
    ElMessage.warning("两次输入的新密码不一致");
    return;
  }
  passwordSubmitting.value = true;
  try {
    const csrfToken = await authApi.getCsrfToken();
    await authApi.changePassword(passwordForm.currentPassword, passwordForm.newPassword, csrfToken);
    await sessionStore.clearSession();
    passwordDialogVisible.value = false;
    ElMessage.success("密码已修改，请使用新密码重新登录");
    await router.replace("/login");
  } catch (error) {
    const problem = getProblemDetail(error);
    ElMessage.error(problem?.detail ?? "密码修改失败，请稍后重试");
  } finally {
    passwordSubmitting.value = false;
  }
}

/** 返回与服务端一致的密码策略提示；服务端仍是最终校验边界。 */
function passwordValidationMessage(password: string, username: string): string | null {
  if (
    password.length < 6
    || new TextEncoder().encode(password).length > 72
    || /\s/.test(password)
    || !/[A-Za-z]/.test(password)
    || !/[0-9]/.test(password)
    || password.toLocaleLowerCase() === username.trim().toLocaleLowerCase()
  ) {
    return "密码须至少6位且不超过72个UTF-8字节，包含英文字母和数字，不含空白且不得与用户名相同";
  }
  return null;
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
      <div class="account-actions">
        <div v-if="sessionStore.user" class="account-identity" :title="`当前登录账户：${sessionStore.user.username}`">
          <Icon icon="tabler:user-circle" />
          <span>{{ sessionStore.user.display_name }}（{{ sessionStore.user.username }}）</span>
        </div>
        <button class="logout-button" type="button" @click="openPasswordDialog">
          <Icon icon="tabler:key" />修改密码
        </button>
        <button class="logout-button" type="button" @click="logout">
          <Icon icon="tabler:logout" />退出登录
        </button>
      </div>
    </header>
    <main class="route-content"><RouterView /></main>
    <el-dialog v-model="passwordDialogVisible" title="修改密码" width="460px" destroy-on-close align-center>
      <p class="password-description">修改成功后会退出当前登录，需要使用新密码重新进入系统。</p>
      <el-form label-position="top">
        <el-form-item label="当前密码" required>
          <el-input v-model="passwordForm.currentPassword" type="password" show-password autocomplete="current-password" />
        </el-form-item>
        <el-form-item label="新密码" required>
          <el-input v-model="passwordForm.newPassword" type="password" show-password autocomplete="new-password" />
          <span class="field-help">至少6位且不超过72个UTF-8字节，包含英文字母和数字；不得含空白或与用户名相同</span>
        </el-form-item>
        <el-form-item label="确认新密码" required>
          <el-input v-model="passwordForm.newPasswordConfirm" type="password" show-password autocomplete="new-password" @keyup.enter="changePassword" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="passwordDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="passwordSubmitting" @click="changePassword">确认修改</el-button>
      </template>
    </el-dialog>
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
.account-actions { display:flex; align-items:center; margin-left:auto; gap:18px; }
.account-identity { display:inline-flex; max-width:280px; align-items:center; overflow:hidden; color:#334155; font-size:12px; font-weight:600; text-overflow:ellipsis; white-space:nowrap; gap:5px; }
.account-identity span { overflow:hidden; text-overflow:ellipsis; }
.account-identity svg { width:16px; height:16px; flex:0 0 auto; color:#087cf0; }
.logout-button { display:inline-flex; align-items:center; padding:0 4px; color:#536174; font-size:12px; background:transparent; border:0; cursor:pointer; gap:5px; }
.logout-button:hover { color:#172033; }
.logout-button svg { width:15px; height:15px; }
.route-content { min-width:0; min-height:0; flex:1; overflow:hidden; padding:0 18px; }
.password-description { margin:0 0 18px; color:#66758a; font-size:13px; line-height:1.6; }
.field-help { display:block; margin-top:6px; color:#7a8798; font-size:12px; line-height:1.5; }
@media(max-width:760px){.assistant-header{padding:0 12px}.product-nav{overflow-x:auto;gap:22px}.account-actions{gap:8px}.account-identity{max-width:112px}.logout-button{font-size:0}.route-content{padding:0 10px}}
</style>
