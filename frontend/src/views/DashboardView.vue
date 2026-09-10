<script setup lang="ts">
import { Icon } from "@iconify/vue/offline";
import { ElMessage } from "element-plus";
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";

import projectApprovalIcon from "@/assets/prototype/project-approval.svg";
import laboratoryIcon from "@/assets/prototype/laboratory.svg";
import { getProblemDetail } from "@/api/http";
import { projectApi } from "@/api/projects";
import type { DashboardSummary, Project } from "@/types/api";

const router = useRouter();
const loading = ref(true);
const summary = ref<DashboardSummary | null>(null);
const projectRecords = ref<Project[]>([]);

const projects = computed<DashboardSummary["active_projects"]>(() => {
  const dashboardProjects = summary.value?.active_projects ?? [];
  const includedIds = new Set(dashboardProjects.map((item) => item.id));
  const additionalProjects = projectRecords.value
    .filter((item) => (item.status === "active" || item.status === "at_risk") && !includedIds.has(item.id))
    .map((item) => {
      const milestone = item.milestones.find((entry) => entry.state === "current")
        ?? item.milestones.find((entry) => entry.state === "todo")
        ?? item.milestones[0]
        ?? null;
      const riskDays = milestone
        ? Math.ceil((new Date(milestone.date).getTime() - Date.now()) / 86_400_000)
        : null;
      return {
        id: item.id,
        project_no: item.project_no,
        name: item.name,
        project_type: item.project_type_code,
        owner_name: item.owner_display_name,
        objectives: item.objectives,
        planned_start_date: item.planned_start_date,
        planned_end_date: item.planned_end_date,
        milestone,
        progress_percent: item.progress_percent,
        is_followed: false,
        risk_level: item.status === "at_risk" || (riskDays !== null && riskDays < 0)
          ? "overdue" as const
          : "normal" as const,
        risk_days: riskDays,
      };
    });
  return [...dashboardProjects, ...additionalProjects]
    .sort((left, right) => Number(right.is_followed) - Number(left.is_followed))
    .slice(0, 10);
});
const projectMetrics = computed(() => summary.value?.project_metrics);
const experimentMetrics = computed(() => summary.value?.experiment_metrics);
/** 加载当前用户可见范围内的实时汇总数据。 */
async function loadDashboard(): Promise<void> {
  loading.value = true;
  try {
    const [dashboardSummary, projectPage] = await Promise.all([
      projectApi.dashboard(),
      projectApi.list({ page: 1, page_size: 100 }),
    ]);
    summary.value = dashboardSummary;
    projectRecords.value = projectPage.data;
  } catch (error) {
    const problem = getProblemDetail(error);
    ElMessage.error(problem?.detail ?? "总览数据加载失败");
  } finally {
    loading.value = false;
  }
}

/** 格式化空日期，避免展示虚构计划。 */
function formatDate(value: string | null): string {
  return value ? value.slice(0, 10) : "未设置";
}

/** 使用服务端统一计算的里程碑风险，避免客户端时区产生误判。 */
function milestoneHint(project: DashboardSummary["active_projects"][number]): string {
  if (project.risk_level === "overdue") return `延期 ${Math.abs(project.risk_days ?? 0)} 天`;
  if (project.risk_level === "countdown") {
    return project.risk_days === 0 ? "今日到期" : `剩余 ${project.risk_days} 天`;
  }
  if (project.risk_days === null) return "未设置";
  return `剩余 ${project.risk_days} 天`;
}

function openProject(projectId: string): void {
  void router.push(`/projects/${projectId}`);
}

function handleProjectKeydown(event: KeyboardEvent, projectId: string): void {
  if (event.key === "Enter" || event.key === " ") {
    event.preventDefault();
    openProject(projectId);
  }
}

/** 切换项目关注状态并立即反映到真实总览数据。 */
async function toggleFollow(projectId: string, isFollowed: boolean): Promise<void> {
  try {
    await projectApi.setFollow(projectId, !isFollowed);
    await loadDashboard();
  } catch (error) {
    const problem = getProblemDetail(error);
    ElMessage.error(problem?.detail ?? "项目关注状态更新失败");
  }
}

onMounted(loadDashboard);
</script>

