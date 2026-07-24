<script setup lang="ts">
import { DataAnalysis, Lock, User } from "@element-plus/icons-vue";
import { computed, reactive, ref } from "vue";
import { useRoute, useRouter } from "vue-router";

import { getProblemDetail } from "@/api/http";
import { useSessionStore } from "@/stores/session";

const route = useRoute();
const router = useRouter();
const sessionStore = useSessionStore();

const form = reactive({
  username: "",
  password: "",
});
const loading = ref(false);
const errorMessage = ref("");
const canSubmit = computed(() => form.username.trim() && form.password);

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
      <form class="login-form" @submit.prevent="submit">
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

        <div class="development-accounts">
          <p>开发账号（统一密码：<code>00000000</code>）</p>
          <div>
            <code>admin</code>
            <code>manager</code>
            <code>researcher</code>
            <code>inspector</code>
          </div>
        </div>

        <p class="security-note">登录会话仅保存在受保护的浏览器 Cookie 中</p>
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
  background: #fff;
  place-items: center;
}

.login-form {
  display: flex;
  flex-direction: column;
  width: 360px;
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

.development-accounts {
  margin-top: 18px;
  padding: 11px 12px;
  color: #667085;
  font-size: 11px;
  background: #f8fafc;
  border: 1px solid #eaecf0;
  border-radius: 6px;
}

.development-accounts p {
  margin: 0 0 8px;
}

.development-accounts div {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.development-accounts code {
  padding: 2px 5px;
  color: #344054;
  background: #eef2ff;
  border-radius: 4px;
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
    min-width: 1024px;
  }

  .login-visual {
    display: none;
  }
}
</style>
