<script setup lang="ts">
import { Icon } from "@iconify/vue";
import { ElMessage } from "element-plus";
import {
  computed,
  nextTick,
  onBeforeUnmount,
  onMounted,
  reactive,
  ref,
  watch,
} from "vue";
import { useRoute, useRouter } from "vue-router";

import { getProblemDetail } from "@/api/http";
import { projectApi } from "@/api/projects";
import { userApi } from "@/api/users";
import { useSessionStore } from "@/stores/session";
import type {
  OrganizationUserOption,
  Project,
  ProjectCreateInput,
  ProjectMilestone,
} from "@/types/api";
import { futureDateShortcuts } from "@/utils/datePicker";

type AggregateStatus = "" | "running" | "not_started" | "ended";
type CreateProjectForm = Omit<ProjectCreateInput, "member_ids"> & {
  member_ids: string[];
};

const route = useRoute();
const router = useRouter();
const sessionStore = useSessionStore();
const loading = ref(false);
const submitting = ref(false);
const projects = ref<Project[]>([]);
const userOptions = ref<OrganizationUserOption[]>([]);
const total = ref(0);
const createVisible = ref(false);
const firstField = ref<HTMLInputElement>();
const createTrigger = ref<HTMLButtonElement>();

const filters = reactive({
  page: 1,
  pageSize: 20,
  search: "",
  status: "" as AggregateStatus,
  projectType: "" as "" | "聚酰亚胺" | "环氧树脂",
  ownerId: "",
  ordering: "-updated_at",
});

const createForm = reactive<CreateProjectForm>({
  name: "",
  project_type_code: "",
  description: "",
  owner_id: "",
  planned_start_date: "",
  planned_end_date: "",
  objectives: [],
  milestones: [],
  member_ids: [],
});
const objectiveText = ref("");
const createError = ref("");
const memberSearch = ref("");
const statusOptions: Array<{ value: AggregateStatus; label: string }> = [
  { value: "", label: "全部" },
  { value: "running", label: "进行中" },
  { value: "not_started", label: "待开始" },
  { value: "ended", label: "已结束" },
];

const emptyDescription = computed(() =>
  filters.search ||
  filters.status ||
  filters.projectType ||
  filters.ownerId
    ? "没有找到匹配项目，请调整筛选条件"
    : "暂无项目，可新建项目开始沉淀实验过程与数据",
);

const memberOptions = computed(() =>
  userOptions.value.filter((user) => {
    if (user.id === createForm.owner_id) return false;
    const keyword = memberSearch.value.trim().toLowerCase();
    return (
      !keyword ||
      `${user.display_name} ${user.username} ${user.organization_name}`
        .toLowerCase()
        .includes(keyword)
    );
  }),
);

function localDateTimeValue(date: Date): string {
  const offsetDate = new Date(date.getTime() - date.getTimezoneOffset() * 60_000);
  return offsetDate.toISOString().slice(0, 16);
}

function formatDate(value: string | null): string {
  if (!value) return "—";
  return new Intl.DateTimeFormat("zh-CN", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  })
    .format(new Date(value))
    .replaceAll("/", "-");
}

function formatUpdatedAt(value: string): string {
  return new Intl.DateTimeFormat("zh-CN", {
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  })
    .format(new Date(value))
    .replaceAll("/", "-");
}

function statusLabel(status: Project["status"]): string {
  const labels: Record<Project["status"], string> = {
    draft: "待开始",
    not_started: "待开始",
    active: "进行中",
    at_risk: "进行中",
    suspended: "已暂停",
    completed: "已结束",
    archived: "已结束",
  };
  return labels[status];
}

function statusClass(status: Project["status"]): string {
  if (status === "at_risk" || status === "suspended") return "risk";
  if (status === "active") return "running";
  if (status === "draft" || status === "not_started") return "pending";
  return "ended";
}

