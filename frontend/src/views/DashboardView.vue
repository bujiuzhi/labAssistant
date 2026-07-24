<script setup lang="ts">
import { Calendar, CircleCheck, Clock, FolderOpened, Plus } from "@element-plus/icons-vue";
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";

import { projectApi } from "@/api/projects";
import ProjectStatusTag from "@/components/ProjectStatusTag.vue";
import { useSessionStore } from "@/stores/session";
import type { Project } from "@/types/api";

const router = useRouter();
const sessionStore = useSessionStore();

const loading = ref(true);
const recentProjects = ref<Project[]>([]);
const projectTotal = ref(0);
const activeTotal = ref(0);
const draftTotal = ref(0);
const errorMessage = ref("");

const todayLabel = computed(() =>
  new Intl.DateTimeFormat("zh-CN", {
    month: "long",
    day: "numeric",
    weekday: "long",
  }).format(new Date()),
);

async function loadDashboard(): Promise<void> {
  loading.value = true;
  errorMessage.value = "";
  try {
    const [allProjects, activeProjects, draftProjects] = await Promise.all([
      projectApi.list({ page_size: 5, ordering: "-updated_at" }),
      projectApi.list({ page_size: 1, status: "active" }),
      projectApi.list({ page_size: 1, status: "draft" }),
    ]);
    recentProjects.value = allProjects.data;
    projectTotal.value = allProjects.meta.total;
    activeTotal.value = activeProjects.meta.total;
    draftTotal.value = draftProjects.meta.total;
  } catch {
    errorMessage.value = "工作台数据加载失败，请稍后重试";
  } finally {
    loading.value = false;
  }
}

onMounted(loadDashboard);
</script>

<template>
  <section class="page-shell dashboard-page">
    <header class="page-header">
      <div>
        <h1 class="page-title">下午好，{{ sessionStore.displayName }}</h1>
        <p class="page-description">{{ todayLabel }}，这里是当前项目与实验工作的概览。</p>
      </div>
      <el-button
        v-if="sessionStore.hasPermission('project.create')"
        type="primary"
        :icon="Plus"
        @click="router.push({ name: 'projects', query: { create: '1' } })"
      >
        新建项目
      </el-button>
    </header>

    <el-alert
      v-if="errorMessage"
      :title="errorMessage"
      type="error"
      :closable="false"
      show-icon
      class="dashboard-alert"
    />

    <div v-loading="loading" class="dashboard-content">
      <section class="metric-strip" aria-label="项目统计">
        <article class="metric-item">
          <span class="metric-icon primary"><FolderOpened /></span>
          <div>
            <strong>{{ projectTotal }}</strong>
            <span>可见项目</span>
          </div>
        </article>
        <article class="metric-item">
          <span class="metric-icon info"><Clock /></span>
          <div>
            <strong>{{ activeTotal }}</strong>
            <span>进行中项目</span>
          </div>
        </article>
        <article class="metric-item">
          <span class="metric-icon warning"><Calendar /></span>
          <div>
            <strong>{{ draftTotal }}</strong>
            <span>待完善草稿</span>
          </div>
        </article>
        <article class="metric-item">
          <span class="metric-icon success"><CircleCheck /></span>
          <div>
            <strong>0</strong>
            <span>今日已完成</span>
          </div>
        </article>
      </section>

      <section class="workspace-grid">
        <article class="content-panel recent-projects">
          <header class="panel-header">
            <div>
              <h2>最近项目</h2>
              <p>按最后更新时间展示</p>
            </div>
            <el-button text type="primary" @click="router.push('/projects')">查看全部</el-button>
          </header>
          <div v-if="recentProjects.length" class="project-list">
            <button
              v-for="project in recentProjects"
              :key="project.id"
              class="project-row"
              type="button"
              @click="router.push(`/projects/${project.id}`)"
            >
              <span class="project-code">{{ project.project_no }}</span>
              <span class="project-name">{{ project.name }}</span>
              <ProjectStatusTag :status="project.status" />
              <span class="project-owner">{{ project.owner_display_name }}</span>
              <span class="project-date">
                {{ new Date(project.updated_at).toLocaleDateString("zh-CN") }}
              </span>
            </button>
          </div>
          <div v-else class="empty-state compact">
            <FolderOpened />
            <strong>还没有项目</strong>
            <span>创建第一个项目后，进展会显示在这里。</span>
          </div>
        </article>

        <aside class="content-panel task-panel">
          <header class="panel-header">
            <div>
              <h2>待处理事项</h2>
              <p>按优先级汇总</p>
            </div>
          </header>
          <div class="task-empty">
            <CircleCheck />
            <strong>当前没有待处理事项</strong>
            <span>实验提交、检测审核和临期提醒将在这里集中出现。</span>
          </div>
        </aside>
      </section>
    </div>
  </section>
