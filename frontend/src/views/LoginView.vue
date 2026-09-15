<script setup lang="ts">
import { DataAnalysis, Key, Lock, User } from "@element-plus/icons-vue";
import { computed, reactive, ref } from "vue";
import { useRoute, useRouter } from "vue-router";

import { authApi } from "@/api/auth";
import { getProblemDetail } from "@/api/http";
import { useSessionStore } from "@/stores/session";

const route = useRoute();
const router = useRouter();
const sessionStore = useSessionStore();

const form = reactive({
  username: "",
  password: "",
});
const registrationForm = reactive({
  invitation_code: "",
  username: "",
  display_name: "",
  email: "",
  password: "",
  passwordConfirm: "",
});
const mode = ref<"login" | "register">("login");
const loading = ref(false);
const errorMessage = ref("");
const successMessage = ref("");
const canSubmit = computed(() => form.username.trim() && form.password);
const canRegister = computed(() =>
  registrationForm.invitation_code.trim()
  && registrationForm.username.trim()
  && registrationForm.display_name.trim()
  && registrationForm.password
  && registrationForm.passwordConfirm,
);

async function submit(): Promise<void> {
  if (!canSubmit.value || loading.value) {
    return;
  }
  loading.value = true;
  errorMessage.value = "";
  try {
    await sessionStore.login(form.username.trim(), form.password);
    const redirect = typeof route.query.redirect === "string" ? route.query.redirect : "/dashboard";
    await router.replace(redirect);
  } catch (error) {
    const problem = getProblemDetail(error);
    errorMessage.value = problem?.detail ?? "登录失败，请检查网络后重试";
  } finally {
    loading.value = false;
  }
}

/** 使用管理员签发的邀请码创建普通组织用户。 */
async function submitRegistration(): Promise<void> {
  if (!canRegister.value || loading.value) return;
  const passwordMessage = passwordValidationMessage(
    registrationForm.password,
    registrationForm.username,
  );
  if (passwordMessage) {
    errorMessage.value = passwordMessage;
    return;
  }
  if (registrationForm.password !== registrationForm.passwordConfirm) {
    errorMessage.value = "两次输入的密码不一致";
    return;
  }
  const registrationPayload = {
    invitation_code: registrationForm.invitation_code.trim(),
    username: registrationForm.username.trim(),
    display_name: registrationForm.display_name.trim(),
    email: registrationForm.email.trim() || undefined,
    password: registrationForm.password,
  };
  loading.value = true;
  errorMessage.value = "";
  successMessage.value = "";
  try {
    const csrfToken = await authApi.getCsrfToken();
    await authApi.register(registrationPayload, csrfToken);
    form.username = registrationPayload.username;
    form.password = "";
    registrationForm.invitation_code = "";
    registrationForm.password = "";
    registrationForm.passwordConfirm = "";
    mode.value = "login";
    successMessage.value = "注册完成，请使用新密码登录。";
  } catch (error) {
    const problem = getProblemDetail(error);
    errorMessage.value = problem?.detail ?? "注册失败，请检查邀请码和网络后重试";
  } finally {
    loading.value = false;
  }
}

/** 切换登录与注册面板，并清除上一种操作的错误提示。 */
function switchMode(nextMode: "login" | "register"): void {
  if (loading.value) return;
  form.password = "";
  registrationForm.invitation_code = "";
  registrationForm.password = "";
  registrationForm.passwordConfirm = "";
  mode.value = nextMode;
  errorMessage.value = "";
  successMessage.value = "";
}

