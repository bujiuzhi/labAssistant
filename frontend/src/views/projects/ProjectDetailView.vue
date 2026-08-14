<script setup lang="ts">
import { Icon } from "@iconify/vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { computed, nextTick, onMounted, reactive, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";

import { getProblemDetail } from "@/api/http";
import { experimentApi } from "@/api/experiments";
import { projectApi } from "@/api/projects";
import { userApi } from "@/api/users";
import ProjectDataAssetsTab from "@/components/projects/ProjectDataAssetsTab.vue";
import ProjectDocumentsTab from "@/components/projects/ProjectDocumentsTab.vue";
import ProjectTasksTab from "@/components/projects/ProjectTasksTab.vue";
import { useSessionStore } from "@/stores/session";
import type {
  OrganizationUserOption,
  Experiment,
  Project,
  ProjectMilestone,
  ProjectOperationLog,
} from "@/types/api";

interface MilestoneStage {
  stage: string;
  name: string;
  date: string;
  state: "done" | "current" | "todo";
}

const route = useRoute();
const router = useRouter();
const sessionStore = useSessionStore();
const editVisible = ref(false);
const milestoneEditing = ref(false);
const loading = ref(false);
const submitting = ref(false);
const loadError = ref("");
const apiProject = ref<Project | null>(null);
const userOptions = ref<OrganizationUserOption[]>([]);
const recentExperiments = ref<Experiment[]>([]);
const recentExperimentsLoading = ref(false);
const operationLogs = ref<ProjectOperationLog[]>([]);
const operationLogsLoading = ref(false);
const milestoneEditor = ref<HTMLElement | null>(null);

const projectTypes = ["聚酰亚胺", "环氧树脂"];
const editableStatuses = ["draft", "not_started", "active", "at_risk", "suspended"];

const basicForm = reactive({
  name: "",
  projectTypeCode: "",
  ownerId: "",
  startDate: "",
  endDate: "",
  memberIds: [] as string[],
  objectives: "",
});
const milestoneForm = ref<ProjectMilestone[]>([]);

interface ExtendedProjectFields {
  current_stage: string;
  progress_percent: number;
  document_count: number;
  experiment_count: number;
  data_resource_count: number;
  objectives: string[];
}

interface ProjectView {
  id: string;
  name: string;
  startDate: string;
  endDate: string;
  type: string;
  owner: string;
  objective: string;
  status: string;
  stage: string;
  progress: number;
  documentCount: number;
  experimentCount: number;
  resourceCount: number;
  updatedAt: string;
}

/**
 * 将真实 API 项目转换为原型详情页所需的展示结构
 *
 * @param source API 返回的项目
 * @returns 原型详情页展示项目
 */
function adaptApiProject(source: Project): ProjectView {
  const extended = source as Project & Partial<ExtendedProjectFields>;
  const isArchived = ["completed", "archived"].includes(source.status);
  const isAtRisk = ["at_risk", "suspended"].includes(source.status);
  const isActive = source.status === "active";

  return {
    id: source.project_no,
    name: source.name,
    startDate: formatProjectDateTime(source.planned_start_date),
    endDate: formatProjectDateTime(source.planned_end_date),
    type: source.project_type_code,
    owner: source.owner_display_name,
    objective: extended.objectives?.[0] ?? (source.description || "暂无项目目标"),
    status: isArchived ? "已归档" : isAtRisk ? "有风险" : isActive ? "进行中" : "待开始",
    stage:
      extended.current_stage ??
      (isArchived ? "项目归档" : isActive ? "实验执行" : isAtRisk ? "风险处置" : "方案设计"),
    progress:
      extended.progress_percent ?? (isArchived ? 100 : isActive ? 55 : isAtRisk ? 40 : 0),
    documentCount: extended.document_count ?? 0,
    experimentCount: extended.experiment_count ?? 0,
    resourceCount: extended.data_resource_count ?? 0,
    updatedAt: source.updated_at,
  };
}

const project = computed<ProjectView | null>(() =>
  apiProject.value ? adaptApiProject(apiProject.value) : null,
);

const projectTabs = ["概览", "文档资料", "数据资产", "任务管理"];
const activeProjectTab = ref("概览");
const canEdit = computed(
  () =>
    sessionStore.hasPermission("project.update") &&
    apiProject.value !== null &&
    editableStatuses.includes(apiProject.value.status),
);
const canManageMembers = computed(() =>
  sessionStore.hasPermission("project.manage_members"),
);
const projectIsWritable = computed(
  () =>
    apiProject.value !== null &&
    !["completed", "archived"].includes(apiProject.value.status),
);
const canUploadDocuments = computed(
  () =>
    projectIsWritable.value &&
    sessionStore.hasPermission("document.upload"),
);
const projectTypeOptions = computed(() => projectTypes);
const memberOptions = computed(() =>
  userOptions.value.filter((user) => user.id !== basicForm.ownerId),
);

const milestones = computed<MilestoneStage[]>(() => {
  if (apiProject.value?.milestones.length) {
    return apiProject.value.milestones.map((item, index) => ({
      stage: `阶段 ${String(index + 1).padStart(2, "0")}`,
      name: item.name,
      date: item.date,
      state: item.state,
    }));
  }
  return [];
});

const memberNames = computed(() => {
  if (apiProject.value?.members.length) {
    return apiProject.value.members.map((member) => member.display_name);
  }
  return [];
});

