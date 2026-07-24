<script setup lang="ts">
import {
  Edit,
  Key,
  Plus,
  Refresh,
  Search,
} from "@element-plus/icons-vue";
import { ElMessage } from "element-plus";
import { computed, onMounted, reactive, ref } from "vue";

import { getProblemDetail } from "@/api/http";
import { userApi } from "@/api/users";
import type {
  ManagedRoleOption,
  ManagedUser,
  ManagedUserCreateInput,
  ManagedUserStatus,
  ManagedUserUpdateInput,
} from "@/types/api";

const DEFAULT_PASSWORD = "00000000";

const loading = ref(false);
const submitting = ref(false);
const users = ref<ManagedUser[]>([]);
const roleOptions = ref<ManagedRoleOption[]>([]);
const total = ref(0);
const userDialogVisible = ref(false);
const passwordDialogVisible = ref(false);
const editingUser = ref<ManagedUser | null>(null);
const passwordTarget = ref<ManagedUser | null>(null);
const filters = reactive({
  page: 1,
  pageSize: 20,
  search: "",
  status: "" as ManagedUserStatus | "",
  roleCode: "",
});
const userForm = reactive<ManagedUserCreateInput & { passwordConfirm: string }>({
  username: "",
  display_name: "",
  email: "",
  password: DEFAULT_PASSWORD,
  passwordConfirm: DEFAULT_PASSWORD,
  status: "active",
  role_codes: ["researcher"],
});
const passwordForm = reactive({
  password: DEFAULT_PASSWORD,
  passwordConfirm: DEFAULT_PASSWORD,
});

const dialogTitle = computed(() =>
  editingUser.value ? "编辑用户" : "新建用户",
);

const statusOptions: Array<{ label: string; value: ManagedUserStatus }> = [
  { label: "正常", value: "active" },
  { label: "锁定", value: "locked" },
  { label: "禁用", value: "disabled" },
];

/**
 * 格式化日期时间
 *
 * @param value ISO 日期时间
 * @returns 中文日期时间或占位符
 */
function formatDateTime(value: string | null): string {
  if (!value) return "从未登录";
  return new Date(value).toLocaleString("zh-CN", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  });
}

/**
 * 返回用户状态中文名称
 *
 * @param value 用户状态代码
 * @returns 状态名称
 */
function statusLabel(value: ManagedUserStatus): string {
  return statusOptions.find((item) => item.value === value)?.label ?? value;
}

/**
 * 返回 Element Plus 状态标签类型
 *
 * @param value 用户状态代码
 * @returns 标签类型
 */
function statusTagType(
  value: ManagedUserStatus,
): "success" | "warning" | "danger" {
  if (value === "active") return "success";
  if (value === "locked") return "warning";
  return "danger";
}

/**
 * 加载用户分页数据
 */
async function loadUsers(): Promise<void> {
  loading.value = true;
  try {
    const response = await userApi.listManagedUsers({
      page: filters.page,
      page_size: filters.pageSize,
      search: filters.search.trim() || undefined,
      status: filters.status,
      role_code: filters.roleCode || undefined,
    });
    users.value = response.data;
    total.value = response.meta.total;
  } catch (error) {
    const problem = getProblemDetail(error);
    ElMessage.error(problem?.detail ?? "用户列表加载失败");
  } finally {
    loading.value = false;
  }
}

/**
 * 加载可分配系统角色
 */
async function loadRoleOptions(): Promise<void> {
  try {
    roleOptions.value = await userApi.listManagedRoleOptions();
  } catch (error) {
    const problem = getProblemDetail(error);
    ElMessage.error(problem?.detail ?? "系统角色加载失败");
  }
}

/**
 * 应用筛选条件
 */
function applyFilters(): void {
  filters.page = 1;
  void loadUsers();
}

/**
 * 重置筛选条件
 */