</template>

<style scoped>
.dashboard-alert {
  flex: none;
  margin-bottom: 12px;
}

.dashboard-content {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
  gap: 14px;
}

.metric-strip {
  display: grid;
  flex: none;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
}

.metric-item {
  display: flex;
  align-items: center;
  min-width: 0;
  padding: 20px 22px;
}

.metric-item + .metric-item {
  border-left: 1px solid var(--color-border);
}

.metric-icon {
  display: grid;
  flex: none;
  width: 40px;
  height: 40px;
  margin-right: 14px;
  border-radius: 8px;
  place-items: center;
}

.metric-icon :deep(svg) {
  width: 19px;
}

.metric-icon.primary {
  color: var(--color-primary);
  background: var(--color-primary-soft);
}

.metric-icon.info {
  color: var(--color-info);
  background: #eaf4fb;
}

.metric-icon.warning {
  color: var(--color-warning);
  background: #fff4df;
}

.metric-icon.success {
  color: var(--color-success);
  background: #e8f7ee;
}

.metric-item div {
  display: flex;
  flex-direction: column;
}

.metric-item strong {
  color: var(--color-text-primary);
  font-size: 24px;
  font-weight: 650;
  line-height: 1.1;
}

.metric-item span:last-child {
  margin-top: 5px;
  color: var(--color-text-secondary);
  font-size: 12px;
}

.workspace-grid {
  display: grid;
  flex: 1;
  grid-template-columns: minmax(0, 1.7fr) minmax(280px, 0.7fr);
  min-height: 0;
  gap: 14px;
}

.recent-projects,
.task-panel {
  display: flex;
  flex-direction: column;
}

.panel-header {
  display: flex;
  flex: none;
  align-items: center;
  justify-content: space-between;
  min-height: 64px;
  padding: 0 18px;
  border-bottom: 1px solid var(--color-border);
}

.panel-header h2 {
  margin: 0;
  font-size: 15px;
  font-weight: 650;
}

.panel-header p {
  margin: 2px 0 0;
  color: var(--color-text-tertiary);
  font-size: 11px;
}

.project-list {
  min-height: 0;
  padding: 2px 10px;
  overflow-y: auto;
}

.project-row {
  display: grid;
  grid-template-columns: 122px minmax(180px, 1fr) 74px 90px 94px;
  align-items: center;
  width: 100%;
  min-height: 58px;
  padding: 0 10px;
  color: var(--color-text-primary);
  text-align: left;
  background: transparent;
  border: 0;
  border-bottom: 1px solid var(--color-border);
  cursor: pointer;
}

.project-row:hover {
  background: var(--color-bg-page);
}

.project-code {
  color: var(--color-text-secondary);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}

.project-name {
  overflow: hidden;
  font-weight: 550;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.project-owner,
.project-date {
  color: var(--color-text-secondary);
  font-size: 12px;
}

.empty-state.compact {
  gap: 7px;
  min-height: 250px;
}

.empty-state.compact :deep(svg) {
  width: 28px;
  color: var(--color-text-tertiary);
}

.empty-state.compact strong {
  color: var(--color-text-primary);
  font-size: 13px;
}

.empty-state.compact span {
  font-size: 12px;
}

.task-empty {
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 32px;
  color: var(--color-text-secondary);
  text-align: center;
}

.task-empty :deep(svg) {
  width: 34px;
  margin-bottom: 13px;
  color: var(--color-success);
}

.task-empty strong {
  color: var(--color-text-primary);
  font-size: 13px;
}

.task-empty span {
  max-width: 240px;
  margin-top: 7px;
  font-size: 12px;
  line-height: 1.7;
}
</style>
