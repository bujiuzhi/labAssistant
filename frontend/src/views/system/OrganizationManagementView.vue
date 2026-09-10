<script setup lang="ts">
import { Plus, Refresh } from "@element-plus/icons-vue";
import { ElMessage } from "element-plus";
import { onMounted, reactive, ref } from "vue";

import { organizationApi } from "@/api/organizations";
import { getProblemDetail } from "@/api/http";
import type { PlatformOrganization, PlatformOrganizationCreateInput } from "@/types/api";

const loading = ref(false);
const submitting = ref(false);
const dialogVisible = ref(false);
const organizations = ref<PlatformOrganization[]>([]);
const form = reactive<PlatformOrganizationCreateInput & { passwordConfirm: string }>({
  organization_code: "",
  organization_name: "",
  admin_username: "",
  admin_display_name: "",
  admin_email: "",
  admin_password: "",
  passwordConfirm: "",
});

/** 加载仅包含基础元数据的组织目录。 */
async function loadOrganizations(): Promise<void> {
  loading.value = true;
  try {
    organizations.value = await organizationApi.list();
  } catch (error) {
    ElMessage.error(getProblemDetail(error)?.detail ?? "组织目录加载失败");
  } finally {
    loading.value = false;
  }
}

/** 打开组织开通窗口并清除上次输入的敏感信息。 */
function openCreateDialog(): void {
  form.organization_code = "";
  form.organization_name = "";
  form.admin_username = "";
  form.admin_display_name = "";
  form.admin_email = "";
  form.admin_password = "";
  form.passwordConfirm = "";
  dialogVisible.value = true;
}

/** 提交前进行与服务端一致的基础校验；服务端仍是最终授权和校验边界。 */
function validationMessage(): string | null {
  if (!/^[A-Z0-9][A-Z0-9_-]{1,31}$/.test(form.organization_code.trim().toUpperCase())) {
    return "组织编码须为2–32位大写字母、数字、下划线或短横线";
  }
  if (!form.organization_name.trim() || !form.admin_display_name.trim()) return "请填写组织名称和管理员显示名称";
  if (!/^[A-Za-z0-9._-]{3,64}$/.test(form.admin_username.trim())) return "管理员用户名格式不正确";
  if (form.admin_password.length < 6 || new TextEncoder().encode(form.admin_password).length > 72 || /\s/.test(form.admin_password)
      || !/[A-Za-z]/.test(form.admin_password) || !/[0-9]/.test(form.admin_password)
      || form.admin_password.toLocaleLowerCase() === form.admin_username.trim().toLocaleLowerCase()) {
    return "密码须至少6位且不超过72个UTF-8字节，包含英文字母和数字，不含空白且不得与用户名相同";
  }
  if (form.admin_password !== form.passwordConfirm) return "两次输入的初始密码不一致";
  return null;
}

/** 原子开通新组织，成功后只保留目录信息，前端不会持有初始密码。 */
async function submit(): Promise<void> {
  if (submitting.value) return;
  const message = validationMessage();
  if (message) {
    ElMessage.warning(message);
    return;
  }
  submitting.value = true;
  try {
    await organizationApi.create({
      organization_code: form.organization_code.trim().toUpperCase(),
      organization_name: form.organization_name.trim(),
      admin_username: form.admin_username.trim(),
      admin_display_name: form.admin_display_name.trim(),
      admin_email: form.admin_email?.trim() || undefined,
      admin_password: form.admin_password,
    });
    dialogVisible.value = false;
    ElMessage.success("组织已开通，首个管理员可使用所设账号登录");
    await loadOrganizations();
  } catch (error) {
    ElMessage.error(getProblemDetail(error)?.detail ?? "组织开通失败");
  } finally {
    submitting.value = false;
  }
}

function formatDateTime(value: string | null): string {
  if (!value) return "刚刚创建";
  return new Date(value).toLocaleString("zh-CN", { dateStyle: "medium", timeStyle: "short", hour12: false });
}