function resetFilters(): void {
  filters.search = "";
  filters.status = "";
  filters.roleCode = "";
  filters.page = 1;
  void loadUsers();
}

/**
 * 打开新建用户弹窗
 */
function openCreateDialog(): void {
  editingUser.value = null;
  userForm.username = "";
  userForm.display_name = "";
  userForm.email = "";
  userForm.password = DEFAULT_PASSWORD;
  userForm.passwordConfirm = DEFAULT_PASSWORD;
  userForm.status = "active";
  userForm.role_codes = ["researcher"];
  userDialogVisible.value = true;
}

/**
 * 打开编辑普通用户弹窗
 *
 * @param user 目标用户
 */
function openEditDialog(user: ManagedUser): void {
  if (user.is_super_admin) {
    ElMessage.info("超级管理员账号受保护");
    return;
  }
  editingUser.value = user;
  userForm.username = user.username;
  userForm.display_name = user.display_name;
  userForm.email = user.email;
  userForm.password = "";
  userForm.passwordConfirm = "";
  userForm.status = user.status;
  userForm.role_codes = [...user.role_codes];
  userDialogVisible.value = true;
}

/**
 * 校验用户表单
 *
 * @returns 表单是否有效
 */
function validateUserForm(): boolean {
  if (!/^[A-Za-z0-9._-]{3,64}$/.test(userForm.username.trim())) {
    ElMessage.warning("用户名须为3–64位字母、数字、点、下划线或短横线");
    return false;
  }
  if (!userForm.display_name.trim()) {
    ElMessage.warning("请输入显示名称");
    return false;
  }
  if (!userForm.role_codes.length) {
    ElMessage.warning("至少选择一个系统角色");
    return false;
  }
  if (!editingUser.value) {
    if (userForm.password.length < 8) {
      ElMessage.warning("初始密码至少8位");
      return false;
    }
    if (userForm.password !== userForm.passwordConfirm) {
      ElMessage.warning("两次输入的初始密码不一致");
      return false;
    }
  }
  return true;
}

/**
 * 创建或更新用户
 */
async function submitUser(): Promise<void> {
  if (submitting.value || !validateUserForm()) return;
  submitting.value = true;
  try {
    const commonPayload: ManagedUserUpdateInput = {
      username: userForm.username.trim(),
      display_name: userForm.display_name.trim(),
      email: userForm.email.trim(),
      status: userForm.status,
      role_codes: [...userForm.role_codes],
    };
    if (editingUser.value) {
      await userApi.updateManagedUser(editingUser.value.id, commonPayload);
      ElMessage.success("用户信息已更新");
    } else {
      await userApi.createManagedUser({
        ...commonPayload,
        password: userForm.password,
      });
      ElMessage.success("用户创建成功");
    }
    userDialogVisible.value = false;
    await loadUsers();
  } catch (error) {
    const problem = getProblemDetail(error);
    const fieldMessage = problem?.field_errors
      ? Object.values(problem.field_errors).flat()[0]
      : null;
    ElMessage.error(fieldMessage ?? problem?.detail ?? "用户保存失败");
  } finally {
    submitting.value = false;
  }
}

/**
 * 打开密码重置弹窗
 *
 * @param user 目标用户
 */
function openPasswordDialog(user: ManagedUser): void {
  if (user.is_super_admin) {
    ElMessage.info("超级管理员账号受保护");
    return;
  }
  passwordTarget.value = user;
  passwordForm.password = DEFAULT_PASSWORD;
  passwordForm.passwordConfirm = DEFAULT_PASSWORD;
  passwordDialogVisible.value = true;
}

/**
 * 重置普通用户密码
 */