const objectiveItems = computed(() => {
  if (apiProject.value?.objectives.length) {
    return apiProject.value.objectives;
  }
  return [];
});

const statusLabel = computed(() => {
  if (!project.value) return "";
  if (project.value.status === "有风险") return "有风险";
  if (project.value.status === "已归档") return "已归档";
  if (project.value.status === "待开始") return "待开始";
  return "进行中";
});

const projectDeadline = computed(() => {
  if (!apiProject.value || ["completed", "archived"].includes(apiProject.value.status)) {
    return "";
  }
  const pendingMilestones = apiProject.value.milestones
    .filter((item) => item.state !== "done" && item.date)
    .sort((left, right) => left.date.localeCompare(right.date));
  const target = pendingMilestones[0]?.date ?? apiProject.value.planned_end_date;
  if (!target) return "";
  const targetDate = new Date(`${target.slice(0, 10)}T00:00:00`);
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const days = Math.round((targetDate.getTime() - today.getTime()) / 86_400_000);
  if (days < 0) return `里程碑已逾期 ${Math.abs(days)} 天`;
  if (days === 0) return "里程碑今日到期";
  return `距离下一里程碑 ${days} 天`;
});

/** 格式化项目计划日期时间。 */
function formatProjectDateTime(value: string | null): string {
  if (!value) return "未设置";
  return new Intl.DateTimeFormat("zh-CN", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  })
    .format(new Date(value))
    .replaceAll("/", "-");
}

function toInputDateTime(value: string | null): string {
  if (!value) return "";
  const date = new Date(value);
  const local = new Date(date.getTime() - date.getTimezoneOffset() * 60_000);
  return local.toISOString().slice(0, 16);
}

/** 格式化实验开始时间，未设置时不伪造日期。 */
function formatExperimentStart(value: string | null): string {
  return value ? value.slice(0, 10) : "未设置";
}

/** 将实验状态转换为页面展示文本。 */
function experimentStatusLabel(status: Experiment["status"]): string {
  const labels: Record<Experiment["status"], string> = {
    in_progress: "进行中",
    not_started: "未开始",
    completed: "已完成",
  };
  return labels[status];
}

/** 返回项目操作类型的用户可读名称。 */
function operationLogActionLabel(actionType: string): string {
  const labels: Record<string, string> = {
    project_created: "创建项目",
    project_updated: "编辑项目",
    milestones_updated: "修改项目里程碑",
    project_archived: "归档项目",
    document_uploaded: "上传文档",
  };
  return labels[actionType] ?? "项目操作";
}

/** 返回项目操作类型对应的图标。 */
function operationLogIcon(actionType: string): string {
  const icons: Record<string, string> = {
    project_created: "tabler:plus",
    project_updated: "tabler:edit",
    milestones_updated: "tabler:flag",
    project_archived: "tabler:archive",
    document_uploaded: "tabler:upload",
  };
  return icons[actionType] ?? "tabler:history";
}

/** 加载项目最近更新的三条真实实验记录。 */
async function loadRecentExperiments(projectId: string): Promise<void> {
  recentExperimentsLoading.value = true;
  try {
    const response = await experimentApi.list({ project_id: projectId, page_size: 3 });
    recentExperiments.value = response.data
      .sort((left, right) => right.updated_at.localeCompare(left.updated_at))
      .slice(0, 3);
  } catch (error) {
    recentExperiments.value = [];
    const problem = getProblemDetail(error);
    ElMessage.error(problem?.detail ?? "最近实验加载失败");
  } finally {
    recentExperimentsLoading.value = false;
  }
}

/** 加载项目创建、编辑、里程碑、文档与归档的真实操作记录。 */
async function loadOperationLogs(projectId: string): Promise<void> {
  operationLogsLoading.value = true;
  try {
    operationLogs.value = await projectApi.listOperationLogs(projectId);
  } catch (error) {
    operationLogs.value = [];
    const problem = getProblemDetail(error);
    ElMessage.error(problem?.detail ?? "项目操作日志加载失败");
  } finally {
    operationLogsLoading.value = false;
  }
}

/**
 * 根据路由参数加载项目详情
 *
 * 项目 UUID 和业务编号均从真实项目 API 读取，接口失败时不展示原型兜底数据。
 */
async function loadProject(): Promise<void> {
  loading.value = true;
  loadError.value = "";
  apiProject.value = null;
  try {
    apiProject.value = await projectApi.get(String(route.params.projectId));
    await Promise.all([
      loadRecentExperiments(apiProject.value.id),
      loadOperationLogs(apiProject.value.id),
    ]);
  } catch (error) {
    const problem = getProblemDetail(error);
    loadError.value = problem?.detail ?? "项目详情加载失败";
    ElMessage.error(loadError.value);
  } finally {
    loading.value = false;
  }
}

/**
 * 加载当前组织可选的负责人和项目成员
 */
async function loadUserOptions(): Promise<void> {
  try {
    userOptions.value = await userApi.listOptions();
  } catch (error) {
    const problem = getProblemDetail(error);
    ElMessage.error(problem?.detail ?? "组织人员加载失败");
  }
}

/**
 * 在概览页内打开项目基础信息编辑模式
 */