onMounted(() => { void loadOrganizations(); });
</script>

<template>
  <section class="page-shell organization-page">
    <header class="page-header">
      <div>
        <h1 class="page-title">组织管理</h1>
        <p class="page-description">开通独立组织及其首个超级管理员。组织间项目、实验、文件、用户和邀请码均保持隔离。</p>
      </div>
      <div class="header-actions">
        <el-button :icon="Refresh" @click="loadOrganizations">刷新</el-button>
        <el-button type="primary" :icon="Plus" @click="openCreateDialog">开通组织</el-button>
      </div>
    </header>

    <article class="content-panel organization-panel">
      <el-table v-loading="loading" :data="organizations" row-key="id" height="100%">
        <el-table-column prop="organization_code" label="组织编码" min-width="150" />
        <el-table-column prop="name" label="组织名称" min-width="220" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }"><el-tag :type="row.status === 'active' ? 'success' : 'info'" size="small">{{ row.status === "active" ? "正常" : row.status }}</el-tag></template>
        </el-table-column>
        <el-table-column label="开通时间" min-width="180">
          <template #default="{ row }">{{ formatDateTime(row.created_at) }}</template>
        </el-table-column>
        <template #empty><el-empty description="暂无已开通组织" :image-size="74" /></template>
      </el-table>
    </article>

    <el-dialog v-model="dialogVisible" title="开通组织" width="680px" destroy-on-close align-center>
      <p class="dialog-hint">将一次性创建组织、三项内置角色和该组织的首个超级管理员；不会创建任何项目、实验或测试数据。</p>
      <el-form label-position="top">
        <div class="form-grid">
          <el-form-item label="组织编码" required>
            <el-input v-model="form.organization_code" maxlength="32" placeholder="例如 LAB_A" @input="form.organization_code = form.organization_code.toUpperCase()" />
          </el-form-item>
          <el-form-item label="组织名称" required><el-input v-model="form.organization_name" maxlength="200" /></el-form-item>
          <el-form-item label="管理员用户名" required><el-input v-model="form.admin_username" maxlength="64" /></el-form-item>
          <el-form-item label="管理员显示名称" required><el-input v-model="form.admin_display_name" maxlength="100" /></el-form-item>
          <el-form-item label="管理员邮箱" class="wide"><el-input v-model="form.admin_email" maxlength="254" placeholder="选填" /></el-form-item>
          <el-form-item label="管理员初始密码" required>
            <el-input v-model="form.admin_password" type="password" show-password maxlength="128" />
            <span class="field-help">至少6位且不超过72个UTF-8字节，包含英文字母和数字；不得含空白或与用户名相同</span>
          </el-form-item>
          <el-form-item label="确认初始密码" required><el-input v-model="form.passwordConfirm" type="password" show-password maxlength="128" /></el-form-item>
        </div>
      </el-form>
      <template #footer><el-button @click="dialogVisible = false">取消</el-button><el-button type="primary" :loading="submitting" @click="submit">确认开通</el-button></template>
    </el-dialog>
  </section>
</template>

<style scoped>
.organization-page, .organization-panel { min-height: 0; }
.organization-page { display: flex; flex: 1; flex-direction: column; gap: 16px; }
.organization-panel { flex: 1; display: flex; padding: 0; overflow: hidden; }
.organization-panel :deep(.el-table) { flex: 1; }
.header-actions { display: flex; gap: 10px; }
.dialog-hint { margin: 0 0 18px; color: var(--el-text-color-secondary); line-height: 1.65; }
.form-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0 16px; }
.wide { grid-column: 1 / -1; }
.field-help { display: block; margin-top: 6px; color: var(--el-text-color-secondary); font-size: 12px; line-height: 1.45; }
@media (max-width: 680px) { .form-grid { grid-template-columns: 1fr; } }
</style>