async function submitPasswordReset(): Promise<void> {
  if (!passwordTarget.value || submitting.value) return;
  if (passwordForm.password.length < 8) {
    ElMessage.warning("新密码至少8位");
    return;
  }
  if (passwordForm.password !== passwordForm.passwordConfirm) {
    ElMessage.warning("两次输入的新密码不一致");
    return;
  }
  submitting.value = true;
  try {
    await userApi.resetManagedUserPassword(
      passwordTarget.value.id,
      passwordForm.password,
    );
    passwordDialogVisible.value = false;
    ElMessage.success(`已重置 ${passwordTarget.value.display_name} 的密码`);
  } catch (error) {
    const problem = getProblemDetail(error);
    ElMessage.error(problem?.detail ?? "密码重置失败");
  } finally {
    submitting.value = false;
  }
}

onMounted(async () => {
  await Promise.all([loadUsers(), loadRoleOptions()]);
});
</script>

<template>
  <section class="page-shell user-page">
    <header class="page-header">
      <div>
        <h1 class="page-title">用户管理</h1>
        <p class="page-description">
          创建组织用户，维护账号状态与系统角色。超级管理员账号受保护。
        </p>
      </div>
      <el-button type="primary" :icon="Plus" @click="openCreateDialog">
        新建用户
      </el-button>
    </header>

    <article class="content-panel user-panel">
      <div class="filter-bar">
        <el-input
          v-model="filters.search"
          :prefix-icon="Search"
          clearable
          placeholder="搜索用户名、姓名或邮箱"
          class="search-input"
          @keyup.enter="applyFilters"
          @clear="applyFilters"
        />
        <el-select
          v-model="filters.status"
          clearable
          placeholder="全部状态"
          class="status-select"
          @change="applyFilters"
        >
          <el-option
            v-for="item in statusOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
        <el-select
          v-model="filters.roleCode"
          clearable
          placeholder="全部角色"
          class="role-select"
          @change="applyFilters"
        >
          <el-option
            v-for="role in roleOptions"
            :key="role.role_code"
            :label="role.name"
            :value="role.role_code"
          />
        </el-select>
        <el-button :icon="Search" @click="applyFilters">查询</el-button>
        <el-button :icon="Refresh" @click="resetFilters">重置</el-button>
        <span class="filter-summary">共 {{ total }} 个用户</span>
      </div>

      <div class="table-wrap">
        <el-table
          v-loading="loading"
          :data="users"
          height="100%"
          row-key="id"
        >
          <el-table-column prop="username" label="用户名" min-width="135" />
          <el-table-column prop="display_name" label="姓名" min-width="110" />
          <el-table-column prop="email" label="邮箱" min-width="175">
            <template #default="{ row }">{{ row.email || "—" }}</template>
          </el-table-column>
          <el-table-column label="系统角色" min-width="170">
            <template #default="{ row }">
              <div class="role-tags">
                <el-tag
                  v-for="roleName in row.role_names"
                  :key="roleName"
                  size="small"
                  effect="plain"
                >
                  {{ roleName }}
                </el-tag>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="92">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.status)" size="small">
                {{ statusLabel(row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="最后登录" width="166">
            <template #default="{ row }">
              {{ formatDateTime(row.last_login) }}
            </template>
          </el-table-column>
          <el-table-column label="操作" width="190" fixed="right">
            <template #default="{ row }">
              <span v-if="row.is_super_admin" class="protected-copy">
                受保护账号
              </span>
              <template v-else>
                <el-button
                  link
                  type="primary"
                  :icon="Edit"
                  @click="openEditDialog(row)"
                >
                  编辑
                </el-button>
                <el-button
                  link
                  type="primary"
                  :icon="Key"
                  @click="openPasswordDialog(row)"
                >
                  重置密码
                </el-button>
              </template>
            </template>
          </el-table-column>
          <template #empty>
            <el-empty description="没有符合条件的用户" :image-size="74" />
          </template>
        </el-table>
      </div>

      <footer class="pagination-bar">
        <el-pagination
          v-model:current-page="filters.page"
          v-model:page-size="filters.pageSize"
          :total="total"
          :page-sizes="[20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="loadUsers"
          @size-change="applyFilters"
        />
      </footer>
    </article>

    <el-dialog
      v-model="userDialogVisible"
      :title="dialogTitle"
      width="640px"
      destroy-on-close
      align-center
    >
      <el-form label-position="top">
        <div class="form-grid">
          <el-form-item label="用户名" required>
            <el-input
              v-model="userForm.username"
              maxlength="64"
              placeholder="字母、数字、点、下划线或短横线"
            />
          </el-form-item>
          <el-form-item label="显示名称" required>
            <el-input v-model="userForm.display_name" maxlength="100" />
          </el-form-item>
          <el-form-item label="邮箱" class="wide">
            <el-input
              v-model="userForm.email"
              maxlength="254"
              placeholder="选填"
            />
          </el-form-item>
          <el-form-item label="账号状态" required>
            <el-select v-model="userForm.status">
              <el-option
                v-for="item in statusOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="系统角色" required>
            <el-select
              v-model="userForm.role_codes"
              multiple
              collapse-tags
              :max-collapse-tags="2"
              placeholder="选择系统角色"
            >
              <el-option
                v-for="role in roleOptions"
                :key="role.role_code"
                :label="role.name"
                :value="role.role_code"
              />
            </el-select>
          </el-form-item>
          <template v-if="!editingUser">
            <el-form-item label="初始密码" required>
              <el-input
                v-model="userForm.password"
                type="password"
                show-password
                maxlength="128"
              />
              <span class="field-help">默认密码为8个0</span>
            </el-form-item>
            <el-form-item label="确认初始密码" required>
              <el-input
                v-model="userForm.passwordConfirm"
                type="password"
                show-password
                maxlength="128"
              />
            </el-form-item>
          </template>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="userDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitUser">
          {{ editingUser ? "保存修改" : "创建用户" }}
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="passwordDialogVisible"
      title="重置用户密码"
      width="480px"
      destroy-on-close
      align-center
    >
      <p class="password-description">
        为 {{ passwordTarget?.display_name }}（{{ passwordTarget?.username }}）设置新密码。
      </p>
      <el-form label-position="top">
        <el-form-item label="新密码" required>
          <el-input
            v-model="passwordForm.password"
            type="password"
            show-password
            maxlength="128"
          />
          <span class="field-help">默认密码为8个0</span>
        </el-form-item>
        <el-form-item label="确认新密码" required>
          <el-input
            v-model="passwordForm.passwordConfirm"
            type="password"
            show-password
            maxlength="128"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="passwordDialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="submitting"
          @click="submitPasswordReset"
        >
          确认重置
        </el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.user-page {
  padding-top: 4px;
}

.user-panel {
  display: flex;
  flex: 1;
  flex-direction: column;
}

.filter-bar {
  display: flex;
  flex: none;
  align-items: center;
  gap: 10px;
  padding: 14px 16px;
  border-bottom: 1px solid var(--color-border);
}

.search-input {
  width: 270px;
}

.status-select {
  width: 132px;
}

.role-select {
  width: 150px;
}

.filter-summary {
  margin-left: auto;
  color: var(--color-text-secondary);
  font-size: 12px;
}

.table-wrap {
  flex: 1;
  min-height: 0;
}

.role-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
}

.protected-copy {
  color: var(--color-text-secondary);
  font-size: 12px;
}

.pagination-bar {
  display: flex;
  flex: none;
  justify-content: flex-end;
  padding: 12px 16px;
  border-top: 1px solid var(--color-border);
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 18px;
}

.form-grid .wide {
  grid-column: 1 / -1;
}

.form-grid :deep(.el-select) {
  width: 100%;
}

.field-help {
  margin-top: 4px;
  color: var(--color-text-secondary);
  font-size: 12px;
}

.password-description {
  margin: 0 0 18px;
  color: var(--color-text-secondary);
  font-size: 13px;
}
</style>
