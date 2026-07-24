<script setup lang="ts">
import { Icon } from "@iconify/vue";
import { ElMessage } from "element-plus";
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";

import projectApprovalIcon from "@/assets/prototype/project-approval.svg";
import laboratoryIcon from "@/assets/prototype/laboratory.svg";
import PrototypeCharts from "@/components/dashboard/PrototypeCharts.vue";
import { getProblemDetail } from "@/api/http";
import { projectApi } from "@/api/projects";
import type { DashboardSummary } from "@/types/api";

const router = useRouter();
const loading = ref(true);
const summary = ref<DashboardSummary | null>(null);

const projects = computed(() => summary.value?.active_projects ?? []);
const projectMetrics = computed(() => summary.value?.project_metrics);
const experimentMetrics = computed(() => summary.value?.experiment_metrics);

/** 加载当前用户可见范围内的实时汇总数据。 */
async function loadDashboard(): Promise<void> {
  loading.value = true;
  try {
    summary.value = await projectApi.dashboard();
  } catch (error) {
    const problem = getProblemDetail(error);
    ElMessage.error(problem?.detail ?? "总览数据加载失败");
  } finally {
    loading.value = false;
  }
}

/** 格式化空日期，避免展示虚构计划。 */
function formatDate(value: string | null): string {
  return value || "未设置";
}

/** 计算里程碑到期提示。 */
function milestoneHint(date: string | undefined): string {
  if (!date) return "未设置";
  const target = new Date(`${date}T00:00:00+08:00`);
  const today = new Date();
  const days = Math.ceil((target.getTime() - today.getTime()) / 86_400_000);
  if (days < 0) return `已逾期 ${Math.abs(days)} 天`;
  if (days === 0) return "今日到期";
  return `剩余 ${days} 天`;
}

/** 切换项目关注状态并立即反映到真实总览数据。 */
async function toggleFollow(projectId: string, isFollowed: boolean): Promise<void> {
  try {
    const nextValue = await projectApi.setFollow(projectId, !isFollowed);
    const item = projects.value.find((project) => project.id === projectId);
    if (item) item.is_followed = nextValue;
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

    <PrototypeCharts
      :type-distribution="summary?.type_distribution ?? []"
      :trend="summary?.trend ?? { dates: [], series: [] }"
    />

    <section class="project-section">
      <header class="project-section-heading">
        <h2>进行中项目</h2>
        <button type="button" aria-label="查看全部项目" title="查看全部项目" @click="router.push('/projects')">
          <Icon icon="tabler:list-details" />
        </button>
      </header>

      <div class="project-grid">
        <article
          v-for="project in projects"
          :key="project.id"
          class="project-card"
          :class="{
            'project-card-overdue': project.planned_end_date && new Date(project.planned_end_date) < new Date(),
            'project-card-warning': project.milestone?.date && milestoneHint(project.milestone.date).startsWith('剩余'),
          }"
          tabindex="0"
          @click="router.push(`/projects/${project.id}`)"
          @keydown.enter="router.push(`/projects/${project.id}`)"
        >
          <header class="project-card-heading">
            <Icon icon="ri:folder-3-line" />
            <h3>{{ project.name }}</h3>
            <button
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
                  'milestone-due-overdue': project.milestone?.date && milestoneHint(project.milestone.date).startsWith('已逾期'),
                  'milestone-due-warning': project.milestone?.date && milestoneHint(project.milestone.date).startsWith('剩余'),
                }"
              >
                <div>
                  <strong>{{ project.milestone?.name || '未设置里程碑' }}</strong>
                  <time>{{ project.milestone?.date || '—' }}</time>
                </div>
                <b>{{ milestoneHint(project.milestone?.date) }}</b>
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
  padding: 16px 0 24px;
  overflow-x: hidden;
  overflow-y: auto;
  scrollbar-gutter: stable;
}

.metric-groups {
  display: grid;
  grid-template-columns: minmax(0, 1.06fr) minmax(0, 0.94fr);
  margin-bottom: 10px;
  gap: 12px;
}

.metric-group {
  min-width: 0;
  overflow: hidden;
  background: var(--color-paper);
  border: 1px solid var(--color-rule);
  border-radius: 8px;
}

.metric-group-projects {
  border-color: #3a9aff;
}

.metric-group-experiments {
  border-color: #d88100;
}

.metric-grid {
  display: grid;
  min-height: 72px;
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
  font-size: 24px;
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
  font-size: 32px;
}

.metric-group-experiments .metric-primary strong,
.metric-group-experiments .metric-primary > span:not(.metric-value) {
  color: #a95200;
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
  margin-top: 12px;
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
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.project-card {
  position: relative;
  display: grid;
  min-width: 0;
  min-height: 330px;
  padding: 15px;
  overflow: hidden;
  background: var(--color-paper);
  border: 1px solid var(--color-rule);
  border-radius: 8px;
  box-shadow: var(--shadow-whisper);
  cursor: pointer;
  gap: 5px;
  isolation: isolate;
  transition:
    transform 180ms ease,
    border-color 180ms ease,
    box-shadow 180ms ease;
}

.project-card::after {
  position: absolute;
  right: 14px;
  bottom: 0;
  left: 14px;
  height: 2px;
  background: var(--color-accent);
  border-radius: 999px;
  content: "";
  opacity: 0;
}

.project-card-overdue {
  background: linear-gradient(180deg, #fff8f7 0%, #ffffff 42%);
  border-color: #f06a64;
}

.project-card-overdue::after {
  background: #e13932;
  opacity: 1;
}

.project-card-warning {
  background: linear-gradient(180deg, #fffaf2 0%, #ffffff 42%);
  border-color: #df9100;
}

.project-card-warning::after {
  background: #df9100;
}

.project-card-heading {
  display: grid;
  align-items: center;
  grid-template-columns: 16px minmax(0, 1fr) 30px;
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
  font-size: 16px;
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
  line-height: 1.55;
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
  padding: 8px 10px;
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
  min-width: 76px;
  height: 30px;
  padding: 0 10px;
  color: #078545;
  font-size: 12px;
  background: var(--color-success-soft);
  border-radius: 999px;
  white-space: nowrap;
}

.milestone-due-warning {
  border-color: #df9100;
  box-shadow: 0 0 0 2px rgb(223 145 0 / 10%);
}

.milestone-due-warning b {
  color: #a95200;
  background: var(--color-warning-soft);
}

.milestone-due-overdue {
  border-color: #df655f;
  box-shadow: 0 0 0 2px rgb(225 57 50 / 8%);
}

.milestone-due-overdue b {
  color: #dc2c26;
  background: var(--color-danger-soft);
}

@media (hover: hover) and (pointer: fine) {
  .project-card:hover {
    border-color: var(--color-accent);
    box-shadow: 0 14px 34px rgb(8 124 240 / 14%);
    transform: translateY(-4px);
  }

  .project-card:hover::after {
    opacity: 1;
  }

  .project-card-overdue:hover {
    border-color: #e13932;
    box-shadow: 0 14px 34px rgb(225 57 50 / 12%);
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
</style>
