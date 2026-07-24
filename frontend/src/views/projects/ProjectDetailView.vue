<script setup lang="ts">
import { Icon } from "@iconify/vue";
import { ElMessage } from "element-plus";
import { computed, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";

import { getProblemDetail } from "@/api/http";
import { projectApi } from "@/api/projects";
import { prototypeProjects, type PrototypeProject } from "@/data/prototype-dashboard";
import { useSessionStore } from "@/stores/session";
import type { Project } from "@/types/api";

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
const loading = ref(false);
const loadError = ref("");
const apiProject = ref<Project | null>(null);

interface ExtendedProjectFields {
  current_stage: string;
  progress_percent: number;
  document_count: number;
  experiment_count: number;
  data_resource_count: number;
  objectives: string[];
}

const projectTypeLabels: Record<string, string> = {
  research: "研发项目",
  validation: "验证项目",
  commissioned: "委托项目",
};

/**
 * 将真实 API 项目转换为原型详情页所需的展示结构
 *
 * @param source API 返回的项目
 * @returns 原型详情页展示项目
 */
function adaptApiProject(source: Project): PrototypeProject {
  const extended = source as Project & Partial<ExtendedProjectFields>;
  const isArchived = ["completed", "archived"].includes(source.status);
  const isAtRisk = ["at_risk", "suspended"].includes(source.status);
  const isActive = source.status === "active";

  return {
    id: source.project_no,
    name: source.name,
    startDate: source.planned_start_date ?? "未设置",
    endDate: source.planned_end_date ?? "未设置",
    type: projectTypeLabels[source.project_type_code] ?? source.project_type_code,
    owner: source.owner_display_name,
    objective: extended.objectives?.[0] ?? (source.description || "暂无项目目标"),
    milestoneName: isArchived ? "项目已完成" : "完成当前阶段评审",
    milestoneDate: source.planned_end_date ?? "未设置",
    urgency: isAtRisk ? "warning" : "normal",
    countdownText: isArchived ? "已完成" : "按计划推进",
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

const prototypeProject = computed(() =>
  prototypeProjects.find((item) => item.id === String(route.params.projectId)),
);

const project = computed<PrototypeProject | null>(() => {
  if (prototypeProject.value) {
    return prototypeProject.value;
  }
  return apiProject.value ? adaptApiProject(apiProject.value) : null;
});

const projectTabs = ["概览", "文档资料", "实验管理", "数据资产"];
const activeProjectTab = ref("概览");

const milestones = computed<MilestoneStage[]>(() => {
  const currentProject = project.value;
  if (!currentProject) {
    return [];
  }
  if (currentProject.id === "PRJ-2026-PI-005") {
    return [
      {
        stage: "阶段 01",
        name: "完成应用需求分解、原料选型与总体技术方案评审",
        date: "2026-02-20",
        state: "done",
      },
      {
        stage: "阶段 02",
        name: "完成聚酰胺酸合成路线筛选及溶液稳定性验证",
        date: "2026-04-18",
        state: "done",
      },
      {
        stage: "阶段 03",
        name: "完成精密流延与梯度亚胺化工艺窗口验证",
        date: "2026-07-20",
        state: "current",
      },
      {
        stage: "阶段 04",
        name: "完成薄膜热学、力学、介电性能及批次一致性评价",
        date: "2026-09-25",
        state: "todo",
      },
      {
        stage: "阶段 05",
        name: "完成百米级连续制膜中试评审与工艺包定版",
        date: "2026-11-20",
        state: "todo",
      },
    ];
  }
  return [
    {
      stage: "阶段 01",
      name: "完成项目需求分解与技术方案评审",
      date: currentProject.startDate,
      state: "done",
    },
    {
      stage: "阶段 02",
      name: currentProject.milestoneName,
      date: currentProject.milestoneDate,
      state: "current",
    },
    {
      stage: "阶段 03",
      name: "完成性能验证与项目验收",
      date: currentProject.endDate,
      state: "todo",
    },
  ];
});

const memberNames = computed(() => {
  if (!project.value) {
    return [];
  }
  const allMembers = [project.value.owner, "李娜", "赵敏", "刘洋"];
  return [...new Set(allMembers)].slice(0, 3);
});

const statusLabel = computed(() => {
  if (!project.value) return "";
  if (project.value.status === "有风险") return "有风险";
  if (project.value.status === "已归档") return "已归档";
  if (project.value.status === "待开始") return "待开始";
  return "进行中";
});

/**
 * 根据路由参数加载项目详情
 *
 * 原型业务编号从保真数据读取；数据库 UUID 从项目 API 读取。
 */
async function loadProject(): Promise<void> {
  if (prototypeProject.value) {
    apiProject.value = null;
    loadError.value = "";
    return;
  }

  loading.value = true;
  loadError.value = "";
  apiProject.value = null;
  try {
    apiProject.value = await projectApi.get(String(route.params.projectId));
  } catch (error) {
    const problem = getProblemDetail(error);
    loadError.value = problem?.detail ?? "项目详情加载失败";
    ElMessage.error(loadError.value);
  } finally {
    loading.value = false;
  }
}

onMounted(loadProject);
watch(() => route.params.projectId, loadProject);
</script>

<template>
  <section v-loading="loading" class="project-overview-page">
    <template v-if="project">
      <p class="breadcrumb">
      <button type="button" @click="router.push('/projects')">项目数据</button>
      <span>/</span>
      {{ project.name }}
      </p>

      <header class="project-hero">
        <div>
          <div class="project-title-line"><h1>{{ project.name }}</h1></div>
          <p>
            <span>{{ project.id }}</span>
            <span class="type-chip">{{ project.type }}</span>
            <span class="status-chip" :class="project.status">{{ statusLabel }}</span>
            <span>负责人 {{ project.owner }}</span>
            <span>·</span>
            <span>{{ project.startDate }} 至 {{ project.endDate }}</span>
            <span>·</span>
            <span>当前阶段&nbsp; <strong>{{ project.stage }}</strong></span>
          </p>
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
          <h2>基础信息</h2>
          <button
            v-if="sessionStore.hasPermission('project.update')"
            class="edit-button"
            type="button"
            @click="editVisible = true"
          >
            <Icon icon="tabler:edit" />编辑
          </button>
        </header>
        <div class="basic-grid">
          <dl>
            <div><dt>项目类型</dt><dd>{{ project.type }}</dd></div>
            <div><dt>项目周期</dt><dd>{{ project.startDate }} 至 {{ project.endDate }}</dd></div>
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
              <li>{{ project.objective }}</li>
              <li v-if="project.id === 'PRJ-2026-PI-005'">
                优化流延与亚胺化参数，完成性能验证及百米级中试。
              </li>
            </ul>
          </section>
        </div>
      </section>

      <section class="detail-card milestone-card">
        <header>
          <h2>项目里程碑</h2>
          <button
            v-if="sessionStore.hasPermission('project.update')"
            class="edit-button"
            type="button"
            @click="editVisible = true"
          >
            <Icon icon="tabler:edit" />编辑
          </button>
        </header>
        <div class="milestone-scroll">
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
            <time v-for="item in milestones" :key="`${item.stage}-date`">{{ item.date }}</time>
          </div>
        </div>
      </section>

      <section class="detail-card progress-card">
        <header><h2>项目进展</h2></header>
        <div class="progress-summary">
          <strong>{{ project.progress }}%</strong>
          <i><b :style="{ width: `${project.progress}%` }" /></i>
          <span>当前阶段：{{ project.stage }}</span>
          <dl>
            <div><dt>文档</dt><dd>{{ project.documentCount }}</dd></div>
            <div><dt>实验</dt><dd>{{ project.experimentCount }}</dd></div>
            <div><dt>数据资源</dt><dd>{{ project.resourceCount }}</dd></div>
          </dl>
        </div>
      </section>
      </div>

      <section v-else class="detail-card module-placeholder">
      <Icon
        :icon="
          activeProjectTab === '文档资料'
            ? 'tabler:files'
            : activeProjectTab === '数据资产'
              ? 'tabler:database'
              : 'tabler:flask'
        "
      />
      <h2>{{ activeProjectTab }}</h2>
      <p>该模块将继续按原型对应页面复刻。</p>
      </section>

      <el-dialog v-model="editVisible" title="编辑项目" width="560px" align-center>
      <el-form label-position="top">
        <el-form-item label="项目名称"><el-input :model-value="project.name" /></el-form-item>
        <div class="dialog-grid">
          <el-form-item label="项目类型"><el-input :model-value="project.type" /></el-form-item>
          <el-form-item label="项目经理"><el-input :model-value="project.owner" /></el-form-item>
        </div>
        <el-form-item label="研发总体目标">
          <el-input :model-value="project.objective" type="textarea" :rows="4" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" @click="editVisible = false">保存修改</el-button>
      </template>
      </el-dialog>
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
  padding: 12px 0 24px;
  overflow-x: hidden;
  overflow-y: auto;
}

.load-error {
  min-height: 420px;
}

.breadcrumb {
  display: flex;
  align-items: center;
  margin: 0 0 8px;
  color: var(--color-faint);
  font-size: 12px;
  gap: 5px;
}

.breadcrumb button {
  padding: 0;
  color: var(--color-muted);
  background: transparent;
  border: 0;
  cursor: pointer;
}

.project-hero {
  display: flex;
  align-items: center;
  min-height: 82px;
  padding: 12px 14px;
  background: var(--color-paper);
  border: 1px solid var(--color-rule);
  border-radius: 8px;
  box-shadow: var(--shadow-whisper);
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
}

.type-chip {
  padding: 3px 8px;
  color: var(--color-ink-2);
  background: var(--color-paper-3);
  border-radius: 5px;
}

.status-chip {
  padding: 3px 8px;
  color: #078545;
  background: var(--color-success-soft);
  border-radius: 5px;
}

.status-chip.有风险 {
  color: #a95200;
  background: var(--color-warning-soft);
}

.status-chip.待开始,
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
  padding-top: 14px;
  gap: 12px;
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

.basic-card {
  padding-bottom: 16px;
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

.objective ul {
  padding-left: 18px;
  margin: 0;
  color: var(--color-ink-2);
  line-height: 1.8;
}

.milestone-card {
  padding-bottom: 16px;
}

.milestone-scroll {
  padding: 0 28px;
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
  color: var(--color-ink-2);
  font-size: 11px;
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

.module-placeholder {
  display: flex;
  min-height: 360px;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  margin-top: 14px;
  color: var(--color-muted);
}

.module-placeholder svg {
  width: 36px;
  height: 36px;
  color: var(--color-accent);
}

.module-placeholder h2 {
  margin-top: 12px;
  color: var(--color-ink);
}

.module-placeholder p {
  margin: 5px 0 0;
}

.dialog-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

@media (max-width: 1180px) {
  .basic-grid dl {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .progress-summary {
    grid-template-columns: 58px minmax(160px, 1fr);
  }
}
</style>
