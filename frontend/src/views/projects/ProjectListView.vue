<script setup lang="ts">
import { Plus, Refresh, Search } from "@element-plus/icons-vue";
import type { FormInstance, FormRules } from "element-plus";
import { ElMessage } from "element-plus";
import { computed, nextTick, onMounted, reactive, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";

import { getProblemDetail } from "@/api/http";
import { projectApi } from "@/api/projects";
import ProjectStatusTag from "@/components/ProjectStatusTag.vue";
import { useSessionStore } from "@/stores/session";
import type { Project, ProjectCreateInput, ProjectStatus } from "@/types/api";

const route = useRoute();
const router = useRouter();
const sessionStore = useSessionStore();
const projectTypeLabels: Record<string, string> = {
  research: "研发项目",
  validation: "验证项目",
  commissioned: "委托项目",
};

const loading = ref(false);
const projects = ref<Project[]>([]);
const total = ref(0);
const createVisible = ref(false);
const submitting = ref(false);
const createFormRef = ref<FormInstance>();
const filters = reactive({
  page: 1,
  pageSize: 20,
  search: "",
  status: "" as ProjectStatus | "",
});
const createForm = reactive<ProjectCreateInput>({
  name: "",
  project_type_code: "research",
  description: "",
  owner_id: "",
  planned_start_date: null,
  planned_end_date: null,
});

const rules: FormRules<ProjectCreateInput> = {
  name: [{ required: true, message: "请输入项目名称", trigger: "blur" }],
  project_type_code: [{ required: true, message: "请选择项目类型", trigger: "change" }],
  planned_end_date: [
    {
      validator: (_rule, value, callback) => {
        if (
          value &&
          createForm.planned_start_date &&
          value < createForm.planned_start_date
        ) {
          callback(new Error("计划结束日期不能早于开始日期"));
          return;
        }
        callback();
      },
      trigger: "change",
    },
  ],
};

const emptyDescription = computed(() =>
  filters.search || filters.status
    ? "没有符合当前筛选条件的项目"
    : "创建第一个项目，开始沉淀实验过程与数据。",
);

function formatDate(value: string | null): string {
  return value ? new Date(`${value}T00:00:00`).toLocaleDateString("zh-CN") : "—";
}

function formatProjectType(value: string): string {
  return projectTypeLabels[value] ?? value;
}

async function loadProjects(): Promise<void> {
  loading.value = true;
  try {
    const response = await projectApi.list({
      page: filters.page,
      page_size: filters.pageSize,
      search: filters.search.trim() || undefined,
      status: filters.status,
      ordering: "-updated_at",
    });
    projects.value = response.data;
    total.value = response.meta.total;
  } catch (error) {
    const problem = getProblemDetail(error);
    ElMessage.error(problem?.detail ?? "项目列表加载失败");
  } finally {
    loading.value = false;
  }
}

function applyFilters(): void {
  filters.page = 1;
  void loadProjects();
}

function resetFilters(): void {
  filters.search = "";
  filters.status = "";
  filters.page = 1;
  void loadProjects();
}

function openCreateDialog(): void {
  createForm.name = "";
  createForm.project_type_code = "research";
  createForm.description = "";
  createForm.owner_id = sessionStore.user?.id ?? "";
  createForm.planned_start_date = null;
  createForm.planned_end_date = null;
  createVisible.value = true;
  void nextTick(() => createFormRef.value?.clearValidate());
}

async function submitProject(): Promise<void> {
  const valid = await createFormRef.value?.validate().catch(() => false);
  if (!valid || submitting.value) {
    return;
  }
  submitting.value = true;
  try {
    const project = await projectApi.create(createForm);
    ElMessage.success("项目创建成功");
    createVisible.value = false;
    await router.push(`/projects/${project.id}`);
  } catch (error) {
    const problem = getProblemDetail(error);
    ElMessage.error(problem?.detail ?? "项目创建失败");
  } finally {
    submitting.value = false;
  }
}

watch(
  () => route.query.create,
  (value) => {
    if (value === "1" && sessionStore.hasPermission("project.create")) {
      openCreateDialog();
      void router.replace({ name: "projects" });
    }
  },
  { immediate: true },
);

onMounted(loadProjects);
</script>

<template>
  <section class="page-shell project-page">
    <header class="page-header">
      <div>
        <h1 class="page-title">项目管理</h1>
        <p class="page-description">管理项目范围、负责人、计划周期与当前状态。</p>
      </div>
      <el-button
        v-if="sessionStore.hasPermission('project.create')"
        type="primary"
        :icon="Plus"
        @click="openCreateDialog"
      >
        新建项目
      </el-button>
    </header>

    <article class="content-panel project-panel">
      <div class="filter-bar">
        <el-input
          v-model="filters.search"
          :prefix-icon="Search"
          clearable
          placeholder="搜索项目编号或名称"
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
          <el-option label="草稿" value="draft" />
          <el-option label="进行中" value="active" />
          <el-option label="已暂停" value="suspended" />
          <el-option label="已完成" value="completed" />
          <el-option label="已归档" value="archived" />
        </el-select>
        <el-button :icon="Search" @click="applyFilters">查询</el-button>
        <el-button :icon="Refresh" @click="resetFilters">重置</el-button>
        <span class="filter-summary">共 {{ total }} 个项目</span>
      </div>

      <div class="table-wrap">
        <el-table
          v-loading="loading"
          :data="projects"
          height="100%"
          row-key="id"
          @row-click="(row: Project) => router.push(`/projects/${row.id}`)"
        >
          <el-table-column prop="project_no" label="项目编号" width="170" />
          <el-table-column prop="name" label="项目名称" min-width="160" show-overflow-tooltip />
          <el-table-column prop="project_type_code" label="类型" width="100">
            <template #default="{ row }">
              {{ formatProjectType(row.project_type_code) }}
            </template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }"><ProjectStatusTag :status="row.status" /></template>
          </el-table-column>
          <el-table-column prop="owner_display_name" label="负责人" width="110" />
          <el-table-column label="计划周期" width="180">
            <template #default="{ row }">
              {{ formatDate(row.planned_start_date) }} – {{ formatDate(row.planned_end_date) }}
            </template>
          </el-table-column>
          <el-table-column label="更新时间" width="110">
            <template #default="{ row }">
              {{ new Date(row.updated_at).toLocaleDateString("zh-CN") }}
            </template>
          </el-table-column>
          <template #empty>
            <el-empty :description="emptyDescription" :image-size="74" />
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
          @current-change="loadProjects"
          @size-change="applyFilters"
        />
      </footer>
    </article>

    <el-dialog
      v-model="createVisible"
      title="新建项目"
      width="620px"
      destroy-on-close
      align-center
    >
      <el-form
        ref="createFormRef"
        :model="createForm"
        :rules="rules"
        label-position="top"
        class="project-form"
      >
        <div class="form-grid">
          <el-form-item label="项目名称" prop="name" class="wide">
            <el-input v-model="createForm.name" maxlength="200" show-word-limit />
          </el-form-item>
          <el-form-item label="项目类型" prop="project_type_code">
            <el-select v-model="createForm.project_type_code">
              <el-option label="研发项目" value="research" />
              <el-option label="验证项目" value="validation" />
              <el-option label="委托项目" value="commissioned" />
            </el-select>
          </el-form-item>
          <el-form-item label="负责人">
            <el-input :model-value="sessionStore.displayName" disabled />
          </el-form-item>
          <el-form-item label="计划开始日期" prop="planned_start_date">
            <el-date-picker
              v-model="createForm.planned_start_date"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="选择日期"
            />
          </el-form-item>
          <el-form-item label="计划结束日期" prop="planned_end_date">
            <el-date-picker
              v-model="createForm.planned_end_date"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="选择日期"
            />
          </el-form-item>
          <el-form-item label="项目说明" prop="description" class="wide">
            <el-input
              v-model="createForm.description"
              type="textarea"
              :rows="4"
              maxlength="10000"
              show-word-limit
              placeholder="说明项目目标、范围与预期成果"
            />
          </el-form-item>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitProject">
          创建项目
        </el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.project-panel {
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
  width: 280px;
}

.status-select {
  width: 145px;
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

.table-wrap :deep(.el-table__row) {
  cursor: pointer;
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

.project-form :deep(.el-select),
.project-form :deep(.el-date-editor) {
  width: 100%;
}
</style>