function openBasicEditor(): void {
  if (!canEdit.value || !apiProject.value) {
    ElMessage.warning("当前账号或项目状态不允许编辑");
    return;
  }
  basicForm.name = apiProject.value.name;
  basicForm.projectTypeCode = apiProject.value.project_type_code;
  basicForm.ownerId = apiProject.value.owner_id;
  basicForm.startDate = toInputDateTime(apiProject.value.planned_start_date);
  basicForm.endDate = toInputDateTime(apiProject.value.planned_end_date);
  basicForm.memberIds = apiProject.value.members
    .filter((member) => member.user_id !== apiProject.value?.owner_id)
    .map((member) => member.user_id);
  basicForm.objectives = (
    apiProject.value.objectives.length
      ? apiProject.value.objectives
      : [apiProject.value.description]
  )
    .filter(Boolean)
    .join("\n");
  editVisible.value = true;
}

/** 取消基础信息编辑并放弃未保存内容。 */
function cancelBasicEdit(): void {
  editVisible.value = false;
}

/**
 * 保存项目基础信息
 */
async function submitBasicEdit(): Promise<void> {
  if (!apiProject.value || submitting.value) {
    return;
  }
  const objectives = basicForm.objectives
    .split("\n")
    .map((item) => item.trim())
    .filter(Boolean);
  if (
    !basicForm.name.trim() ||
    !basicForm.projectTypeCode ||
    !basicForm.ownerId ||
    !basicForm.startDate ||
    !basicForm.endDate ||
    !objectives.length
  ) {
    ElMessage.warning("请填写全部必填项");
    return;
  }
  if (basicForm.endDate < basicForm.startDate) {
    ElMessage.warning("结束时间不能早于开始时间");
    return;
  }

  submitting.value = true;
  try {
    apiProject.value = await projectApi.update(
      apiProject.value.id,
      apiProject.value.version,
      {
        name: basicForm.name.trim(),
        project_type_code: basicForm.projectTypeCode,
        owner_id: basicForm.ownerId,
        planned_start_date: basicForm.startDate,
        planned_end_date: basicForm.endDate,
        description: objectives.join("；"),
        objectives,
        ...(canManageMembers.value ? { member_ids: basicForm.memberIds } : {}),
      },
    );
    await loadOperationLogs(apiProject.value.id);
    editVisible.value = false;
    ElMessage.success("项目基础信息已更新");
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

/**
 * 在项目里程碑卡片内打开编辑模式
 */
function openMilestoneEditor(): void {
  if (!canEdit.value || !apiProject.value) {
    ElMessage.warning("当前账号或项目状态不允许编辑");
    return;
  }
  milestoneForm.value = apiProject.value.milestones.length
    ? apiProject.value.milestones.map((item) => ({ ...item }))
    : [
        {
          date: apiProject.value.planned_end_date ?? "",
          name: "完成当前阶段评审",
          state: "current",
        },
      ];
  milestoneEditing.value = true;
}

/**
 * 取消项目里程碑编辑并放弃未保存内容
 */
function cancelMilestoneEdit(): void {
  milestoneEditing.value = false;
}

/**
 * 新增一个空里程碑编辑行
 */
function addMilestone(): void {
  milestoneForm.value.push({ date: "", name: "", state: "todo" });
  void nextTick(() => {
    const rows = milestoneEditor.value?.querySelectorAll<HTMLElement>(
      ".milestone-editor-row",
    );
    rows?.[rows.length - 1]?.querySelector<HTMLInputElement>("input")?.focus();
  });
}

/**
 * 删除指定里程碑编辑行
 *
 * @param index 里程碑行索引
 */
function removeMilestone(index: number): void {
  if (milestoneForm.value.length === 1) {
    ElMessage.warning("至少保留一个里程碑");
    return;
  }
  milestoneForm.value.splice(index, 1);
}

/**
 * 保存项目里程碑
 */
async function submitMilestones(): Promise<void> {
  if (!apiProject.value || submitting.value) {
    return;
  }
  if (milestoneForm.value.some((item) => !item.name.trim() || !item.date)) {
    ElMessage.warning("请完整填写每个里程碑的目标和计划时间");
    return;
  }
  if (milestoneForm.value.filter((item) => item.state === "current").length > 1) {
    ElMessage.warning("只能设置一个当前阶段里程碑");
    return;
  }

  const milestonesToSave = milestoneForm.value
    .map((item) => ({ ...item, name: item.name.trim() }))
    .sort((left, right) => left.date.localeCompare(right.date));
  submitting.value = true;
  try {
    apiProject.value = await projectApi.update(
      apiProject.value.id,
      apiProject.value.version,
      { milestones: milestonesToSave },
    );
    await loadOperationLogs(apiProject.value.id);
    milestoneEditing.value = false;
    ElMessage.success("项目里程碑已更新");
  } catch (error) {
    const problem = getProblemDetail(error);
    if (problem?.status === 412) {
      ElMessage.warning("项目已被其他人修改，正在刷新最新数据");
      await loadProject();
    } else {
      ElMessage.error(problem?.detail ?? "里程碑更新失败");
    }
  } finally {
    submitting.value = false;
  }
}

/** 二次确认后归档项目，归档操作会写入项目审计记录。 */
async function archiveCurrentProject(): Promise<void> {
  if (!apiProject.value || !canEdit.value || submitting.value) return;
  try {
    await ElMessageBox.confirm(
      "归档后项目基础信息、里程碑和文档将转为只读，是否继续？",
      "归档项目",
      {
        confirmButtonText: "确认归档",
        cancelButtonText: "取消",
        confirmButtonClass: "el-button--danger",
        type: "warning",
      },
    );
  } catch {
    return;
  }
  submitting.value = true;
  try {
    apiProject.value = await projectApi.archive(
      apiProject.value.id,
      apiProject.value.version,
    );
    await loadOperationLogs(apiProject.value.id);
    ElMessage.success("项目已归档");
  } catch (error) {
    const problem = getProblemDetail(error);
    ElMessage.error(problem?.detail ?? "项目归档失败");
  } finally {
    submitting.value = false;
  }
}

onMounted(async () => {
  await Promise.all([loadProject(), loadUserOptions()]);
});
watch(() => route.params.projectId, loadProject);
</script>

<template>
  <section v-loading="loading" class="project-overview-page">
    <template v-if="project">
      <header class="project-hero">
        <div>
          <div class="project-title-line"><h1>{{ project.name }}</h1></div>
          <p>
            <span>{{ project.id }}</span>
            <span class="status-chip" :class="project.status">{{ statusLabel }}</span>
          </p>
        </div>
        <div class="project-hero-actions">
          <strong v-if="projectDeadline" class="deadline-hint">{{ projectDeadline }}</strong>
          <button
            v-if="canEdit"
            class="ui-button ui-button--danger archive-button"
            type="button"
            :disabled="submitting"
            @click="archiveCurrentProject"
          >
            <Icon icon="tabler:archive" />归档
          </button>
        </div>
      </header>

      <nav class="project-tabs" aria-label="项目模块">
        <button
          v-for="tab in projectTabs"
          :key="tab"
          type="button"
          :class="{ active: activeProjectTab === tab }"
          @click="activeProjectTab = tab"
        >
          {{ tab }}
        </button>
      </nav>

      <div v-if="activeProjectTab === '概览'" class="overview-content">
        <section class="detail-card basic-card">
          <header>
            <div>
              <h2>{{ editVisible ? "编辑基础信息" : "基础信息" }}</h2>
              <p v-if="editVisible" class="editor-description">
                修改后将同步更新项目列表与详情页
              </p>
            </div>
            <button
              v-if="canEdit && !editVisible"
              class="ui-button ui-button--secondary edit-button"
              type="button"
              @click="openBasicEditor"
            >
              <Icon icon="tabler:edit" />编辑
            </button>
            <div v-else-if="editVisible" class="basic-editor-actions">
              <button
                class="ui-button ui-button--secondary"
                type="button"
                :disabled="submitting"
                @click="cancelBasicEdit"
              >取消</button>
              <button
                class="ui-button ui-button--primary"
                type="button"
                :disabled="submitting"
                @click="submitBasicEdit"
              >
                <Icon icon="tabler:check" />{{ submitting ? "保存中" : "保存" }}
              </button>
            </div>
            <span
              v-else-if="apiProject && sessionStore.hasPermission('project.update')"
              class="readonly-hint"
            >
              当前状态只读
            </span>
          </header>
          <el-form v-if="editVisible" class="basic-inline-editor" label-position="top">
            <el-form-item label="项目名称" required class="full-row">
              <el-input v-model="basicForm.name" maxlength="200" />
            </el-form-item>
            <el-form-item label="项目类型" required>
              <el-select v-model="basicForm.projectTypeCode" style="width: 100%">
                <el-option v-for="item in projectTypeOptions" :key="item" :label="item" :value="item" />
              </el-select>
            </el-form-item>
            <el-form-item label="项目经理" required>
              <el-select v-model="basicForm.ownerId" filterable style="width: 100%" placeholder="选择项目经理">
                <el-option v-for="user in userOptions" :key="user.id" :label="user.display_name" :value="user.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="开始时间" required>
              <el-date-picker v-model="basicForm.startDate" type="datetime" value-format="YYYY-MM-DDTHH:mm" style="width: 100%" />
            </el-form-item>
            <el-form-item label="结束时间" required>
              <el-date-picker v-model="basicForm.endDate" type="datetime" value-format="YYYY-MM-DDTHH:mm" style="width: 100%" />
            </el-form-item>
            <el-form-item v-if="canManageMembers" label="人员组成" class="full-row">
              <el-select v-model="basicForm.memberIds" multiple filterable style="width: 100%" placeholder="选择项目成员">
                <el-option v-for="user in memberOptions" :key="user.id" :label="user.display_name" :value="user.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="指标性研发目标" required class="full-row">
              <el-input v-model="basicForm.objectives" type="textarea" :rows="4" maxlength="5000" placeholder="每行填写一项目标" />
            </el-form-item>
          </el-form>
          <div v-else class="basic-grid">
            <dl>
              <div><dt>项目类型</dt><dd>{{ project.type }}</dd></div>
              <div>
                <dt>项目周期</dt>
                <dd>{{ project.startDate }} 至 {{ project.endDate }}</dd>
              </div>
              <div><dt>项目经理</dt><dd>{{ project.owner }}</dd></div>
              <div>
                <dt>人员组成</dt>
                <dd class="member-list">
                  <span v-for="member in memberNames" :key="member">
                    <i>{{ member.slice(0, 1) }}</i>{{ member }}
                  </span>
                </dd>
              </div>
            </dl>
            <section class="objective">
              <span>研发总体目标</span>
              <ul>
                <li v-for="item in objectiveItems" :key="item">{{ item }}</li>
              </ul>
            </section>
          </div>
        </section>

        <section
          class="detail-card milestone-card"
          data-testid="project-milestone-card"
        >
          <header class="milestone-card-head">
            <div>
              <h2>{{ milestoneEditing ? "编辑项目里程碑" : "项目里程碑" }}</h2>
              <p v-if="milestoneEditing">
                可新增、修改或删除阶段节点，保存后同步到项目总览。
              </p>
            </div>
            <button
              v-if="canEdit && !milestoneEditing"
              class="ui-button ui-button--secondary edit-button"
              type="button"
              data-testid="milestone-edit-button"
              @click="openMilestoneEditor"
            >
              <Icon icon="tabler:edit" />编辑
            </button>
            <div v-else-if="milestoneEditing" class="milestone-card-actions">
              <button
                class="ui-button ui-button--secondary milestone-action-button"
                type="button"
                @click="cancelMilestoneEdit"
              >
                取消
              </button>
              <button
                class="ui-button ui-button--primary milestone-action-button primary"
                type="button"
                :disabled="submitting"
                @click="submitMilestones"
              >
                <Icon icon="tabler:check" />
                {{ submitting ? "保存中" : "保存" }}
              </button>
            </div>
          </header>
          <div v-if="!milestoneEditing" class="milestone-scroll">
            <div class="milestone-grid" :style="{ '--stage-count': milestones.length }">
              <article v-for="item in milestones" :key="item.stage" :class="item.state">
                <span>{{ item.stage }}</span>
                <strong>{{ item.name }}</strong>
              </article>
              <div
                v-for="item in milestones"
                :key="`${item.stage}-bar`"
                class="stage-progress"
                :class="item.state"
              >
                <i />
                <b />
              </div>
              <time v-for="item in milestones" :key="`${item.stage}-date`">
                {{ item.date }}
              </time>
            </div>
          </div>
          <div
            v-else
            ref="milestoneEditor"
            class="milestone-editor"
            data-testid="milestone-inline-editor"
          >
            <div class="milestone-editor-head">
              <span>里程碑目标描述</span>
              <span>计划时间</span>
              <span>状态</span>
              <span />
            </div>
            <div class="milestone-editor-rows">
              <div
                v-for="(item, index) in milestoneForm"
                :key="index"
                class="milestone-editor-row"
              >
                <input
                  v-model="item.name"
                  type="text"
                  maxlength="500"
                  placeholder="输入阶段目标"
                  aria-label="里程碑目标描述"
                />
                <input
                  v-model="item.date"
                  type="date"
                  aria-label="计划时间"
                />
                <select v-model="item.state" aria-label="里程碑状态">
                  <option value="todo">未开始</option>
                  <option value="current">当前阶段</option>
                  <option value="done">已完成</option>
                </select>
                <button
                  class="ui-button ui-button--danger ui-button--icon milestone-remove"
                  type="button"
                  aria-label="删除里程碑"
                  @click="removeMilestone(index)"
                >
                  <Icon icon="tabler:trash" />
                </button>
              </div>
            </div>
            <button
              class="ui-button ui-button--light milestone-add"
              type="button"
              @click="addMilestone"
            >
              <Icon icon="tabler:plus" />新增里程碑
            </button>
          </div>
        </section>

        <section class="overview-bottom-grid" aria-label="项目实验与操作日志">
          <article class="detail-card recent-experiments-card">
            <header><h2>最近实验</h2></header>
            <div v-loading="recentExperimentsLoading" class="recent-experiment-list">
              <button
                v-for="experiment in recentExperiments"
                :key="experiment.id"
                type="button"
                class="recent-experiment-row"
                @click="router.push(`/eln?experiment=${experiment.id}`)"
              >
                <strong>{{ experiment.name }}</strong>
                <span>开始时间：{{ formatExperimentStart(experiment.estimated_start) }}</span>
                <small>{{ experimentStatusLabel(experiment.status) }} · {{ experiment.owner_display_name }}</small>
              </button>
              <el-empty v-if="!recentExperimentsLoading && !recentExperiments.length" description="暂无项目实验" :image-size="54" />
            </div>
          </article>

          <article class="detail-card operation-log-card">
            <header><h2>项目操作日志</h2></header>
            <div v-loading="operationLogsLoading" class="operation-log-list">
              <div v-for="log in operationLogs" :key="log.id" class="operation-log-row">
                <i><Icon :icon="operationLogIcon(log.action_type)" /></i>
                <strong>
                  {{ log.actor_display_name }} ·
                  {{ operationLogActionLabel(log.action_type) }}
                </strong>
                <small>{{ log.description }}</small>
                <time>{{ formatProjectDateTime(log.created_at) }}</time>
              </div>
              <el-empty
                v-if="!operationLogsLoading && !operationLogs.length"
                description="暂无项目操作日志"
                :image-size="54"
              />
            </div>
          </article>
        </section>
      </div>

      <ProjectDocumentsTab
        v-else-if="activeProjectTab === '文档资料'"
        :project-id="apiProject?.id ?? project.id"
        :can-upload="canUploadDocuments"
        @changed="loadProject"
      />

      <ProjectDataAssetsTab v-else-if="activeProjectTab === '数据资产'" />

      <ProjectTasksTab v-else-if="activeProjectTab === '任务管理'" />

    </template>

    <el-empty v-else-if="!loading" class="load-error" :description="loadError || '未找到项目'">
      <el-button type="primary" @click="router.push('/projects')">返回项目数据</el-button>
    </el-empty>
  </section>
</template>

<style scoped>
.project-overview-page {
  height: 100%;
  min-width: 0;
  padding: var(--space-card) 0 24px;
  overflow-x: hidden;
  overflow-y: auto;
}

.load-error {
  min-height: 420px;
}

.project-hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 82px;
  padding: 12px 14px;
  background: var(--color-paper);
  border: 1px solid var(--color-rule);
  border-radius: 8px;
  box-shadow: var(--shadow-whisper);
  gap: 18px;
}

.project-title-line {
  display: flex;
  align-items: center;
  gap: 12px;
}

.project-title-line h1 {
  margin: 0;
  color: var(--color-ink);
  font-size: 22px;
  line-height: 1.3;
}

.project-hero p {
  display: flex;
  align-items: center;
  margin: 7px 0 0;
  color: var(--color-muted);
  font-size: 12px;
  gap: 10px;
  flex-wrap: wrap;
}

.project-hero-actions {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 8px;
}

.project-hero-actions button {
  display: inline-flex;
  height: 34px;
  align-items: center;
  padding: 0 10px;
  color: var(--color-ink-2);
  background: var(--color-paper);
  border: 1px solid var(--color-rule-2);
  border-radius: 5px;
  cursor: pointer;
  gap: 5px;
}

.project-hero-actions button:hover {
  color: var(--color-accent);
  border-color: var(--color-accent);
}

.project-hero-actions .archive-button:hover {
  color: var(--color-danger);
  border-color: var(--color-danger);
}

.deadline-hint {
  padding: 5px 8px;
  color: #a95200;
  font-size: 12px;
  background: var(--color-warning-soft);
  border-radius: 5px;
}

.status-chip {
  padding: 3px 8px;
  color: #1769aa;
  background: #e8f3ff;
  border-radius: 5px;
}

.status-chip.有风险 {
  color: #c2413a;
  background: #fff0ef;
}

.status-chip.待开始 {
  color: #a95200;
  background: var(--color-warning-soft);
}

.status-chip.已归档 {
  color: var(--color-muted);
  background: var(--color-paper-3);
}

.project-tabs {
  display: flex;
  align-items: center;
  height: 46px;
  padding: 0 14px;
  margin-top: 6px;
  background: var(--color-paper);
  border: 1px solid var(--color-rule);
  border-radius: 8px;
  gap: 36px;
}

.project-tabs button {
  position: relative;
  align-self: stretch;
  padding: 0;
  color: var(--color-ink-2);
  background: transparent;
  border: 0;
  cursor: pointer;
}

.project-tabs button.active {
  color: var(--color-ink);
  font-weight: 650;
}

.project-tabs button.active::after {
  position: absolute;
  right: 0;
  bottom: 0;
  left: 0;
  height: 2px;
  background: var(--color-accent);
  content: "";
}

.overview-content {
  display: grid;
  padding-top: var(--space-card);
  gap: var(--space-card);
}

.detail-card {
  background: var(--color-paper);
  border: 1px solid var(--color-rule);
  border-radius: 8px;
  box-shadow: var(--shadow-whisper);
}

.detail-card > header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 48px;
  padding: 0 16px;
}

