<script setup lang="ts">
import { ArrowLeft, Edit, RefreshRight } from "@element-plus/icons-vue";
import type { FormInstance, FormRules } from "element-plus";
import { ElMessage } from "element-plus";
import { computed, onMounted, reactive, ref } from "vue";
import { useRoute, useRouter } from "vue-router";

import { getProblemDetail } from "@/api/http";
import { projectApi } from "@/api/projects";
import ProjectStatusTag from "@/components/ProjectStatusTag.vue";
import { useSessionStore } from "@/stores/session";
import type { Project, ProjectCreateInput } from "@/types/api";

const route = useRoute();
const router = useRouter();
const sessionStore = useSessionStore();
const projectTypeLabels: Record<string, string> = {
  research: "研发项目",
  validation: "验证项目",
  commissioned: "委托项目",
};

const project = ref<Project | null>(null);
const loading = ref(false);
const editVisible = ref(false);
const submitting = ref(false);
const editFormRef = ref<FormInstance>();
const editForm = reactive<Partial<ProjectCreateInput>>({
  name: "",
  project_type_code: "",
  description: "",
  planned_start_date: null,
  planned_end_date: null,
});

const rules: FormRules = {
  name: [{ required: true, message: "请输入项目名称", trigger: "blur" }],
  project_type_code: [{ required: true, message: "请选择项目类型", trigger: "change" }],
};

const canEdit = computed(
  () =>
    sessionStore.hasPermission("project.update") &&
    project.value !== null &&
    ["draft", "active", "suspended"].includes(project.value.status),
);

function formatDate(value: string | null): string {
  return value ? new Date(`${value}T00:00:00`).toLocaleDateString("zh-CN") : "未设置";
}

function formatProjectType(value: string): string {
  return projectTypeLabels[value] ?? value;
}