/** 返回与服务端一致的密码策略提示；服务端仍是最终校验边界。 */
function passwordValidationMessage(password: string, username: string): string | null {
  if (
    Array.from(password).length < 6
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
  <main class="login-page">
    <section class="login-visual">
      <div class="visual-grid" aria-hidden="true" />
      <div class="visual-content">
        <div class="login-brand">
          <span class="login-brand-mark"><DataAnalysis /></span>
          <span>材料实验助手</span>
        </div>
        <div class="visual-copy">
          <h1>让每一次实验都有迹可循</h1>
          <p>从项目计划到电子实验记录、检测结果与报告归档，在同一条可信链路中协作。</p>
        </div>
        <div class="visual-flow" aria-label="系统核心流程">
          <span>项目</span>
          <i />
          <span>实验</span>
          <i />
          <span>检测</span>
          <i />
          <span>归档</span>
        </div>
      </div>
    </section>

    <section class="login-panel">
      <form
        v-if="mode === 'login'"
        class="login-form"
        @submit.prevent="submit"
      >
        <header>
          <h2>登录工作台</h2>
          <p>使用组织内账号进入材料实验管理系统</p>
        </header>

        <el-alert
          v-if="errorMessage"
          :title="errorMessage"
          type="error"
          :closable="false"
          show-icon
        />
        <el-alert
          v-if="successMessage"
          :title="successMessage"
          type="success"
          :closable="false"
          show-icon
        />

        <label class="field-label" for="username">用户名</label>
        <el-input
          id="username"
          v-model="form.username"
          :prefix-icon="User"
          size="large"
          autocomplete="username"
          placeholder="请输入用户名"
        />

        <label class="field-label" for="password">密码</label>
        <el-input
          id="password"
          v-model="form.password"
          :prefix-icon="Lock"
          size="large"
          type="password"
          show-password
          autocomplete="current-password"
          placeholder="请输入密码"
          @keyup.enter="submit"
        />

        <el-button
          type="primary"
          size="large"
          native-type="submit"
          :loading="loading"
          :disabled="!canSubmit"
          class="submit-button"
        >
          登录
        </el-button>

        <el-button type="text" class="switch-mode" :disabled="loading" @click="switchMode('register')">
          持有管理员邀请码？注册组织账号
        </el-button>
      </form>

      <form
        v-else
        class="login-form registration-form"
        @submit.prevent="submitRegistration"
      >
        <header>
          <h2>注册组织账号</h2>
          <p>邀请码由组织超级管理员签发，注册后默认使用其指定角色。</p>
        </header>

        <el-alert
          v-if="errorMessage"
          :title="errorMessage"
          type="error"
          :closable="false"
          show-icon
        />

        <label class="field-label" for="invitation-code">邀请码</label>
        <el-input
          id="invitation-code"
          v-model="registrationForm.invitation_code"
          :prefix-icon="Key"
          autocomplete="one-time-code"
          placeholder="请输入管理员提供的邀请码"
        />

        <label class="field-label" for="registration-username">用户名</label>
        <el-input
          id="registration-username"
          v-model="registrationForm.username"
          :prefix-icon="User"
          autocomplete="username"
          placeholder="3–64位字母、数字、点、下划线或短横线"
        />

        <label class="field-label" for="registration-display-name">显示名称</label>
        <el-input
          id="registration-display-name"
          v-model="registrationForm.display_name"
          :prefix-icon="User"
          autocomplete="name"
          placeholder="请输入姓名或显示名称"
        />

        <label class="field-label" for="registration-email">邮箱（选填）</label>
        <el-input
          id="registration-email"
          v-model="registrationForm.email"
          autocomplete="email"
          placeholder="用于组织联系"
        />

        <label class="field-label" for="registration-password">密码</label>
        <el-input
          id="registration-password"
          v-model="registrationForm.password"
          :prefix-icon="Lock"
          type="password"
          show-password
          autocomplete="new-password"
          placeholder="至少6位，包含英文字母和数字"
        />

        <label class="field-label" for="registration-password-confirm">确认密码</label>
        <el-input
          id="registration-password-confirm"
          v-model="registrationForm.passwordConfirm"
          :prefix-icon="Lock"
          type="password"
          show-password
          autocomplete="new-password"
          placeholder="请再次输入密码"
        />

        <el-button
          type="primary"
          size="large"
          native-type="submit"
          :loading="loading"
          :disabled="!canRegister"
          class="submit-button"
        >
          注册并返回登录
        </el-button>

        <el-button type="text" class="switch-mode" :disabled="loading" @click="switchMode('login')">
          返回登录
        </el-button>
        <p class="security-note">没有邀请码请联系组织超级管理员；邀请码仅可使用一次。</p>
      </form>
    </section>
  </main>
</template>

<style scoped>
.login-page {
  display: grid;
  grid-template-columns: minmax(480px, 1.15fr) minmax(440px, 0.85fr);
  width: 100%;
  height: 100%;
  min-height: 650px;
  background: #fff;
}

.login-visual {
  position: relative;
  overflow: hidden;
  color: #fff;
  background:
    radial-gradient(circle at 82% 18%, rgb(89 126 229 / 55%), transparent 30%),
    linear-gradient(145deg, #172033 0%, #24355f 55%, #3157c8 100%);
}

.visual-grid {
  position: absolute;
  inset: 0;
  opacity: 0.13;
  background-image:
    linear-gradient(rgb(255 255 255 / 25%) 1px, transparent 1px),
    linear-gradient(90deg, rgb(255 255 255 / 25%) 1px, transparent 1px);
  background-size: 52px 52px;
  mask-image: linear-gradient(to bottom right, #000, transparent 80%);
}

.visual-content {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  height: 100%;
  padding: 38px 54px 58px;
}

.login-brand {
  display: flex;
  align-items: center;
  gap: 11px;
  font-size: 15px;
  font-weight: 650;
}

.login-brand-mark {
  display: grid;
  width: 34px;
  height: 34px;
  background: rgb(255 255 255 / 14%);
  border: 1px solid rgb(255 255 255 / 22%);
  border-radius: 8px;
  place-items: center;
}

.login-brand-mark :deep(svg) {
  width: 19px;
}

.visual-copy {
  max-width: 560px;
  margin: auto 0 72px;
}

.visual-copy h1 {
  max-width: 520px;
  margin: 0;
  font-size: clamp(38px, 4vw, 58px);
  font-weight: 650;
  line-height: 1.16;
  letter-spacing: -0.035em;
}

.visual-copy p {
  max-width: 510px;
  margin: 22px 0 0;
  color: rgb(255 255 255 / 72%);
  font-size: 16px;
  line-height: 1.8;
}

.visual-flow {
  display: flex;
  align-items: center;
  width: fit-content;
  color: rgb(255 255 255 / 78%);
  font-size: 12px;
  letter-spacing: 0.03em;
}

.visual-flow i {
  width: 34px;
  height: 1px;
  margin: 0 11px;
  background: rgb(255 255 255 / 28%);
}

.login-panel {
  display: grid;
  overflow-y: auto;
  background: #fff;
  place-items: center;
}

.login-form {
  display: flex;
  flex-direction: column;
  width: 360px;
}

.registration-form {
  padding: 32px 0;
}

.switch-mode {
  align-self: center;
  margin-top: 14px;
}

.login-form header {
  margin-bottom: 28px;
}

.login-form h2 {
  margin: 0;
  color: #172033;
  font-size: 27px;
  font-weight: 650;
  letter-spacing: -0.02em;
}

.login-form header p {
  margin: 8px 0 0;
  color: #667085;
  font-size: 13px;
}

.login-form :deep(.el-alert) {
  margin-bottom: 20px;
}

.field-label {
  margin: 0 0 7px;
  color: #344054;
  font-size: 13px;
  font-weight: 550;
}

.login-form :deep(.el-input) {
  margin-bottom: 18px;
}

.login-form :deep(.el-input__wrapper) {
  min-height: 42px;
  border-radius: 6px;
  box-shadow: 0 0 0 1px #d0d5dd inset;
}

.login-form :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px #3157c8 inset;
}

.submit-button {
  width: 100%;
  height: 42px;
  margin-top: 5px;
  background: #3157c8;
  border-color: #3157c8;
}

.security-note {
  margin: 15px 0 0;
  color: #98a2b3;
  font-size: 11px;
  text-align: center;
}

@media (width <= 1100px) {
  .login-page {
    grid-template-columns: 1fr;
    min-width: 0;
  }

  .login-visual {
    display: none;
  }

  .login-form {
    width: min(360px, calc(100vw - 40px));
  }
}
</style>