.detail-card h2 {
  margin: 0;
  font-size: 15px;
}

.edit-button {
  display: inline-flex;
  align-items: center;
  height: 34px;
  padding: 0 11px;
  color: var(--color-ink-2);
  background: var(--color-paper);
  border: 1px solid var(--color-rule-2);
  border-radius: 5px;
  cursor: pointer;
  gap: 5px;
}

.edit-button svg {
  width: 15px;
}

.readonly-hint {
  color: var(--color-muted);
  font-size: 12px;
}

.basic-card {
  padding-bottom: 16px;
}

.editor-description {
  margin: 3px 0 0;
  color: var(--color-muted);
  font-size: 12px;
}

.basic-editor-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.basic-editor-actions button {
  display: inline-flex;
  height: 34px;
  align-items: center;
  padding: 0 14px;
  border-radius: 5px;
  cursor: pointer;
  gap: 5px;
}

.basic-inline-editor {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  padding: 4px 16px 0;
  gap: 0 12px;
}

.basic-inline-editor .full-row {
  grid-column: 1 / -1;
}

.basic-inline-editor :deep(.el-form-item) {
  margin-bottom: 12px;
}

.basic-inline-editor :deep(.el-form-item__label) {
  padding-bottom: 5px;
  color: var(--color-ink-2);
  font-size: 12px;
  font-weight: 600;
  line-height: 1.3;
}