async function loadProjects(): Promise<void> {
  loading.value = true;
  try {
    const response = await projectApi.list({
      page: filters.page,
      page_size: filters.pageSize,
      search: filters.search.trim() || undefined,
      status: filters.status,
      project_type: filters.projectType,
      owner_id: filters.ownerId || undefined,
      ordering: filters.ordering,
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

async function loadUserOptions(): Promise<void> {
  try {
    userOptions.value = await userApi.listOptions();
  } catch (error) {
    const problem = getProblemDetail(error);
    ElMessage.error(problem?.detail ?? "人员选项加载失败");
  }
}

function applyFilters(): void {
  filters.page = 1;
  void loadProjects();
}

function selectStatus(status: AggregateStatus): void {
  filters.status = status;
  applyFilters();
}

function resetFilters(): void {
  Object.assign(filters, {
    page: 1,
    search: "",
    status: "",
    projectType: "",
    ownerId: "",
    ordering: "-updated_at",
  });
  void loadProjects();
}

function initializeCreateForm(): void {
  const now = new Date();
  Object.assign(createForm, {
    name: "",
    project_type_code: "",
    description: "",
    owner_id: sessionStore.user?.id ?? userOptions.value[0]?.id ?? "",
    planned_start_date: localDateTimeValue(now),
    planned_end_date: "",
    objectives: [],
    milestones: [
      {
        name: "",
        date: "",
        state: "current",
      } satisfies ProjectMilestone,
    ],
    member_ids: [],
  });
  objectiveText.value = "";
  memberSearch.value = "";
  createError.value = "";
}

/**
 * 项目经理变更后，确保经理不会同时作为普通项目成员提交
 *
 * @param ownerId 新选择的项目经理用户 ID
 */
function handleOwnerChange(ownerId: string): void {
  createForm.member_ids = createForm.member_ids.filter(
    (memberId) => memberId !== ownerId,
  );
}

/**
 * 按姓名、账号或组织筛选项目成员候选项
 *
 * @param keyword 下拉框内输入的筛选关键字
 */
function filterMemberOptions(keyword: string): void {
  memberSearch.value = keyword;
}

/**
 * 成员下拉框关闭后清理筛选词，确保下次展开显示完整候选项
 *
 * @param visible 下拉框是否展开
 */
function handleMemberSelectVisibleChange(visible: boolean): void {
  if (!visible) memberSearch.value = "";
}

function openCreateDrawer(): void {
  initializeCreateForm();
  createVisible.value = true;
  void nextTick(() => firstField.value?.focus());
}

function closeCreateDrawer(
  returnFocus = true,
  allowWhileSubmitting = false,
): void {
  if (submitting.value && !allowWhileSubmitting) return;
  createVisible.value = false;
  createError.value = "";
  if (returnFocus) {
    void nextTick(() => createTrigger.value?.focus());
  }
}

/**
 * 使用 Esc 关闭项目抽屉，Element Plus 下拉展开时由下拉先消费本次按键
 *
 * @param event 键盘事件
 */
function handleDocumentKeydown(event: KeyboardEvent): void {
  if (event.key !== "Escape" || !createVisible.value) return;
  const target = event.target;
  if (
    target instanceof Element &&
    target.closest(".el-select") &&
    document.querySelector(".el-select-dropdown:not(.is-hidden)")
  ) {
    return;
  }
  event.preventDefault();
  closeCreateDrawer();
}

function addMilestone(): void {
  createForm.milestones.push({ name: "", date: "", state: "todo" });
}

function removeMilestone(index: number): void {
  if (createForm.milestones.length === 1) {
    ElMessage.warning("至少保留一个重点里程碑");
    return;
  }
  const removedWasCurrent = createForm.milestones[index]?.state === "current";
  createForm.milestones.splice(index, 1);
  if (removedWasCurrent && createForm.milestones[0]) {
    createForm.milestones[0].state = "current";
  }
}

function validateCreateForm(): boolean {
  const objectives = objectiveText.value
    .split("\n")
    .map((item) => item.trim())
    .filter(Boolean);
  createError.value = "";
  if (
    !createForm.name.trim() ||
    !createForm.project_type_code ||
    !createForm.owner_id ||
    !createForm.planned_start_date ||
    !createForm.planned_end_date ||
    !objectives.length
  ) {
    createError.value = "请完整填写项目名称、类型、经理、周期和研发总体目标。";
    return false;
  }
  if (createForm.planned_end_date < createForm.planned_start_date) {
    createError.value = "结束时间不能早于开始时间。";
    return false;
  }
  if (
    !createForm.milestones.length ||
    createForm.milestones.some((item) => !item.name.trim() || !item.date)
  ) {
    createError.value = "请同时填写里程碑名称和完成日期。";
    return false;
  }
  createForm.objectives = objectives;
  createForm.description = objectives.join("；");
  createForm.milestones = createForm.milestones.map((item, index) => ({
    name: item.name.trim(),
    date: item.date,
    state: index === 0 ? "current" : "todo",
  }));
  return true;
}

async function submitProject(): Promise<void> {
  if (submitting.value || !validateCreateForm()) return;
  submitting.value = true;
  try {
    await projectApi.create({
      ...createForm,
      member_ids: [...createForm.member_ids],
      objectives: [...createForm.objectives],
      milestones: createForm.milestones.map((item) => ({ ...item })),
    });
    closeCreateDrawer(true, true);
    filters.page = 1;
    await loadProjects();
    ElMessage.success(
      `项目已创建，${createForm.milestones.length} 个里程碑已同步至看板`,
    );
  } catch (error) {
    const problem = getProblemDetail(error);
    if (!problem) {
      console.error("项目创建请求未发送", error);
    }
    createError.value =
      Object.values(problem?.field_errors ?? {})[0]?.[0] ??
      problem?.detail ??
      (error instanceof Error
        ? `项目创建请求未发送：${error.message}`
        : "项目创建失败");
  } finally {
    submitting.value = false;
  }
}

function openProject(project: Project): void {
  void router.push(`/projects/${project.id}`);
}

function handleRowKeydown(event: KeyboardEvent, project: Project): void {
  if (event.key === "Enter" || event.key === " ") {
    event.preventDefault();
    openProject(project);
  }
}

watch(
  () => route.query.create,
  (value) => {
    if (value === "1" && sessionStore.hasPermission("project.create")) {
      openCreateDrawer();
      void router.replace({ name: "projects" });
    }
  },
  { immediate: true },
);

onMounted(async () => {
  document.addEventListener("keydown", handleDocumentKeydown);
  await Promise.all([loadProjects(), loadUserOptions()]);
});
onBeforeUnmount(() => {
  document.removeEventListener("keydown", handleDocumentKeydown);
});
</script>

<template>
  <section class="project-data-page" :class="{ 'drawer-open': createVisible }">
    <div class="project-main">
      <section class="filter-panel" aria-label="项目筛选">
        <div class="status-tabs" role="tablist" aria-label="项目状态">
          <button
            v-for="item in statusOptions"
            :key="item.value"
            type="button"
            :class="{ active: filters.status === item.value }"
            @click="selectStatus(item.value)"
          >
            {{ item.label }}
          </button>
        </div>

        <label>
          <span>项目类型</span>
          <el-select
            v-model="filters.projectType"
            placeholder="全部"
            style="width: 120px"
            @change="applyFilters"
          >
            <el-option label="全部" value="" />
            <el-option label="聚酰亚胺" value="聚酰亚胺" />
            <el-option label="环氧树脂" value="环氧树脂" />
          </el-select>
        </label>
        <label>
          <span>负责人</span>
          <select v-model="filters.ownerId" @change="applyFilters">
            <option value="">全部</option>
            <option
              v-for="user in userOptions"
              :key="user.id"
              :value="user.id"
            >
              {{ user.display_name }}
            </option>
          </select>
        </label>
        <label>
          <span>排序</span>
          <select v-model="filters.ordering" @change="applyFilters">
            <option value="-updated_at">更新时间 ↓</option>
            <option value="updated_at">更新时间 ↑</option>
            <option value="project_no">项目编号 ↑</option>
            <option value="-project_no">项目编号 ↓</option>
          </select>
        </label>
        <label class="search-field">
          <Icon icon="tabler:search" />
          <input
            v-model="filters.search"
            type="search"
            placeholder="搜索项目名称或编号"
            @keyup.enter="applyFilters"
          />
        </label>
        <button
          class="ui-button ui-button--secondary ui-button--icon filter-icon-button"
          type="button"
          aria-label="查询"
          @click="applyFilters"
        >
          <Icon icon="tabler:search" />
        </button>
        <button
          class="ui-button ui-button--secondary ui-button--icon filter-icon-button"
          type="button"
          aria-label="重置筛选"
          @click="resetFilters"
        >
          <Icon icon="tabler:refresh" />
        </button>
        <button
          v-if="sessionStore.hasPermission('project.create')"
          ref="createTrigger"
          class="ui-button ui-button--primary primary-button"
          type="button"
          aria-controls="project-create-drawer"
          :aria-expanded="createVisible"
          @click="openCreateDrawer"
        >
          <Icon icon="tabler:plus" />新建项目
        </button>
      </section>

      <div class="result-bar">
        <span>共 {{ total }} 个项目</span>
      </div>

      <section v-loading="loading" class="project-table-card" aria-live="polite">
        <div class="table-scroll">
          <table>
            <thead>
              <tr>
                <th>项目名称 / 编号</th>
                <th>项目类型</th>
                <th>负责人</th>
                <th>开始时间</th>
                <th>结束时间</th>
                <th class="number-cell">文档</th>
                <th class="number-cell">实验</th>
                <th class="number-cell">数据资源</th>
                <th>最近更新</th>
                <th>状态</th>
                <th><span class="visually-hidden">操作</span></th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="project in projects"
                :key="project.id"
                tabindex="0"
                @click="openProject(project)"
                @keydown="handleRowKeydown($event, project)"
              >
                <td>
                  <strong>{{ project.name }}</strong>
                  <small>{{ project.project_no }}</small>
                </td>
                <td><span class="type-pill">{{ project.project_type_code }}</span></td>
                <td>{{ project.owner_display_name }}</td>
                <td>{{ formatDate(project.planned_start_date) }}</td>
                <td>{{ formatDate(project.planned_end_date) }}</td>
                <td class="number-cell">{{ project.document_count }}</td>
                <td class="number-cell">{{ project.experiment_count }}</td>
                <td class="number-cell">{{ project.data_resource_count }}</td>
                <td>{{ formatUpdatedAt(project.updated_at) }}</td>
                <td>
                  <span class="status-pill" :class="statusClass(project.status)">
                    {{ statusLabel(project.status) }}
                  </span>
                </td>
                <td><Icon class="row-chevron" icon="tabler:chevron-right" /></td>
              </tr>
            </tbody>
          </table>

          <div v-if="!loading && !projects.length" class="empty-projects">
            <Icon icon="tabler:folder-search" />
            <strong>{{ emptyDescription }}</strong>
            <button
              class="ui-button ui-button--secondary"
              type="button"
              @click="resetFilters"
            >
              重置筛选
            </button>
          </div>
        </div>

        <footer class="table-footer">
          <label>
            每页显示
            <select v-model="filters.pageSize" @change="applyFilters">
              <option :value="10">10</option>
              <option :value="20">20</option>
              <option :value="50">50</option>
            </select>
          </label>
          <el-pagination
            v-model:current-page="filters.page"
            :page-size="filters.pageSize"
            :total="total"
            layout="prev, pager, next"
            @current-change="loadProjects"
          />
        </footer>
      </section>
    </div>

    <aside
      v-if="createVisible"
      id="project-create-drawer"
      class="project-drawer"
      role="dialog"
      aria-modal="false"
      aria-labelledby="create-project-title"
    >
      <header>
        <h2 id="create-project-title">新建项目</h2>
        <button
          class="ui-button ui-button--light ui-button--icon"
          type="button"
          aria-label="关闭新建项目"
          @click="closeCreateDrawer()"
        >
          <Icon icon="tabler:x" />
        </button>
      </header>

      <form @submit.prevent="submitProject">
        <div class="drawer-body">
          <section class="form-section">
            <h3><Icon icon="tabler:clipboard-text" />基础信息</h3>
            <div class="form-grid">
              <label class="wide">
                <span>项目名称 <i>*</i></span>
                <input
                  ref="firstField"
                  v-model="createForm.name"
                  type="text"
                  maxlength="200"
                  placeholder="请输入项目名称"
                />
              </label>
              <label>
                <span>项目类型 <i>*</i></span>
                <el-select
                  v-model="createForm.project_type_code"
                  placeholder="请选择项目类型"
                >
                  <el-option label="聚酰亚胺" value="聚酰亚胺" />
                  <el-option label="环氧树脂" value="环氧树脂" />
                </el-select>
              </label>
              <label>
                <span>项目经理 <i>*</i></span>
                <el-select
                  v-model="createForm.owner_id"
                  filterable
                  placeholder="输入姓名筛选"
                  @change="handleOwnerChange"
                >
                  <el-option
                    v-for="user in userOptions"
                    :key="user.id"
                    :label="`${user.display_name} · ${user.organization_name}`"
                    :value="user.id"
                  >
                    <span>{{ user.display_name }}</span>
                    <small>{{ user.organization_name }}</small>
                  </el-option>
                </el-select>
              </label>
              <label>
                <span>开始时间 <i>*</i></span>
                <el-date-picker
                  v-model="createForm.planned_start_date"
                  type="datetime"
                  format="YYYY/MM/DD HH:mm"
                  value-format="YYYY-MM-DDTHH:mm"
                  :shortcuts="futureDateShortcuts"
                  placeholder="选择开始时间"
                  style="width: 100%"
                />
              </label>
              <label>
                <span>结束时间 <i>*</i></span>
                <el-date-picker
                  v-model="createForm.planned_end_date"
                  type="datetime"
                  format="YYYY/MM/DD HH:mm"
                  value-format="YYYY-MM-DDTHH:mm"
                  :shortcuts="futureDateShortcuts"
                  placeholder="选择结束时间"
                  style="width: 100%"
                />
              </label>
            </div>
          </section>

          <section class="form-section">
            <h3><Icon icon="tabler:users" />人员组成</h3>
            <label class="member-select-field">
              <span>选择成员</span>
              <el-select
                v-model="createForm.member_ids"
                multiple
                filterable
                clearable
                collapse-tags
                collapse-tags-tooltip
                :reserve-keyword="false"
                :max-collapse-tags="2"
                placeholder="输入姓名、账号或组织筛选"
                no-data-text="暂无其他可选成员"
                no-match-text="没有匹配成员"
                :filter-method="filterMemberOptions"
                @visible-change="handleMemberSelectVisibleChange"
              >
                <el-option
                  v-for="user in memberOptions"
                  :key="user.id"
                  :label="user.display_name"
                  :value="user.id"
                >
                  <span class="member-option">
                    <i>{{ user.display_name.slice(0, 1) }}</i>
                    <span class="member-option-copy">
                      <strong>{{ user.display_name }}</strong>
                      <small>{{ user.username }} · {{ user.organization_name }}</small>
                    </span>
                  </span>
                </el-option>
              </el-select>
            </label>
          </section>

          <section class="form-section">
            <h3><Icon icon="tabler:target-arrow" />研发总体目标</h3>
            <label>
              <span>指标性目标 <i>*</i></span>
              <textarea
                v-model="objectiveText"
                rows="4"
                maxlength="5000"
                placeholder="每行填写一项指标，例如：&#10;拉伸强度 ≥ 55 MPa&#10;玻璃化转变温度 ≥ 280 ℃"
              />
            </label>
          </section>

          <section class="form-section">
            <div class="section-heading">
              <h3><Icon icon="tabler:flag-3" />重点里程碑</h3>
              <button
                class="ui-button ui-button--light ui-button--icon"
                type="button"
                aria-label="增加里程碑"
                @click="addMilestone"
              >
                <Icon icon="tabler:plus" />
              </button>
            </div>
            <div class="milestone-heading" aria-hidden="true">
              <span>里程碑名称</span><span>完成日期</span><span />
            </div>
            <div class="milestone-list">
              <div
                v-for="(milestone, index) in createForm.milestones"
                :key="index"
                class="milestone-row"
              >
                <input
                  v-model="milestone.name"
                  type="text"
                  maxlength="500"
                  aria-label="里程碑名称"
                  placeholder="例如：完成中试验证"
                />
                <el-date-picker
                  v-model="milestone.date"
                  type="date"
                  format="YYYY/MM/DD"
                  value-format="YYYY-MM-DD"
                  :shortcuts="futureDateShortcuts"
                  placeholder="选择完成日期"
                  aria-label="完成日期"
                  style="width: 100%"
                />
                <button
                  class="ui-button ui-button--danger ui-button--icon"
                  type="button"
                  aria-label="删除里程碑"
                  @click="removeMilestone(index)"
                >
                  <Icon icon="tabler:trash" />
                </button>
              </div>
            </div>
          </section>
          <p v-if="createError" class="form-error" role="alert">{{ createError }}</p>
        </div>

        <footer>
          <button
            class="ui-button ui-button--secondary secondary-button"
            type="button"
            @click="closeCreateDrawer()"
          >
            取消
          </button>
          <button
            class="ui-button ui-button--primary primary-button"
            type="submit"
            :disabled="submitting"
          >
            <Icon icon="tabler:check" />
            {{ submitting ? "创建中…" : "创建项目" }}
          </button>
        </footer>
      </form>
    </aside>
  </section>
</template>

<style scoped>
.project-data-page {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  height: 100%;
  min-width: 0;
  overflow: hidden;
  transition: grid-template-columns 220ms ease;
}

.project-data-page.drawer-open {
  grid-template-columns: minmax(0, 1fr) clamp(430px, 39vw, 560px);
}

.project-main {
  display: flex;
  min-width: 0;
  min-height: 0;
  flex-direction: column;
  padding: var(--space-card) 0;
  overflow: hidden;
}

.filter-panel {
  display: flex;
  min-height: 58px;
  align-items: center;
  flex: 0 0 auto;
  padding: 10px var(--space-card);
  overflow-x: auto;
  background: var(--color-paper);
  border: 1px solid var(--color-rule);
  border-radius: 8px;
  gap: var(--space-control);
}

.status-tabs {
  display: flex;
  height: 38px;
  align-items: center;
  padding: 3px;
  background: var(--color-paper-3);
  border-radius: 7px;
}

.status-tabs button {
  height: 32px;
  padding: 0 12px;
  color: var(--color-muted);
  white-space: nowrap;
  background: transparent;
  border: 0;
  border-radius: 5px;
  cursor: pointer;
}

.status-tabs button.active {
  color: var(--color-accent);
  font-weight: 650;
  background: var(--color-paper);
  box-shadow: 0 1px 4px rgb(15 23 42 / 8%);
}

.filter-panel > label {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 6px;
  color: var(--color-muted);
  font-size: 12px;
  white-space: nowrap;
}

.filter-panel select,
.filter-panel input {
  height: 36px;
  color: var(--color-ink-2);
  background: var(--color-paper);
  border: 1px solid var(--color-rule-2);
  border-radius: 6px;
  outline: 0;
}

.filter-panel select {
  min-width: 108px;
  padding: 0 28px 0 9px;
}

.search-field {
  position: relative;
  margin-left: auto;
}

.search-field svg {
  position: absolute;
  left: 10px;
  width: 16px;
}

.search-field input {
  width: 210px;
  padding: 0 9px 0 32px;
}

.filter-icon-button,
.primary-button,
.secondary-button {
  display: inline-flex;
  height: 36px;
  align-items: center;
  justify-content: center;
  padding: 0 12px;
  border-radius: 6px;
  cursor: pointer;
  gap: 6px;
  white-space: nowrap;
}

.filter-icon-button {
  width: 36px;
  padding: 0;
  color: var(--color-muted);
  background: var(--color-paper);
  border: 1px solid var(--color-rule-2);
}

.primary-button {
  color: #fff;
  background: var(--color-accent);
  border: 1px solid var(--color-accent);
}

.primary-button:disabled {
  cursor: wait;
  opacity: 0.65;
}

.secondary-button {
  color: var(--color-ink-2);
  background: var(--color-paper);
  border: 1px solid var(--color-rule-2);
}

.result-bar {
  display: flex;
  min-height: 34px;
  align-items: center;
  flex: 0 0 34px;
  padding: 0 12px;
  color: var(--color-muted);
  font-size: 12px;
}

.project-table-card {
  display: flex;
  min-height: 0;
  flex: 1;
  flex-direction: column;
  overflow: hidden;
  background: var(--color-paper);
  border: 1px solid var(--color-rule);
  border-radius: 8px;
}

.table-scroll {
  position: relative;
  min-height: 0;
  flex: 1;
  overflow: auto;
}

table {
  width: 100%;
  min-width: 1080px;
  border-spacing: 0;
  border-collapse: separate;
}

th {
  position: sticky;
  z-index: 2;
  top: 0;
  height: 44px;
  padding: 0 12px;
  color: var(--color-muted);
  font-size: 12px;
  font-weight: 600;
  text-align: left;
  white-space: nowrap;
  background: var(--color-paper-2);
  border-bottom: 1px solid var(--color-rule);
}

td {
  height: 66px;
  padding: 8px 12px;
  color: var(--color-ink-2);
  font-size: 13px;
  white-space: nowrap;
  border-bottom: 1px solid var(--color-rule);
}

tbody tr {
  cursor: pointer;
}

tbody tr:hover,
tbody tr:focus-visible {
  background: #f8faff;
}

tbody tr:focus-visible {
  outline: 2px solid var(--color-focus);
  outline-offset: -2px;
}

td strong,
td small {
  display: block;
}

td strong {
  max-width: 280px;
  overflow: hidden;
  color: var(--color-ink);
  font-size: 14px;
  text-overflow: ellipsis;
}

td small {
  margin-top: 4px;
  color: var(--color-faint);
  font-size: 11px;
}

.number-cell {
  text-align: center;
}

.type-pill,
.status-pill {
  display: inline-flex;
  align-items: center;
  padding: 3px 8px;
  border-radius: 5px;
}

.type-pill {
  color: var(--color-ink-2);
  background: var(--color-paper-3);
}

.status-pill.running {
  color: #078545;
  background: var(--color-success-soft);
}

.status-pill.risk {
  color: #a95200;
  background: var(--color-warning-soft);
}

.status-pill.pending {
  color: #8a6500;
  background: #fff7db;
}

.status-pill.ended {
  color: var(--color-muted);
  background: var(--color-paper-3);
}

.row-chevron {
  color: var(--color-faint);
}

.table-footer {
  display: flex;
  min-height: 52px;
  align-items: center;
  justify-content: space-between;
  flex: 0 0 52px;
  padding: 6px 12px;
  color: var(--color-muted);
  font-size: 12px;
  border-top: 1px solid var(--color-rule);
}

.table-footer label {
  display: flex;
  align-items: center;
  gap: 7px;
}

.table-footer select {
  height: 32px;
  padding: 0 20px 0 8px;
  background: var(--color-paper);
  border: 1px solid var(--color-rule-2);
  border-radius: 5px;
}

.empty-projects {
  position: absolute;
  inset: 45px 0 0;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  color: var(--color-muted);
  gap: 10px;
}

.empty-projects svg {
  width: 36px;
  height: 36px;
}

.empty-projects button {
  height: 34px;
  padding: 0 12px;
  color: var(--color-accent);
  background: var(--color-paper);
  border: 1px solid var(--color-accent);
  border-radius: 6px;
  cursor: pointer;
}

.project-drawer {
  z-index: 5;
  display: flex;
  min-width: 0;
  min-height: 0;
  flex-direction: column;
  margin-left: 12px;
  overflow: hidden;
  background: var(--color-paper);
  border-left: 1px solid var(--color-rule);
  box-shadow: -10px 0 34px rgb(15 23 42 / 9%);
}

.project-drawer > header {
  display: flex;
  min-height: 58px;
  align-items: center;
  justify-content: space-between;
  flex: 0 0 58px;
  padding: 0 18px;
  border-bottom: 1px solid var(--color-rule);
}

.project-drawer h2 {
  margin: 0;
  font-size: 18px;
}

.project-drawer header button,
.section-heading button,
.milestone-row button {
  display: grid;
  width: 32px;
  height: 32px;
  padding: 0;
  color: var(--color-muted);
  background: transparent;
  border: 1px solid var(--color-rule-2);
  border-radius: 6px;
  cursor: pointer;
  place-items: center;
}

.project-drawer form {
  display: flex;
  min-height: 0;
  flex: 1;
  flex-direction: column;
}

.drawer-body {
  min-height: 0;
  flex: 1;
  padding: 14px 16px 20px;
  overflow-y: auto;
}

.form-section {
  margin-bottom: 14px;
  padding: 14px 13px;
  border: 1px solid var(--color-rule);
  border-radius: 8px;
}

.form-section h3 {
  display: flex;
  align-items: center;
  padding-bottom: 10px;
  margin: 0 0 13px;
  font-size: 14px;
  border-bottom: 1px solid var(--color-rule);
  gap: 7px;
}

.form-section h3 svg {
  width: 18px;
  height: 18px;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.form-grid .wide {
  grid-column: 1 / -1;
}

.form-section label {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 6px;
}

.form-section label > span {
  color: var(--color-ink-3);
  font-size: 12px;
  font-weight: 600;
}

.form-section label i {
  color: #dc2626;
  font-style: normal;
}

.form-section :is(
  input[type="text"]:not(.el-select__input):not(.el-input__inner),
  input[type="datetime-local"],
  input[type="date"],
  select,
  textarea
) {
  width: 100%;
  min-width: 0;
  color: var(--color-ink);
  background: var(--color-paper-3);
  border: 1px solid transparent;
  border-radius: 6px;
  outline: 0;
}

.form-section :is(input:not(.el-select__input):not(.el-input__inner), select) {
  height: 38px;
  padding: 0 10px;
}

.form-section textarea {
  min-height: 108px;
  padding: 10px;
  line-height: 1.6;
  resize: vertical;
}

.form-section :is(input:not(.el-select__input):not(.el-input__inner), select, textarea):focus {
  background: var(--color-paper);
  border-color: var(--color-accent);
  box-shadow: 0 0 0 3px rgb(37 99 235 / 10%);
}

.form-section :deep(.el-select) {
  width: 100%;
}

.form-section :deep(.el-select__wrapper) {
  min-height: 38px;
  background: var(--color-paper-3);
  box-shadow: none;
}

.form-section :deep(.el-select__wrapper.is-focused) {
  background: var(--color-paper);
  box-shadow:
    0 0 0 1px var(--color-accent) inset,
    0 0 0 3px rgb(37 99 235 / 10%);
}

.member-select-field :deep(.el-select__wrapper) {
  min-height: 40px;
}

.member-option {
  display: flex;
  width: 100%;
  min-width: 0;
  align-items: center;
  gap: 8px;
}

.member-option > i {
  display: grid;
  width: 26px;
  height: 26px;
  flex: 0 0 26px;
  color: var(--color-accent);
  font-size: 12px;
  font-style: normal;
  background: var(--color-accent-soft);
  border-radius: 50%;
  place-items: center;
}

.member-option-copy {
  display: grid;
  min-width: 0;
  line-height: 1.25;
}

.member-option-copy strong,
.member-option-copy small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.member-option-copy strong {
  color: var(--color-ink);
  font-size: 13px;
  font-weight: 600;
}

.member-option-copy small {
  color: var(--color-muted);
  font-size: 11px;
}

.section-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 10px;
  margin-bottom: 10px;
  border-bottom: 1px solid var(--color-rule);
}

.section-heading h3 {
  padding: 0;
  margin: 0;
  border: 0;
}

.section-heading button {
  color: var(--color-accent);
  border-color: var(--color-accent);
}

.milestone-heading,
.milestone-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 144px 32px;
  align-items: center;
  gap: 8px;
}

.milestone-heading {
  padding: 0 1px 6px;
  color: var(--color-muted);
  font-size: 11px;
}

.milestone-list {
  display: grid;
  gap: 8px;
}

.milestone-row button:hover {
  color: #dc2626;
  border-color: #dc2626;
}

.form-error {
  padding: 9px 11px;
  margin: 0;
  color: #b42318;
  font-size: 12px;
  background: #fff1f0;
  border-radius: 6px;
}

.project-drawer form > footer {
  display: flex;
  min-height: 62px;
  align-items: center;
  justify-content: flex-end;
  flex: 0 0 62px;
  padding: 10px 16px;
  border-top: 1px solid var(--color-rule);
  gap: 9px;
}

.visually-hidden {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}

@media (max-width: 1080px) {
  .project-data-page.drawer-open {
    grid-template-columns: minmax(0, 1fr) min(52vw, 500px);
  }

  .filter-panel > label > span {
    display: none;
  }

  .search-field {
    margin-left: 0;
  }
}

@media (max-width: 760px) {
  .project-data-page.drawer-open {
    grid-template-columns: minmax(0, 1fr);
  }

  .project-drawer {
    position: fixed;
    z-index: 1200;
    inset: 0;
    width: 100%;
    margin: 0;
  }

  .project-main {
    padding: 10px 0;
  }

  .filter-panel {
    align-items: stretch;
    flex-wrap: wrap;
    overflow: visible;
  }

  .status-tabs {
    width: 100%;
  }

  .status-tabs button {
    flex: 1;
  }

  .search-field {
    flex: 1 1 180px !important;
  }

  .search-field input {
    width: 100%;
  }

  .form-grid {
    grid-template-columns: 1fr;
  }

  .form-grid .wide {
    grid-column: auto;
  }
}
</style>