function formatDateTime(value: string): string {
  return new Intl.DateTimeFormat("zh-CN", {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(new Date(value));
}

async function loadProject(): Promise<void> {
  loading.value = true;
  try {
    project.value = await projectApi.get(String(route.params.projectId));
  } catch (error) {
    const problem = getProblemDetail(error);
    ElMessage.error(problem?.detail ?? "项目详情加载失败");
  } finally {
    loading.value = false;
  }
}

function openEditDialog(): void {
  if (!project.value) {
    return;
  }
  editForm.name = project.value.name;
  editForm.project_type_code = project.value.project_type_code;
  editForm.description = project.value.description;
  editForm.planned_start_date = project.value.planned_start_date;
  editForm.planned_end_date = project.value.planned_end_date;
  editVisible.value = true;
}

async function submitEdit(): Promise<void> {
  const valid = await editFormRef.value?.validate().catch(() => false);
  if (!valid || submitting.value || !project.value) {
    return;
  }
  if (
    editForm.planned_start_date &&
    editForm.planned_end_date &&
    editForm.planned_end_date < editForm.planned_start_date
  ) {
    ElMessage.warning("计划结束日期不能早于开始日期");
    return;
  }
  submitting.value = true;
  try {
    project.value = await projectApi.update(
      project.value.id,
      project.value.version,
      editForm,
    );
    editVisible.value = false;
    ElMessage.success("项目已更新");
  } catch (error) {
    const problem = getProblemDetail(error);
    if (problem?.status === 412) {
      ElMessage.warning("项目已被其他人修改，正在刷新最新数据");
      await loadProject();
    } else {
      ElMessage.error(problem?.detail ?? "项目更新失败");
    }
  } finally {
    submitting.value = false;
  }
}

onMounted(loadProject);
</script>

<template>
  <section v-loading="loading" class="page-shell project-detail-page">
    <header class="page-header detail-header">
      <div class="detail-heading">
        <el-button text :icon="ArrowLeft" @click="router.push('/projects')">返回</el-button>
        <div v-if="project">
          <div class="heading-line">
            <span class="project-number">{{ project.project_no }}</span>
            <ProjectStatusTag :status="project.status" />
          </div>
          <h1 class="page-title">{{ project.name }}</h1>
          <p class="page-description">负责人：{{ project.owner_display_name }}</p>
        </div>
      </div>
      <div v-if="project" class="header-buttons">
        <el-button :icon="RefreshRight" @click="loadProject">刷新</el-button>
        <el-button v-if="canEdit" type="primary" :icon="Edit" @click="openEditDialog">
          编辑项目
        </el-button>
      </div>
    </header>

    <div v-if="project" class="detail-grid">
      <article class="content-panel overview-panel">
        <header class="section-header">
          <h2>项目概况</h2>
          <span>版本 {{ project.version }}</span>
        </header>
        <dl class="detail-list">
          <div>
            <dt>项目类型</dt>
            <dd>{{ formatProjectType(project.project_type_code) }}</dd>
          </div>
          <div>
            <dt>负责人</dt>
            <dd>{{ project.owner_display_name }}</dd>
          </div>
          <div>
            <dt>计划开始</dt>
            <dd>{{ formatDate(project.planned_start_date) }}</dd>
          </div>
          <div>
            <dt>计划结束</dt>
            <dd>{{ formatDate(project.planned_end_date) }}</dd>
          </div>
          <div>
            <dt>创建时间</dt>
            <dd>{{ formatDateTime(project.created_at) }}</dd>
          </div>
          <div>
            <dt>最后更新</dt>
            <dd>{{ formatDateTime(project.updated_at) }}</dd>
          </div>
        </dl>
        <section class="description-block">
          <h3>项目说明</h3>
          <p>{{ project.description || "尚未填写项目说明。" }}</p>
        </section>
      </article>

      <aside class="detail-side">
        <article class="content-panel progress-panel">
          <header class="section-header"><h2>项目进展</h2></header>
          <div class="progress-empty">
            <span>0%</span>
            <el-progress :percentage="0" :show-text="false" />
            <p>创建实验计划后，将在这里汇总里程碑与完成度。</p>
          </div>
        </article>
        <article class="content-panel activity-panel">
          <header class="section-header"><h2>最近动态</h2></header>
          <div class="activity-item">
            <span class="activity-dot" />
            <div>
              <strong>项目资料已更新</strong>
              <p>{{ formatDateTime(project.updated_at) }}</p>
            </div>
          </div>
        </article>
      </aside>
    </div>

    <el-dialog v-model="editVisible" title="编辑项目" width="620px" align-center>
      <el-form ref="editFormRef" :model="editForm" :rules="rules" label-position="top">
        <div class="form-grid">
          <el-form-item label="项目名称" prop="name" class="wide">
            <el-input v-model="editForm.name" maxlength="200" show-word-limit />
          </el-form-item>
          <el-form-item label="项目类型" prop="project_type_code">
            <el-select v-model="editForm.project_type_code">
              <el-option label="研发项目" value="research" />
              <el-option label="验证项目" value="validation" />
              <el-option label="委托项目" value="commissioned" />
            </el-select>
          </el-form-item>
          <el-form-item label="负责人">
            <el-input :model-value="project?.owner_display_name" disabled />
          </el-form-item>
          <el-form-item label="计划开始日期">
            <el-date-picker
              v-model="editForm.planned_start_date"
              type="date"
              value-format="YYYY-MM-DD"
            />
          </el-form-item>
          <el-form-item label="计划结束日期">
            <el-date-picker
              v-model="editForm.planned_end_date"
              type="date"
              value-format="YYYY-MM-DD"
            />
          </el-form-item>
          <el-form-item label="项目说明" class="wide">
            <el-input v-model="editForm.description" type="textarea" :rows="4" maxlength="10000" />
          </el-form-item>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitEdit">保存修改</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.detail-header {
  align-items: center;
}

.detail-heading {
  display: flex;
  align-items: flex-start;
  gap: 10px;
}

.detail-heading > .el-button {
  margin: 3px 2px 0 -10px;
}

.heading-line {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 5px;
}

.project-number {
  color: var(--color-text-secondary);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}

.header-buttons {
  display: flex;
  gap: 8px;
}

.detail-grid {
  display: grid;
  flex: 1;
  grid-template-columns: minmax(0, 1fr) 330px;
  min-height: 0;
  gap: 14px;
}

.overview-panel {
  overflow-y: auto;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 58px;
  padding: 0 18px;
  border-bottom: 1px solid var(--color-border);
}

.section-header h2 {
  margin: 0;
  font-size: 15px;
}

.section-header > span {
  color: var(--color-text-tertiary);
  font-size: 11px;
}

.detail-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  margin: 0;
  padding: 9px 18px;
}

.detail-list div {
  padding: 14px 0;
  border-bottom: 1px solid var(--color-border);
}

.detail-list dt {
  margin-bottom: 5px;
  color: var(--color-text-tertiary);
  font-size: 11px;
}

.detail-list dd {
  margin: 0;
  font-weight: 550;
}

.description-block {
  padding: 20px 18px 30px;
}

.description-block h3 {
  margin: 0 0 9px;
  font-size: 13px;
}

.description-block p {
  margin: 0;
  color: var(--color-text-secondary);
  white-space: pre-wrap;
}

.detail-side {
  display: grid;
  grid-template-rows: minmax(210px, 0.8fr) minmax(180px, 1fr);
  min-height: 0;
  gap: 14px;
}

.progress-empty {
  padding: 24px 18px;
}

.progress-empty > span {
  display: block;
  margin-bottom: 8px;
  font-size: 24px;
  font-weight: 650;
}

.progress-empty p {
  margin: 14px 0 0;
  color: var(--color-text-secondary);
  font-size: 12px;
}

.activity-item {
  display: flex;
  gap: 10px;
  padding: 18px;
}

.activity-dot {
  flex: none;
  width: 8px;
  height: 8px;
  margin-top: 5px;
  background: var(--color-primary);
  border-radius: 50%;
}

.activity-item strong {
  font-size: 13px;
}

.activity-item p {
  margin: 4px 0 0;
  color: var(--color-text-tertiary);
  font-size: 11px;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 18px;
}

.form-grid .wide {
  grid-column: 1 / -1;
}

.form-grid :deep(.el-select),
.form-grid :deep(.el-date-editor) {
  width: 100%;
}
</style>