.basic-inline-editor :deep(.el-input__wrapper),
.basic-inline-editor :deep(.el-select__wrapper),
.basic-inline-editor :deep(.el-textarea__inner) {
  background: #f8fbff;
  box-shadow: 0 0 0 1px #b8d5f5 inset;
}

.basic-grid {
  padding: 0 28px;
}

.basic-grid dl {
  display: grid;
  grid-template-columns: 1fr 1.35fr 1fr 1.35fr;
  margin: 0;
  gap: 22px;
}

.basic-grid dl > div {
  min-width: 0;
}

.basic-grid dt,
.objective > span {
  margin-bottom: 6px;
  color: var(--color-muted);
  font-size: 12px;
}

.basic-grid dd {
  margin: 0;
  color: var(--color-ink);
  font-weight: 550;
}

.member-list {
  display: flex;
  align-items: center;
  gap: 7px;
}

.member-list span {
  display: inline-flex;
  align-items: center;
  padding: 3px 7px;
  color: var(--color-ink-2);
  font-size: 12px;
  background: var(--color-paper-3);
  border-radius: 999px;
  gap: 4px;
}

.member-list i {
  display: grid;
  width: 18px;
  height: 18px;
  color: var(--color-accent);
  font-size: 10px;
  font-style: normal;
  background: var(--color-accent-soft);
  border-radius: 999px;
  place-items: center;
}