<template>
  <section v-loading="loading" class="overview-page">
    <section class="metric-groups" aria-label="项目与实验关键指标">
      <section class="metric-group metric-group-projects" aria-label="项目数据">
        <div class="metric-grid metric-grid-projects">
          <article class="metric-primary">
            <span class="metric-value">
              <img :src="projectApprovalIcon" alt="" />
              <strong>{{ projectMetrics?.total ?? 0 }}</strong>
            </span>
            <span>项目总数</span>
          </article>
          <article><strong>{{ projectMetrics?.active ?? 0 }}</strong><span>进行中</span></article>
          <article><strong>{{ projectMetrics?.archived ?? 0 }}</strong><span>已归档</span></article>
          <article class="metric-risk"><strong>{{ projectMetrics?.at_risk ?? 0 }}</strong><span>风险项目</span></article>
        </div>
      </section>

      <section class="metric-group metric-group-experiments" aria-label="实验数据">
        <div class="metric-grid metric-grid-experiments">
          <article class="metric-primary">
            <span class="metric-value">
              <img :src="laboratoryIcon" alt="" />
              <strong>{{ experimentMetrics?.total ?? 0 }}</strong>
            </span>
            <span>实验总数</span>
          </article>
          <article><strong>{{ experimentMetrics?.in_progress ?? 0 }}</strong><span>进行中</span></article>
          <article><strong>{{ experimentMetrics?.completed ?? 0 }}</strong><span>已完成</span></article>
        </div>
      </section>
    </section>

    <section class="project-section">
      <header class="project-section-heading">
        <h2>进行中项目</h2>
        <button
          class="ui-button ui-button--light ui-button--icon"
          type="button"
          aria-label="查看全部项目"
          title="查看全部项目"
          @click="router.push('/projects')"
        >
          <Icon icon="tabler:list-details" />
        </button>
      </header>

      <div class="project-grid">
        <article
          v-for="project in projects"
          :key="project.id"
          class="project-card"
          :class="{
            'project-card-overdue': project.risk_level === 'overdue',
            'project-card-warning': project.risk_level === 'countdown',
          }"
          tabindex="0"
          @click="openProject(project.id)"
          @keydown="handleProjectKeydown($event, project.id)"
        >
          <header class="project-card-heading">
            <Icon icon="ri:folder-3-line" />
            <h3>{{ project.name }}</h3>
            <button
              class="ui-button ui-button--light ui-button--icon"
              type="button"
              :aria-label="project.is_followed ? '取消关注项目' : '关注项目'"
              :title="project.is_followed ? '取消关注' : '关注项目'"
              @click.stop="toggleFollow(project.id, project.is_followed)"
            >
              <Icon :icon="project.is_followed ? 'ri:star-fill' : 'ri:star-line'" />
            </button>
          </header>

          <p class="project-field project-dates">
            <Icon icon="ri:calendar-line" />
            <time>{{ formatDate(project.planned_start_date) }}</time>
            <span>—</span>
            <time>{{ formatDate(project.planned_end_date) }}</time>
          </p>

          <dl class="project-meta project-field">
            <Icon icon="ri:price-tag-3-line" />
            <div><dt>类型：</dt><dd>{{ project.project_type }}</dd></div>
            <div><dt>负责人：</dt><dd>{{ project.owner_name }}</dd></div>
          </dl>

          <section class="project-objective project-field">
            <Icon icon="ri:focus-3-line" />
            <div>
              <span>目标：</span>
              <p>{{ project.objectives[0] || '未设置项目目标' }}</p>
            </div>
          </section>

          <section class="project-milestone project-field">
            <Icon icon="ri:timeline-view" />
            <div>
              <span>里程碑</span>
              <div
                class="milestone-due"
                :class="{
                  'milestone-due-overdue': project.risk_level === 'overdue',
                  'milestone-due-warning': project.risk_level === 'countdown',
                }"
              >
                <div>
                  <strong>{{ project.milestone?.name || '未设置里程碑' }}</strong>
                  <time>{{ project.milestone?.date || '—' }}</time>
                </div>
                <b>{{ milestoneHint(project) }}</b>
              </div>
            </div>
          </section>
        </article>
      </div>
    </section>
  </section>
</template>

<style scoped>
.overview-page {
  height: 100%;
  min-width: 0;
  padding: 14px 0 24px;
  overflow-x: hidden;
  overflow-y: auto;
  scrollbar-gutter: stable;
}

.metric-groups {
  display: grid;
  grid-template-columns: minmax(0, 1.06fr) minmax(0, 0.94fr);
  margin-bottom: var(--space-card);
  gap: 12px;
}

.metric-group {
  min-width: 0;
  overflow: hidden;
  background: var(--color-paper);
  border: 1px solid var(--color-rule);
  border-radius: 7px;
}

.metric-grid {
  display: grid;
  min-height: 64px;
  padding: 4px 0;
}