.objective {
  margin-top: 16px;
}

.objective > span {
  color: var(--color-ink-2);
  font-size: 13px;
  font-weight: 600;
}

.objective ul {
  padding-left: 18px;
  margin: 0;
  color: var(--color-ink);
  font-size: 15px;
  font-weight: 550;
  line-height: 1.75;
}

.milestone-card {
  overflow: hidden;
  padding-bottom: 16px;
}

.detail-card > .milestone-card-head {
  min-height: 68px;
  padding: 14px 16px;
  border-bottom: 1px solid var(--color-rule);
}

.milestone-card-head p {
  margin: 3px 0 0;
  color: var(--color-muted);
  font-size: 13px;
}

.milestone-card-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.milestone-action-button {
  display: inline-flex;
  height: 36px;
  align-items: center;
  justify-content: center;
  padding: 0 14px;
  color: var(--color-ink-2);
  background: var(--color-paper);
  border: 1px solid var(--color-rule-2);
  border-radius: 5px;
  cursor: pointer;
  gap: 5px;
}

.milestone-action-button.primary {
  color: #ffffff;
  background: var(--color-accent);
  border-color: var(--color-accent);
}

.milestone-action-button:disabled {
  cursor: wait;
  opacity: 0.65;
}

.milestone-action-button svg {
  width: 15px;
  height: 15px;
}