.metric-grid-projects {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.metric-grid-experiments {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.metric-grid article {
  display: flex;
  align-items: center;
  justify-content: center;
  min-width: 0;
  flex-direction: column;
  padding: 6px 12px;
  text-align: center;
  border-left: 1px solid var(--color-rule-2);
}

.metric-grid article:first-child {
  border-left: 0;
}

.metric-grid article strong {
  color: var(--color-ink);
  font-size: 20px;
  line-height: 1;
  font-variant-numeric: tabular-nums;
}

.metric-grid article > span:not(.metric-value) {
  margin-top: 5px;
  color: var(--color-ink-2);
  font-size: 12px;
  font-weight: 550;
}

.metric-grid .metric-primary strong {
  color: var(--color-accent);
  font-size: 27px;
}

.metric-grid .metric-risk strong {
  color: #dc2c26;
}

.metric-value {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.metric-value img {
  width: 24px;
  height: 24px;
}

.project-section {
  margin-top: 10px;
}

.project-section-heading {
  display: flex;
  align-items: center;
  min-height: 38px;
  gap: 7px;
}

.project-section-heading h2 {
  margin: 0;
  color: var(--color-ink);
  font-size: 16px;
}

.project-section-heading button {
  display: grid;
  width: 28px;
  height: 28px;
  padding: 0;
  color: var(--color-accent);
  background: transparent;
  border: 0;
  border-radius: 5px;
  cursor: pointer;
  place-items: center;
}

.project-section-heading svg {
  width: 19px;
  height: 19px;
}

.project-grid {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 10px;
}

.project-card {
  position: relative;
  display: grid;
  min-width: 0;
  min-height: 286px;
  padding: 12px;
  overflow: hidden;
  background: var(--color-paper);
  border: 1px solid var(--color-rule);
  border-radius: 7px;
  box-shadow: var(--shadow-whisper);
  cursor: pointer;
  gap: 3px;
  isolation: isolate;
  transition: transform 180ms ease, box-shadow 180ms ease;
}

.project-card-overdue {
  background: var(--color-paper);
}

.project-card-warning {
  background: var(--color-paper);
}

.project-card-heading {
  display: grid;
  align-items: center;
  grid-template-columns: 16px minmax(0, 1fr) 36px;
  gap: 10px;
}

.project-card-heading > svg,
.project-field > svg {
  width: 16px;
  height: 16px;
  color: var(--color-muted);
}

.project-card-heading h3 {
  min-width: 0;
  min-height: 38px;
  margin: 0;
  color: var(--color-ink);
  font-size: 14px;
  line-height: 1.35;
}

.project-card-heading button {
  display: grid;
  width: 30px;
  height: 30px;
  padding: 0;
  color: var(--color-muted);
  background: transparent;
  border: 0;
  border-radius: 6px;
  cursor: pointer;
  place-items: center;
}

.project-card-heading button svg {
  width: 18px;
  height: 18px;
}

.project-field {
  display: grid;
  min-width: 0;
  grid-template-columns: 16px minmax(0, 1fr);
  margin: 0;
  gap: 10px;
}

.project-dates {
  display: flex;
  align-items: center;
  color: var(--color-muted);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}

.project-meta {
  grid-template-columns: 16px repeat(2, minmax(0, 1fr));
  align-items: center;
}

.project-meta div {
  display: flex;
  min-width: 0;
  align-items: baseline;
  font-size: 12px;
}

.project-meta dt {
  flex: none;
  color: var(--color-muted);
}

.project-meta dd {
  margin: 0;
  overflow: hidden;
  color: var(--color-ink);
  font-weight: 650;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.project-objective {
  align-items: flex-start;
}

.project-objective span,
.project-milestone > div > span {
  color: var(--color-muted);
  font-size: 12px;
}

.project-objective p {
  display: -webkit-box;
  margin: 2px 0 0;
  overflow: hidden;
  color: var(--color-ink-2);
  font-size: 13px;
  line-height: 1.45;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.project-milestone {
  align-items: flex-start;
}

.project-milestone > div {
  min-width: 0;
}

.milestone-due {
  display: grid;
  align-items: center;
  grid-template-columns: minmax(0, 1fr) auto;
  padding: 7px 8px;
  margin-top: 3px;
  background: var(--color-paper);
  border: 1px solid var(--color-rule-2);
  border-radius: 7px;
  box-shadow: var(--shadow-whisper);
  gap: 10px;
}

.milestone-due div {
  display: grid;
  min-width: 0;
  gap: 3px;
}

.milestone-due strong {
  overflow: hidden;
  color: var(--color-ink);
  font-size: 13px;
  line-height: 1.35;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.milestone-due time {
  color: var(--color-muted);
  font-size: 12px;
}

.milestone-due b {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 68px;
  height: 27px;
  padding: 0 10px;
  color: #078545;
  font-size: 12px;
  background: var(--color-success-soft);
  border-radius: 999px;
  white-space: nowrap;
}

.milestone-due-warning b {
  color: #a95200;
  background: var(--color-warning-soft);
}

.milestone-due-overdue b {
  color: #dc2c26;
  background: var(--color-danger-soft);
}

@media (hover: hover) and (pointer: fine) {
  .project-card:hover {
    z-index: 1;
    box-shadow: 0 12px 28px rgb(15 23 42 / 16%);
    transform: translateY(-3px);
  }

  .project-section-heading button:hover,
  .project-card-heading button:hover {
    background: var(--color-paper-3);
  }
}

@media (max-width: 1180px) {
  .metric-groups {
    grid-template-columns: 1fr;
  }

  .project-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (min-width: 1181px) and (max-width: 1500px) {
  .project-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 700px) {
  .overview-page {
    padding-top: 10px;
  }

  .metric-grid article {
    padding-inline: 7px;
  }

  .metric-grid article strong {
    font-size: 21px;
  }

  .metric-grid .metric-primary strong {
    font-size: 27px;
  }

  .metric-grid article > span:not(.metric-value) {
    font-size: 11px;
  }

  .project-grid {
    grid-template-columns: 1fr;
  }

  .project-card {
    min-height: 308px;
  }
}
</style>