.milestone-scroll {
  padding: 22px 28px 0;
  overflow-x: auto;
}

.milestone-grid {
  display: grid;
  min-width: max(100%, calc(var(--stage-count) * 225px));
  grid-template-columns: repeat(var(--stage-count), minmax(200px, 1fr));
  grid-template-rows: 90px 34px 24px;
  gap: 8px 14px;
}

.milestone-grid article {
  padding: 10px 12px;
  background: var(--color-paper);
  border: 1px solid var(--color-rule);
  border-radius: 6px;
  box-shadow: var(--shadow-whisper);
}

.milestone-grid article.current {
  border-color: #8aa9dc;
}

.milestone-grid article span {
  display: block;
  color: var(--color-muted);
  font-size: 11px;
}

.milestone-grid article strong {
  display: -webkit-box;
  margin-top: 6px;
  overflow: hidden;
  color: var(--color-ink);
  font-size: 13px;
  line-height: 1.45;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.stage-progress {
  position: relative;
  display: flex;
  align-items: center;
}

.stage-progress b {
  position: absolute;
  right: -14px;
  left: 6px;
  height: 18px;
  background: #cfd5dd;
  clip-path: polygon(0 0, calc(100% - 12px) 0, 100% 50%, calc(100% - 12px) 100%, 0 100%);
}

.stage-progress.done b {
  background: #63a15b;
}

.stage-progress.current b {
  background: #507ee8;
}

.stage-progress i {
  z-index: 1;
  width: 6px;
  height: 6px;
  margin-left: 8px;
  background: #ffffff;
  border-radius: 999px;
}

.milestone-grid time {
  color: var(--color-ink);
  font-size: 14px;
  font-weight: 600;
  line-height: 24px;
  text-align: center;
}

.progress-card {
  padding-bottom: 16px;
}

.progress-summary {
  display: grid;
  align-items: center;
  grid-template-columns: 58px minmax(160px, 1fr) auto auto;
  padding: 0 28px;
  gap: 14px;
}

.progress-summary > strong {
  font-size: 22px;
}

.progress-summary > i {
  height: 8px;
  overflow: hidden;
  background: var(--color-paper-3);
  border-radius: 999px;
}

.progress-summary > i b {
  display: block;
  height: 100%;
  background: var(--color-accent);
}

.progress-summary > span {
  color: var(--color-muted);
}

.progress-summary dl {
  display: flex;
  margin: 0;
  gap: 18px;
}

.progress-summary dl div {
  display: flex;
  align-items: baseline;
  gap: 4px;
}

.progress-summary dt {
  color: var(--color-muted);
}

.progress-summary dd {
  margin: 0;
  font-weight: 650;
}

.overview-bottom-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.65fr) minmax(300px, 1fr);
  gap: 12px;
}

.recent-experiments-card,
.operation-log-card {
  min-height: 228px;
}

.recent-experiments-card > header,
.operation-log-card > header {
  padding: 17px 16px 8px;
}

.recent-experiments-card h2,
.operation-log-card h2 {
  margin: 0;
  font-size: 17px;
}

.recent-experiment-list,
.operation-log-list {
  min-height: 162px;
  padding: 4px 16px 16px;
}

.recent-experiment-row {
  display: grid;
  width: 100%;
  grid-template-columns: minmax(200px, 1fr) auto auto;
  align-items: center;
  min-height: 48px;
  padding: 9px 0;
  color: var(--color-ink);
  text-align: left;
  background: transparent;
  border: 0;
  border-bottom: 1px solid var(--color-rule-2);
  cursor: pointer;
  gap: 16px;
}

.recent-experiment-row:last-child { border-bottom: 0; }
.recent-experiment-row:hover strong { color: var(--color-accent); }
.recent-experiment-row strong { font-size: 14px; font-weight: 650; }
.recent-experiment-row span,
.recent-experiment-row small { color: var(--color-muted); font-size: 12px; white-space: nowrap; }

.operation-log-list {
  max-height: 196px;
  overflow-y: auto;
}

.operation-log-row {
  display: grid;
  min-height: 52px;
  padding: 7px 0;
  grid-template-columns: 28px minmax(0, 1fr) auto;
  align-items: center;
  color: var(--color-ink-2);
  border-bottom: 1px solid var(--color-rule-2);
  column-gap: 9px;
}

.operation-log-row:last-child {
  border-bottom: 0;
}

.operation-log-row i {
  display: grid;
  width: 24px;
  height: 24px;
  grid-row: 1 / 3;
  color: var(--color-accent);
  font-style: normal;
  background: var(--color-accent-soft);
  border-radius: 6px;
  place-items: center;
}

.operation-log-row i svg {
  width: 14px;
  height: 14px;
}

.operation-log-row strong {
  font-size: 13px;
  font-weight: 600;
}

.operation-log-row small {
  grid-column: 2;
  overflow: hidden;
  color: var(--color-muted);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.operation-log-row time {
  grid-column: 3;
  grid-row: 1 / 3;
  color: var(--color-muted);
  font-size: 12px;
  white-space: nowrap;
}

.milestone-editor {
  display: grid;
  padding: 16px;
  gap: 10px;
}

.milestone-editor-head,
.milestone-editor-row {
  display: grid;
  align-items: center;
  grid-template-columns: minmax(220px, 1.6fr) 170px 140px 34px;
  gap: 10px;
}

.milestone-editor-head {
  padding: 0 2px;
  color: var(--color-muted);
  font-size: 12px;
}

.milestone-editor-rows {
  display: grid;
  gap: 10px;
}

.milestone-editor-row > input,
.milestone-editor-row > select {
  width: 100%;
  height: 36px;
  padding: 0 11px;
  color: var(--color-ink);
  font: inherit;
  background: var(--color-paper);
  border: 1px solid var(--color-rule-2);
  border-radius: 5px;
  outline: none;
}

.milestone-editor-row > input:focus,
.milestone-editor-row > select:focus {
  border-color: var(--color-accent);
  box-shadow: 0 0 0 2px var(--color-accent-soft);
}

.milestone-editor-row > input::placeholder {
  color: var(--color-faint);
}

.milestone-remove,
.milestone-add {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: var(--color-ink-2);
  background: var(--color-paper);
  border: 1px solid var(--color-rule-2);
  border-radius: 5px;
  cursor: pointer;
}

.milestone-remove {
  width: 34px;
  height: 34px;
}

.milestone-remove:hover {
  color: var(--color-danger);
  border-color: var(--color-danger);
}

.milestone-add {
  width: fit-content;
  height: 34px;
  padding: 0 12px;
  margin-top: 2px;
  color: var(--color-accent);
  gap: 5px;
}

.milestone-remove svg,
.milestone-add svg {
  width: 16px;
  height: 16px;
}

@media (max-width: 1180px) {
  .project-hero {
    align-items: flex-start;
    flex-direction: column;
  }

  .project-hero-actions {
    justify-content: flex-start;
  }

  .basic-grid dl {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .progress-summary {
    grid-template-columns: 58px minmax(160px, 1fr);
  }

  .overview-bottom-grid { grid-template-columns: 1fr; }
}

@media (max-width: 720px) {
  .project-tabs {
    padding: 0 10px;
    overflow-x: auto;
    gap: 24px;
  }

  .project-tabs button {
    flex: 0 0 auto;
  }

  .basic-grid {
    padding: 0 16px;
  }

  .basic-grid dl {
    grid-template-columns: 1fr;
  }

  .operation-log-row {
    grid-template-columns: 28px minmax(0, 1fr);
    padding: 8px 0;
  }

  .operation-log-row time {
    grid-column: 2;
    grid-row: auto;
  }

  .operation-log-row small {
    white-space: normal;
  }

  .milestone-editor-head {
    display: none;
  }

  .milestone-editor-row {
    grid-template-columns: minmax(0, 1fr) 40px;
  }

  .milestone-editor-row > input[type="date"],
  .milestone-editor-row > select {
    grid-column: 1;
  }

  .milestone-editor-row .milestone-remove {
    grid-column: 2;
    grid-row: 1 / 4;
  }

  .recent-experiment-row { grid-template-columns: 1fr; gap: 3px; }
  .recent-experiment-row span,
  .recent-experiment-row small { white-space: normal; }
}
</style>
