<script setup lang="ts">
import { Icon } from "@iconify/vue";
import { ElMessage } from "element-plus";
import { computed, nextTick, onMounted, reactive, ref } from "vue";
import { useRoute } from "vue-router";

import { experimentApi } from "@/api/experiments";
import { getProblemDetail } from "@/api/http";
import { projectApi } from "@/api/projects";
import { useSessionStore } from "@/stores/session";
import type {
  Experiment,
  ExperimentStatus,
  ExperimentWriteInput,
  ExtraFormulaTable,
  ExtraProcess,
  FormulaColumn,
  ProcessImage,
  Project,
  ResultFile,
} from "@/types/api";

const sessionStore = useSessionStore();
const route = useRoute();

const statusOptions: Array<{ value: ExperimentStatus; label: string }> = [
  { value: "in_progress", label: "进行中" },
  { value: "not_started", label: "未开始" },
  { value: "completed", label: "已完成" },
];
const experimentTypes = [
  "配方筛选",
  "性能测试",
  "热分析",
  "结构表征",
  "工艺优化",
  "可靠性测试",
  "单体",
  "聚合",
  "其他",
];

const experiments = ref<Experiment[]>([]);
const projects = ref<Project[]>([]);
const activeStatus = ref<ExperimentStatus>("in_progress");
const selectedExperimentId = ref("");
const selectedProjectId = ref("");
const projectSearch = ref("");
const projectFilterOpen = ref(false);
const copyPanelOpen = ref(false);
const copySearch = ref("");
const copySelectedId = ref("");
const loading = ref(true);
const saving = ref(false);
const isCreating = ref(false);
const previewImage = ref<ProcessImage | null>(null);
const listWidth = ref(495);
const resizing = ref(false);
const recordScroll = ref<HTMLElement | null>(null);
let resizerStartX = 0;
let resizerStartWidth = 0;

interface EditorState {
  name: string;
  project_id: string;
  experiment_type: string;
  purpose: string;
  estimated_start: string;
  estimated_end: string;
  formula_columns: FormulaColumn[];
  formula_rows: Record<string, string>[];
  extra_tables: ExtraFormulaTable[];
  process_text: string;
  extra_processes: ExtraProcess[];
  process_images: ProcessImage[];
  result_text: string;
  result_files: ResultFile[];
}

const editor = reactive<EditorState>(emptyEditor());

function emptyEditor(): EditorState {
  const columns = Array.from({ length: 4 }, (_, index) => ({
    id: `col_${index + 1}`,
    label: index === 0 ? "原料名称" : "",
  }));
  return {
    name: "",
    project_id: "",
    experiment_type: "",
    purpose: "",
    estimated_start: "",
    estimated_end: "",
    formula_columns: columns,
    formula_rows: [
      Object.fromEntries(columns.map((column) => [column.id, ""])),
      Object.fromEntries(columns.map((column) => [column.id, ""])),
    ],
    extra_tables: [],
    process_text: "",
    extra_processes: [],
    process_images: [],
    result_text: "",
    result_files: [],
  };
}

function clone<T>(value: T): T {
  return JSON.parse(JSON.stringify(value)) as T;
}

const selectedExperiment = computed(
  () =>
    experiments.value.find((item) => item.id === selectedExperimentId.value) ??
    null,
);

const canEdit = computed(
  () =>
    isCreating.value ||
    (selectedExperiment.value?.status !== "completed" &&
      sessionStore.hasPermission("experiment.update")),
);

const filteredExperiments = computed(() => {
  return experiments.value.filter(
    (item) =>
      item.status === activeStatus.value &&
      (!selectedProjectId.value || item.project_id === selectedProjectId.value),
  );
});

const statusCounts = computed<Record<ExperimentStatus, number>>(() => ({
  in_progress: experiments.value.filter(
    (item) =>
      item.status === "in_progress" &&
      (!selectedProjectId.value || item.project_id === selectedProjectId.value),
  ).length,
  not_started: experiments.value.filter(
    (item) =>
      item.status === "not_started" &&
      (!selectedProjectId.value || item.project_id === selectedProjectId.value),
  ).length,
  completed: experiments.value.filter(
    (item) =>
      item.status === "completed" &&
      (!selectedProjectId.value || item.project_id === selectedProjectId.value),
  ).length,
}));

const selectedProjectName = computed(
  () =>
    projects.value.find((item) => item.id === selectedProjectId.value)?.name ??
    "全部",
);

const projectOptions = computed(() => {
  const projectIds = new Set(experiments.value.map((item) => item.project_id));
  const keyword = projectSearch.value.trim().toLowerCase();
  const options = [
    { id: "", project_no: "", name: "全部项目" },
    ...projects.value.filter((item) => projectIds.has(item.id)),
  ];
  return options.filter(
    (item) =>
      !keyword ||
      `${item.name} ${item.project_no}`.toLowerCase().includes(keyword) ||
      (!item.id && "全部".includes(keyword)),
  );
});

const copyOptions = computed(() => {
  const keyword = copySearch.value.trim().toLowerCase();
  return experiments.value.filter(
    (item) =>
      !keyword ||
      `${item.name} ${item.experiment_no}`.toLowerCase().includes(keyword),
  );
});

const primaryActionLabel = computed(() => {
  if (isCreating.value) return "创建实验";
  if (selectedExperiment.value?.status === "not_started") return "开始实验";
  return "保存记录";
});

function statusLabel(status: ExperimentStatus): string {
  return statusOptions.find((item) => item.value === status)?.label ?? status;
}

function formatDateTime(value: string | null): string {
  if (!value) return "—";
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

function formatListTime(value: string): string {
  const date = new Date(value);
  const now = new Date();
  if (date.toDateString() === now.toDateString()) {
    return `今天 ${String(date.getHours()).padStart(2, "0")}:${String(
      date.getMinutes(),
    ).padStart(2, "0")}`;
  }
  return `${String(date.getMonth() + 1).padStart(2, "0")}-${String(
    date.getDate(),
  ).padStart(2, "0")} ${String(date.getHours()).padStart(2, "0")}:${String(
    date.getMinutes(),
  ).padStart(2, "0")}`;
}

function toInputDateTime(value: string | null): string {
  if (!value) return "";
  const date = new Date(value);
  const local = new Date(date.getTime() - date.getTimezoneOffset() * 60_000);
  return local.toISOString().slice(0, 16);
}

function recordToEditor(experiment: Experiment): EditorState {
  const record = clone(experiment.record);
  return {
    name: experiment.name,
    project_id: experiment.project_id,
    experiment_type: experiment.experiment_type,
    purpose: experiment.purpose,
    estimated_start: toInputDateTime(experiment.estimated_start),
    estimated_end: toInputDateTime(experiment.estimated_end),
    formula_columns: record.formula_columns.length
      ? record.formula_columns
      : [{ id: "material", label: "原料名称" }],
    formula_rows: record.formula_rows.length
      ? record.formula_rows
      : [{ material: "" }, { material: "" }],
    extra_tables: record.extra_tables,
    process_text: record.process_text,
    extra_processes: record.extra_processes,
    process_images: record.process_images,
    result_text: record.result_text,
    result_files: record.result_files,
  };
}

function replaceEditor(next: EditorState): void {
  Object.assign(editor, clone(next));
}

function selectExperiment(experiment: Experiment): void {
  isCreating.value = false;
  selectedExperimentId.value = experiment.id;
  replaceEditor(recordToEditor(experiment));
  void nextTick(() => {
    if (recordScroll.value) recordScroll.value.scrollTop = 0;
  });
}

function selectFirstVisible(): void {
  const first = filteredExperiments.value[0];
  if (first) {
    selectExperiment(first);
    return;
  }
  selectedExperimentId.value = "";
}

function changeStatus(status: ExperimentStatus): void {
  activeStatus.value = status;
  isCreating.value = false;
  selectFirstVisible();
}

function selectProject(projectId: string): void {
  selectedProjectId.value = projectId;
  projectFilterOpen.value = false;
  projectSearch.value = "";
  isCreating.value = false;
  selectFirstVisible();
}

function updateProjectSearch(event: Event): void {
  projectSearch.value = (event.target as HTMLInputElement).value;
}

function startCreate(source?: Experiment): void {
  isCreating.value = true;
  selectedExperimentId.value = "";
  const next = emptyEditor();
  next.project_id = selectedProjectId.value;
  if (source) {
    const sourceEditor = recordToEditor(source);
    Object.assign(next, sourceEditor, {
      name: `${source.name}-副本`,
      process_images: [],
      result_text: "",
      result_files: [],
    });
  }
  replaceEditor(next);
  copyPanelOpen.value = false;
  copySearch.value = "";
  copySelectedId.value = "";
  void nextTick(() => {
    if (recordScroll.value) recordScroll.value.scrollTop = 0;
  });
  ElMessage.success(
    source
      ? "已进入新增计划，原计划基础信息、配方和过程已加载"
      : "请填写新的实验计划",
  );
}

function confirmCopyPlan(): void {
  const source = experiments.value.find((item) => item.id === copySelectedId.value);
  if (source) startCreate(source);
}

function addFormulaRow(): void {
  editor.formula_rows.push(
    Object.fromEntries(editor.formula_columns.map((column) => [column.id, ""])),
  );
}

function removeFormulaRow(index: number): void {
  if (editor.formula_rows.length > 1) editor.formula_rows.splice(index, 1);
}

function addFormulaColumn(): void {
  const id = `col_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`;
  editor.formula_columns.push({ id, label: "新列" });
  editor.formula_rows.forEach((row) => {
    row[id] = "";
  });
}

function removeFormulaColumn(index: number): void {
  if (editor.formula_columns.length <= 1) return;
  const [column] = editor.formula_columns.splice(index, 1);
  editor.formula_rows.forEach((row) => {
    delete row[column.id];
  });
}

function addExtraTable(): void {
  const token = `${Date.now()}_${Math.random().toString(36).slice(2, 7)}`;
  const columns = [
    { id: `a_${token}`, label: "" },
    { id: `b_${token}`, label: "" },
  ];
  editor.extra_tables.push({
    id: `table_${token}`,
    name: "新增表格",
    columns,
    rows: [
      Object.fromEntries(columns.map((column) => [column.id, ""])),
      Object.fromEntries(columns.map((column) => [column.id, ""])),
    ],
  });
}

function addExtraTableRow(table: ExtraFormulaTable): void {
  table.rows.push(Object.fromEntries(table.columns.map((column) => [column.id, ""])));
}

function addExtraTableColumn(table: ExtraFormulaTable): void {
  const id = `col_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`;
  table.columns.push({ id, label: "新列" });
  table.rows.forEach((row) => {
    row[id] = "";
  });
}

function removeExtraTableColumn(
  table: ExtraFormulaTable,
  columnIndex: number,
): void {
  if (table.columns.length <= 1) return;
  const [column] = table.columns.splice(columnIndex, 1);
  table.rows.forEach((row) => {
    delete row[column.id];
  });
}

function addExtraProcess(): void {
  editor.extra_processes.push({
    id: `process_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`,
    name: "新增过程",
    content: "",
  });
}

async function addProcessImages(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement;
  const files = Array.from(input.files ?? []);
  const current = selectedExperiment.value;
  if (!current) {
    ElMessage.warning("请先创建实验计划后再上传真实过程图片");
    input.value = "";
    return;
  }
  try {
    for (const file of files) {
      if (file.size > 10 * 1024 * 1024) {
        ElMessage.warning(`${file.name} 超过 10 MB，未添加`);
        continue;
      }
      const updated = await experimentApi.uploadAttachment(current.experiment_no, file, "process_image");
      replaceExperiment(updated);
      replaceEditor(recordToEditor(updated));
    }
    ElMessage.success("过程图片已真实上传");
  } catch (error) {
    const problem = getProblemDetail(error);
    ElMessage.error(problem?.detail ?? "过程图片上传失败");
  } finally {
    input.value = "";
  }
}

async function addResultFiles(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement;
  const current = selectedExperiment.value;
  if (!current) {
    ElMessage.warning("请先创建实验计划后再上传真实结果附件");
    input.value = "";
    return;
  }
  try {
    for (const file of Array.from(input.files ?? [])) {
      const updated = await experimentApi.uploadAttachment(current.experiment_no, file, "result_file");
      replaceExperiment(updated);
      replaceEditor(recordToEditor(updated));
    }
    ElMessage.success("结果附件已真实上传");
  } catch (error) {
    const problem = getProblemDetail(error);
    ElMessage.error(problem?.detail ?? "结果附件上传失败");
  } finally {
    input.value = "";
  }
}

function payloadFromEditor(): ExperimentWriteInput {
  return {
    project_id: editor.project_id,
    name: editor.name.trim(),
    experiment_type: editor.experiment_type,
    purpose: editor.purpose.trim(),
    estimated_start: editor.estimated_start || null,
    estimated_end: editor.estimated_end || null,
    formula_columns: editor.formula_columns.map((column, index) => ({
      ...column,
      label: column.label.trim() || `列${index + 1}`,
    })),
    formula_rows: clone(editor.formula_rows),
    extra_tables: clone(editor.extra_tables),
    process_text: editor.process_text,
    extra_processes: clone(editor.extra_processes),
    result_text: editor.result_text,
  };
}

function validateEditor(): boolean {
  if (!editor.project_id) {
    ElMessage.warning("请选择关联项目");
    return false;
  }
  if (!editor.name.trim()) {
    ElMessage.warning("请输入实验名称");
    return false;
  }
  if (!editor.experiment_type) {
    ElMessage.warning("请选择实验类型");
    return false;
  }
  if (
    editor.estimated_start &&
    editor.estimated_end &&
    editor.estimated_end < editor.estimated_start
  ) {
    ElMessage.warning("预估结束时间不能早于开始时间");
    return false;
  }
  return true;
}

function replaceExperiment(updated: Experiment): void {
  const index = experiments.value.findIndex((item) => item.id === updated.id);
  if (index === -1) experiments.value.unshift(updated);
  else experiments.value.splice(index, 1, updated);
}

async function persistRecord(isDraft: boolean): Promise<void> {
  if (saving.value || !validateEditor()) return;
  saving.value = true;
  try {
    if (isCreating.value) {
      const created = await experimentApi.create(payloadFromEditor());
      replaceExperiment(created);
      activeStatus.value = "not_started";
      selectedProjectId.value = created.project_id;
      selectExperiment(created);
      ElMessage.success(isDraft ? "实验草稿已暂存" : "实验计划已创建");
      return;
    }
    const current = selectedExperiment.value;
    if (!current) return;
    const updated = await experimentApi.update(
      current.experiment_no,
      current.version,
      payloadFromEditor(),
    );
    replaceExperiment(updated);
    if (!isDraft && current.status === "not_started") {
      const started = await experimentApi.transition(
        updated.experiment_no,
        updated.version,
        "in_progress",
      );
      replaceExperiment(started);
      activeStatus.value = "in_progress";
      selectExperiment(started);
      ElMessage.success("实验已开始");
      return;
    }
    selectExperiment(updated);
    ElMessage.success(isDraft ? "草稿已暂存" : "实验记录已保存");
  } catch (error) {
    const problem = getProblemDetail(error);
    ElMessage.error(problem?.detail ?? "实验记录保存失败，请稍后重试");
  } finally {
    saving.value = false;
  }
}

function beginResize(event: PointerEvent): void {
  resizing.value = true;
  resizerStartX = event.clientX;
  resizerStartWidth = listWidth.value;
  window.addEventListener("pointermove", resizeList);
  window.addEventListener("pointerup", finishResize, { once: true });
}

function resizeList(event: PointerEvent): void {
  if (!resizing.value) return;
  listWidth.value = Math.min(
    630,
    Math.max(390, resizerStartWidth + event.clientX - resizerStartX),
  );
}

function finishResize(): void {
  resizing.value = false;
  window.removeEventListener("pointermove", resizeList);
}

async function loadPage(): Promise<void> {
  loading.value = true;
  try {
    const [experimentResponse, projectResponse] = await Promise.all([
      experimentApi.list({ page_size: 100 }),
      projectApi.list({ page_size: 100, ordering: "project_no" }),
    ]);
    experiments.value = experimentResponse.data;
    projects.value = projectResponse.data;
    const requestedExperiment = String(route.query.experiment ?? "");
    const initial =
      experiments.value.find(
        (item) =>
          item.experiment_no === requestedExperiment ||
          item.id === requestedExperiment,
      ) ??
      experiments.value.find((item) => item.status === activeStatus.value) ??
      experiments.value[0];
    if (initial) {
      activeStatus.value = initial.status;
      selectExperiment(initial);
    }
  } catch (error) {
    const problem = getProblemDetail(error);
    ElMessage.error(problem?.detail ?? "电子实验记录本加载失败");
  } finally {
    loading.value = false;
  }
}

onMounted(loadPage);
</script>

<template>
  <section
    class="eln-page"
    :class="{ 'is-resizing': resizing }"
    :style="{ '--list-width': `${listWidth}px` }"
  >
    <aside class="experiment-list-panel">
      <section class="experiment-list-card">
        <header class="plan-actions">
          <div class="copy-plan-control">
            <button
              class="button copy-plan-button"
              type="button"
              aria-haspopup="listbox"
              :aria-expanded="copyPanelOpen"
              @click="copyPanelOpen = !copyPanelOpen"
            >
              <Icon icon="tabler:copy" />
              复制计划
              <Icon class="copy-chevron" icon="tabler:chevron-down" />
            </button>
            <div v-if="copyPanelOpen" class="copy-plan-dropdown">
              <label class="dropdown-search">
                <Icon icon="tabler:search" />
                <input
                  v-model="copySearch"
                  type="search"
                  placeholder="搜索实验名称"
                  autocomplete="off"
                />
              </label>
              <div class="copy-plan-list" role="radiogroup" aria-label="选择实验计划">
                <button
                  v-for="item in copyOptions"
                  :key="item.id"
                  class="copy-plan-item"
                  :class="{ selected: copySelectedId === item.id }"
                  type="button"
                  role="radio"
                  :aria-checked="copySelectedId === item.id"
                  @click="copySelectedId = item.id"
                >
                  <span class="copy-radio" />
                  <span class="copy-main">
                    <strong>{{ item.name }}</strong>
                    <small>{{ item.experiment_no }} · {{ item.project_name }}</small>
                  </span>
                  <span class="status-chip" :class="item.status">
                    {{ statusLabel(item.status) }}
                  </span>
                </button>
                <p v-if="!copyOptions.length" class="copy-empty">
                  没有匹配的实验计划
                </p>
              </div>
              <footer>
                <button
                  class="button"
                  type="button"
                  @click="copyPanelOpen = false"
                >
                  取消
                </button>
                <button
                  class="button primary"
                  type="button"
                  :disabled="!copySelectedId"
                  @click="confirmCopyPlan"
                >
                  <Icon icon="tabler:copy" />
                  复制所选计划
                </button>
              </footer>
            </div>
          </div>
          <button class="button primary" type="button" @click="startCreate()">
            <Icon icon="tabler:plus" />
            新增计划
          </button>
        </header>

        <div class="combined-filter">
          <label class="project-filter-trigger">
            <Icon icon="tabler:search" />
            <input
              :value="projectFilterOpen ? projectSearch : selectedProjectName"
              type="text"
              placeholder="搜索项目名称或编号"
              autocomplete="off"
              aria-label="项目筛选"
              @focus="
                projectFilterOpen = true;
                projectSearch = '';
              "
              @input="updateProjectSearch"
            />
          </label>
          <div v-if="projectFilterOpen" class="project-options" role="listbox">
            <button
              v-for="project in projectOptions"
              :key="project.id || 'all'"
              class="project-option"
              :class="{ selected: selectedProjectId === project.id }"
              type="button"
              role="option"
              :aria-selected="selectedProjectId === project.id"
              @mousedown.prevent="selectProject(project.id)"
            >
              <Icon icon="tabler:check" />
              <span>{{ project.name }}</span>
              <small v-if="project.project_no">{{ project.project_no }}</small>
            </button>
            <p v-if="!projectOptions.length" class="filter-empty">
              没有匹配的项目
            </p>
          </div>
        </div>

        <div class="record-tabs" role="tablist">
          <button
            v-for="status in statusOptions"
            :key="status.value"
            type="button"
            :class="{ active: activeStatus === status.value }"
            @click="changeStatus(status.value)"
          >
            {{ status.label }} <b>{{ statusCounts[status.value] }}</b>
          </button>
        </div>

        <div v-loading="loading" class="experiment-list" aria-live="polite">
          <button
            v-for="experiment in filteredExperiments"
            :key="experiment.id"
            class="experiment-row"
            :class="{ selected: selectedExperiment?.id === experiment.id }"
            type="button"
            @click="selectExperiment(experiment)"
          >
            <strong>{{ experiment.name }}</strong>
            <small>{{ experiment.project_name }}</small>
            <small>{{ experiment.experiment_no }}</small>
            <footer>
              <span class="status-chip" :class="experiment.status">
                {{ statusLabel(experiment.status) }}
              </span>
              <time>{{ formatListTime(experiment.updated_at) }}</time>
            </footer>
          </button>
          <div v-if="!loading && !filteredExperiments.length" class="list-empty">
            <Icon icon="tabler:flask-off" />
            <p>当前分类没有关联实验</p>
          </div>
        </div>
      </section>
    </aside>

    <div
      class="layout-resizer"
      aria-label="调整实验列表宽度"
      @pointerdown="beginResize"
    />

    <article class="record-workspace">
      <div
        v-if="selectedExperiment || isCreating"
        ref="recordScroll"
        class="record-scroll"
      >
        <section class="record-section basic-info-section">
          <header>
            <h3>实验基础信息</h3>
          </header>

          <template v-if="!isCreating && selectedExperiment">
            <div class="record-summary">
              <div class="record-title">
                <h2>{{ selectedExperiment.name }}</h2>
                <span class="status-chip" :class="selectedExperiment.status">
                  {{ statusLabel(selectedExperiment.status) }}
                </span>
              </div>
              <p>
                {{ selectedExperiment.experiment_no }} · 实验员
                {{ selectedExperiment.owner_display_name }}
              </p>
            </div>
            <div class="basic-info-grid">
              <div>
                <span>实验类型</span>
                <strong>{{ selectedExperiment.experiment_type || "—" }}</strong>
              </div>
              <div>
                <span>预估开始时间</span>
                <strong>{{
                  formatDateTime(selectedExperiment.estimated_start)
                }}</strong>
              </div>
              <div>
                <span>预估结束时间</span>
                <strong>{{
                  formatDateTime(selectedExperiment.estimated_end)
                }}</strong>
              </div>
              <div class="purpose-field">
                <span>实验目的</span>
                <strong>{{ selectedExperiment.purpose || "—" }}</strong>
              </div>
            </div>
          </template>

          <div v-else class="basic-info-create">
            <label>
              <span>关联项目</span>
              <select v-model="editor.project_id">
                <option value="">请选择项目</option>
                <option
                  v-for="project in projects"
                  :key="project.id"
                  :value="project.id"
                >
                  {{ project.name }}
                </option>
              </select>
            </label>
            <label>
              <span>实验名称</span>
              <input
                v-model="editor.name"
                type="text"
                maxlength="200"
                autocomplete="off"
              />
            </label>
            <label>
              <span>实验类型</span>
              <select v-model="editor.experiment_type">
                <option value="">请选择实验类型</option>
                <option v-for="item in experimentTypes" :key="item" :value="item">
                  {{ item }}
                </option>
              </select>
            </label>
            <label>
              <span>预估开始时间</span>
              <input v-model="editor.estimated_start" type="datetime-local" />
            </label>
            <label>
              <span>预估结束时间</span>
              <input v-model="editor.estimated_end" type="datetime-local" />
            </label>
            <label class="purpose-field">
              <span>实验目的</span>
              <textarea v-model="editor.purpose" />
            </label>
          </div>
        </section>

        <section class="record-section">
          <header class="section-heading">
            <div>
              <h3>原料配方表</h3>
              <button
                class="module-add-button"
                type="button"
                :disabled="!canEdit"
                @click="addExtraTable"
              >
                <Icon icon="tabler:table-plus" />
                新增表格
              </button>
            </div>
            <div>
              <button
                class="text-action"
                type="button"
                :disabled="!canEdit"
                @click="addFormulaRow"
              >
                <Icon icon="tabler:row-insert-bottom" />
                添加行
              </button>
              <button
                class="text-action"
                type="button"
                :disabled="!canEdit"
                @click="addFormulaColumn"
              >
                <Icon icon="tabler:column-insert-right" />
                添加列
              </button>
            </div>
          </header>
          <div class="formula-wrap">
            <table class="formula-editor">
              <thead>
                <tr>
                  <th
                    v-for="(column, columnIndex) in editor.formula_columns"
                    :key="column.id"
                  >
                    <div class="formula-column-head">
                      <input
                        v-model="column.label"
                        class="column-name"
                        :disabled="!canEdit"
                        :aria-label="`第${columnIndex + 1}列名称`"
                      />
                      <button
                        type="button"
                        class="icon-action"
                        :disabled="!canEdit || editor.formula_columns.length === 1"
                        :aria-label="`删除${column.label || '当前'}列`"
                        @click="removeFormulaColumn(columnIndex)"
                      >
                        <Icon icon="tabler:trash" />
                      </button>
                    </div>
                  </th>
                  <th class="row-action-column">
                    <span class="sr-only">行操作</span>
                  </th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(row, rowIndex) in editor.formula_rows" :key="rowIndex">
                  <td v-for="column in editor.formula_columns" :key="column.id">
                    <input
                      v-model="row[column.id]"
                      :disabled="!canEdit"
                      :aria-label="`${column.label || '未命名列'} 第${rowIndex + 1}行`"
                    />
                  </td>
                  <td class="row-action-column">
                    <button
                      type="button"
                      class="icon-action"
                      :disabled="!canEdit || editor.formula_rows.length === 1"
                      :aria-label="`删除第${rowIndex + 1}行`"
                      @click="removeFormulaRow(rowIndex)"
                    >
                      <Icon icon="tabler:trash" />
                    </button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>

          <section
            v-for="(table, tableIndex) in editor.extra_tables"
            :key="table.id"
            class="extra-module"
          >
            <header>
              <input
                v-model="table.name"
                class="module-name"
                :disabled="!canEdit"
                aria-label="表格模块名称"
              />
              <div>
                <button
                  class="text-action"
                  type="button"
                  :disabled="!canEdit"
                  @click="addExtraTableRow(table)"
                >
                  <Icon icon="tabler:row-insert-bottom" />添加行
                </button>
                <button
                  class="text-action"
                  type="button"
                  :disabled="!canEdit"
                  @click="addExtraTableColumn(table)"
                >
                  <Icon icon="tabler:column-insert-right" />添加列
                </button>
                <button
                  class="icon-action danger"
                  type="button"
                  :disabled="!canEdit"
                  aria-label="删除表格模块"
                  @click="editor.extra_tables.splice(tableIndex, 1)"
                >
                  <Icon icon="tabler:trash" />
                </button>
              </div>
            </header>
            <div class="formula-wrap">
              <table class="formula-editor">
                <thead>
                  <tr>
                    <th
                      v-for="(column, columnIndex) in table.columns"
                      :key="column.id"
                    >
                      <div class="formula-column-head">
                        <input
                          v-model="column.label"
                          class="column-name"
                          :disabled="!canEdit"
                          placeholder="列名称"
                        />
                        <button
                          class="icon-action"
                          type="button"
                          :disabled="!canEdit || table.columns.length === 1"
                          aria-label="删除列"
                          @click="removeExtraTableColumn(table, columnIndex)"
                        >
                          <Icon icon="tabler:trash" />
                        </button>
                      </div>
                    </th>
                    <th class="row-action-column" />
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="(row, rowIndex) in table.rows" :key="rowIndex">
                    <td v-for="column in table.columns" :key="column.id">
                      <input v-model="row[column.id]" :disabled="!canEdit" />
                    </td>
                    <td class="row-action-column">
                      <button
                        class="icon-action"
                        type="button"
                        :disabled="!canEdit || table.rows.length === 1"
                        aria-label="删除行"
                        @click="table.rows.splice(rowIndex, 1)"
                      >
                        <Icon icon="tabler:trash" />
                      </button>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </section>
        </section>

        <section class="record-section process-section">
          <div class="process-copy">
            <div class="section-heading">
              <h3>实验过程</h3>
              <button
                class="module-add-button"
                type="button"
                :disabled="!canEdit"
                @click="addExtraProcess"
              >
                <Icon icon="tabler:text-plus" />
                新增
              </button>
            </div>
            <textarea
              v-model="editor.process_text"
              :disabled="!canEdit"
              placeholder="按时间记录实验操作、现象与异常信息"
              aria-label="实验过程文字记录"
            />
            <section
              v-for="(process, processIndex) in editor.extra_processes"
              :key="process.id"
              class="extra-process"
            >
              <header>
                <input
                  v-model="process.name"
                  class="module-name"
                  :disabled="!canEdit"
                  aria-label="过程模块名称"
                />
                <button
                  class="icon-action danger"
                  type="button"
                  :disabled="!canEdit"
                  aria-label="删除过程模块"
                  @click="editor.extra_processes.splice(processIndex, 1)"
                >
                  <Icon icon="tabler:trash" />
                </button>
              </header>
              <textarea
                v-model="process.content"
                :disabled="!canEdit"
                placeholder="输入实验过程内容"
              />
            </section>
          </div>

          <div class="process-media">
            <div>
              <div class="subheading">
                <h3>过程图片 <span>{{ editor.process_images.length }}</span></h3>
                <small>JPG、PNG，单张不超过 10 MB</small>
              </div>
              <div class="image-grid">
                <figure
                  v-for="image in editor.process_images"
                  :key="image.id ?? image.url"
                  class="image-item"
                >
                  <button
                    class="preview-button"
                    type="button"
                    :aria-label="`放大查看${image.name}`"
                    @click="previewImage = image"
                  >
                    <img :src="image.url" :alt="image.name" />
                  </button>
                  <span>{{ image.name }}</span>
                </figure>
              </div>
            </div>
            <label v-if="canEdit" class="upload-tile">
              <input
                type="file"
                accept="image/jpeg,image/png"
                multiple
                @change="addProcessImages"
              />
              <Icon icon="tabler:photo-plus" />
              <span>上传图片</span>
            </label>
          </div>
        </section>

        <section class="record-section result-section">
          <h3>实验结果</h3>
          <textarea
            v-model="editor.result_text"
            :disabled="!canEdit"
            maxlength="1000"
            placeholder="输入实验结论、关键现象及后续建议"
            aria-label="实验结果"
          />
          <span class="char-count">{{ editor.result_text.length }} / 1000</span>
          <div class="result-attachments">
            <div class="attachment-heading">
              <h4>结果附件 <span>{{ editor.result_files.length }}</span></h4>
              <small>支持文档、表格及图片</small>
            </div>
            <div class="file-list">
              <div
                v-for="file in editor.result_files"
                :key="file.id ?? file.name"
                class="file-chip"
              >
                <Icon icon="tabler:file-spreadsheet" />
                <a v-if="file.url" :href="file.url" :download="file.name">{{ file.name }}</a>
                <span v-else>{{ file.name }}</span>
                <small>{{ file.size }}</small>
              </div>
            </div>
            <label v-if="canEdit" class="upload-file">
              <input type="file" multiple @change="addResultFiles" />
              <Icon icon="tabler:paperclip" />
              上传附件
            </label>
          </div>
        </section>
      </div>

      <div v-else class="workspace-empty">
        <Icon icon="tabler:notebook-off" />
        <h2>当前筛选下暂无实验</h2>
        <p>请切换项目、实验状态或新增实验计划</p>
      </div>

      <footer
        v-if="(selectedExperiment || isCreating) && canEdit"
        class="record-actions"
      >
        <button
          class="button"
          type="button"
          :disabled="saving"
          @click="persistRecord(true)"
        >
          暂存
        </button>
        <button
          class="button primary"
          type="button"
          :disabled="saving"
          @click="persistRecord(false)"
        >
          <Icon
            :icon="
              selectedExperiment?.status === 'not_started'
                ? 'tabler:player-play'
                : 'tabler:device-floppy'
            "
          />
          {{ saving ? "处理中…" : primaryActionLabel }}
        </button>
      </footer>
    </article>

    <div
      v-if="previewImage"
      class="image-preview"
      role="dialog"
      aria-modal="true"
      aria-label="过程图片预览"
      @click.self="previewImage = null"
    >
      <button
        class="preview-close"
        type="button"
        aria-label="关闭图片预览"
        @click="previewImage = null"
      >
        <Icon icon="tabler:x" />
      </button>
      <figure>
        <img :src="previewImage.url" :alt="previewImage.name" />
        <figcaption>{{ previewImage.name }}</figcaption>
      </figure>
    </div>
  </section>
</template>

<style scoped>
.eln-page {
  --list-width: 495px;
  display: grid;
  grid-template-columns: var(--list-width) 4px minmax(0, 1fr);
  width: 100%;
  height: 100%;
  min-height: 0;
  overflow: hidden;
  background: #f5f7fb;
}

.eln-page.is-resizing {
  cursor: col-resize;
  user-select: none;
}

.experiment-list-panel {
  z-index: 4;
  display: flex;
  min-width: 390px;
  min-height: 0;
  background: #f5f7fb;
  border-right: 1px solid var(--color-rule);
}

.experiment-list-card {
  position: relative;
  display: flex;
  min-width: 0;
  min-height: 0;
  flex: 1;
  flex-direction: column;
  margin: 12px 6px 12px 0;
  overflow: visible;
  background: var(--color-paper);
  border: 1px solid var(--color-rule);
  border-radius: 9px;
}

.plan-actions {
  display: grid;
  height: 62px;
  flex: 0 0 62px;
  grid-template-columns: 1fr 1fr;
  align-items: center;
  padding: 10px 14px 8px;
  gap: 10px;
}

.button {
  display: inline-flex;
  height: 38px;
  align-items: center;
  justify-content: center;
  padding: 0 14px;
  color: var(--color-ink-2);
  background: var(--color-paper);
  border: 1px solid var(--color-rule-2);
  border-radius: 6px;
  cursor: pointer;
  gap: 7px;
  white-space: nowrap;
}

.button:hover:not(:disabled) {
  background: var(--color-paper-3);
}

.button.primary {
  color: #fff;
  background: var(--color-accent);
  border-color: var(--color-accent);
}

.button.primary:hover:not(:disabled) {
  background: #2563eb;
}

.button:disabled,
.text-action:disabled,
.module-add-button:disabled,
.icon-action:disabled {
  cursor: not-allowed;
  opacity: 0.45;
}

.copy-plan-control {
  position: relative;
}

.copy-plan-button {
  width: 100%;
}

.copy-chevron {
  margin-left: auto;
}

.copy-plan-dropdown {
  position: absolute;
  z-index: 80;
  top: calc(100% + 2px);
  left: 0;
  display: flex;
  width: min(560px, calc(100vw - 100px));
  height: min(520px, calc(100vh - 150px));
  flex-direction: column;
  padding-top: 12px;
  overflow: hidden;
  background: var(--color-paper);
  border: 1px solid var(--color-rule-2);
  border-radius: 0 0 8px 8px;
  box-shadow: 0 16px 42px rgb(15 23 42 / 16%);
}

.dropdown-search,
.combined-filter {
  display: flex;
  align-items: center;
  color: var(--color-ink-4);
  background: var(--color-paper);
  border: 1px solid var(--color-rule-2);
  border-radius: 7px;
  gap: 8px;
}

.dropdown-search {
  height: 38px;
  margin: 0 12px 8px;
  padding: 0 11px;
}

.dropdown-search input,
.combined-filter input {
  width: 100%;
  min-width: 0;
  color: var(--color-ink);
  background: transparent;
  border: 0;
  outline: 0;
}

.copy-plan-list {
  min-height: 0;
  flex: 1;
  padding: 4px 12px;
  overflow: auto;
}

.copy-plan-item {
  display: grid;
  width: 100%;
  min-height: 70px;
  grid-template-columns: 18px minmax(0, 1fr) auto;
  align-items: center;
  padding: 10px 12px;
  color: var(--color-ink);
  text-align: left;
  background: transparent;
  border: 0;
  border-radius: 8px;
  cursor: pointer;
  gap: 12px;
}

.copy-plan-item:hover,
.copy-plan-item.selected {
  background: var(--color-accent-soft);
}

.copy-radio {
  width: 14px;
  height: 14px;
  border: 1px solid var(--color-rule-2);
  border-radius: 50%;
}

.copy-plan-item.selected .copy-radio {
  border: 4px solid var(--color-accent);
}

.copy-main {
  min-width: 0;
}

.copy-main strong,
.copy-main small {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.copy-main small {
  margin-top: 5px;
  color: var(--color-ink-4);
  font-size: 12px;
}

.copy-plan-dropdown > footer {
  display: flex;
  height: 56px;
  flex: 0 0 56px;
  align-items: center;
  justify-content: flex-end;
  padding: 8px 12px;
  border-top: 1px solid var(--color-rule);
  gap: 10px;
}

.combined-filter {
  position: relative;
  z-index: 50;
  height: 44px;
  flex: 0 0 44px;
  margin: 0 14px;
  padding: 0 10px;
}

.combined-filter:focus-within,
.dropdown-search:focus-within {
  border-color: var(--color-focus);
  box-shadow: 0 0 0 3px rgb(37 99 235 / 10%);
}

.project-filter-trigger {
  display: flex;
  width: 100%;
  min-width: 0;
  align-items: center;
  gap: 8px;
}

.project-options {
  position: absolute;
  z-index: 90;
  top: calc(100% + 1px);
  right: -1px;
  left: -1px;
  max-height: 330px;
  padding: 5px;
  overflow: auto;
  background: var(--color-paper);
  border: 1px solid var(--color-rule-2);
  border-radius: 0 0 7px 7px;
  box-shadow: 0 14px 32px rgb(15 23 42 / 14%);
}

.project-option {
  display: grid;
  width: 100%;
  min-height: 38px;
  grid-template-columns: 17px minmax(0, 1fr) auto;
  align-items: center;
  padding: 6px 9px;
  color: var(--color-ink-2);
  text-align: left;
  background: transparent;
  border: 0;
  border-left: 3px solid transparent;
  border-radius: 5px;
  cursor: pointer;
  gap: 6px;
}

.project-option svg {
  visibility: hidden;
}

.project-option small {
  color: var(--color-ink-4);
  font-size: 11px;
}

.project-option:hover {
  background: var(--color-paper-3);
}

.project-option.selected {
  color: var(--color-accent);
  background: var(--color-accent-soft);
  border-left-color: var(--color-accent);
}

.project-option.selected svg {
  visibility: visible;
}

.record-tabs {
  display: grid;
  height: 52px;
  flex: 0 0 52px;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  margin: 0 14px;
  border-bottom: 1px solid var(--color-rule);
}

.record-tabs button {
  position: relative;
  color: var(--color-ink-4);
  background: transparent;
  border: 0;
  cursor: pointer;
  white-space: nowrap;
}

.record-tabs button.active {
  color: var(--color-accent);
  font-weight: 650;
}

.record-tabs button.active::after {
  position: absolute;
  right: 0;
  bottom: -1px;
  left: 0;
  height: 2px;
  background: var(--color-accent);
  content: "";
}

.record-tabs b {
  font-weight: inherit;
}

.experiment-list {
  min-height: 0;
  flex: 1;
  overflow: auto;
  border-radius: 0 0 8px 8px;
}

.experiment-row {
  position: relative;
  width: 100%;
  min-height: 126px;
  padding: 15px 17px;
  color: var(--color-ink);
  text-align: left;
  background: var(--color-paper);
  border: 0;
  border-bottom: 1px solid var(--color-rule);
  cursor: pointer;
}

.experiment-row:hover {
  background: #f8fafc;
}

.experiment-row.selected {
  background: var(--color-accent-soft);
}

.experiment-row.selected::before {
  position: absolute;
  inset-block: 0;
  left: 0;
  width: 3px;
  background: var(--color-accent);
  content: "";
}

.experiment-row strong,
.experiment-row small {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.experiment-row strong {
  font-size: 15px;
  line-height: 1.45;
}

.experiment-row small {
  margin-top: 5px;
  color: var(--color-ink-4);
  font-size: 12px;
}

.experiment-row footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 10px;
}

.experiment-row time {
  color: var(--color-ink-4);
  font-size: 12px;
}

.status-chip {
  display: inline-flex;
  align-items: center;
  padding: 3px 8px;
  color: #238548;
  font-size: 12px;
  background: #eaf8ed;
  border-radius: 5px;
}

.status-chip.not_started {
  color: #a16207;
  background: #fff7db;
}

.status-chip.completed {
  color: var(--color-ink-4);
  background: var(--color-paper-3);
}

.list-empty,
.workspace-empty {
  display: flex;
  min-height: 240px;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  color: var(--color-ink-4);
}

.list-empty svg,
.workspace-empty svg {
  width: 34px;
  height: 34px;
}

.list-empty p {
  margin: 10px 0;
}

.layout-resizer {
  cursor: col-resize;
  background: transparent;
  touch-action: none;
}

.layout-resizer:hover {
  background: rgb(37 99 235 / 10%);
}

.record-workspace {
  position: relative;
  display: flex;
  min-width: 0;
  min-height: 0;
  flex-direction: column;
  padding: 10px 6px 12px;
  overflow: hidden;
  background: #f5f7fb;
}

.record-scroll {
  min-height: 0;
  flex: 1;
  padding: 0 0 82px 12px;
  overflow: auto;
  overscroll-behavior: contain;
}

.record-section {
  margin-bottom: 10px;
  padding: 18px;
  background: var(--color-paper);
  border: 1px solid var(--color-rule);
  border-radius: 8px;
}

.record-section h3,
.record-section h4 {
  margin: 0;
  color: var(--color-ink);
}

.record-section h3 {
  font-size: 16px;
}

.record-summary {
  margin-top: 15px;
  padding-bottom: 14px;
  border-bottom: 1px solid var(--color-rule);
}

.record-title {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 10px;
}

.record-title h2 {
  overflow: hidden;
  margin: 0;
  font-size: 20px;
  font-weight: 700;
  letter-spacing: -0.01em;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.record-summary p {
  margin: 6px 0 0;
  color: var(--color-ink-4);
  font-size: 13px;
}

.basic-info-grid,
.basic-info-create {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  margin-top: 16px;
  gap: 14px 18px;
}

.basic-info-grid > div,
.basic-info-create label {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 5px;
}

.basic-info-grid span,
.basic-info-create label > span {
  color: var(--color-ink-4);
  font-size: 12px;
  font-weight: 600;
}

.basic-info-grid strong {
  color: var(--color-ink);
  font-size: 14px;
  font-weight: 520;
  line-height: 1.55;
  overflow-wrap: anywhere;
}

.purpose-field {
  grid-column: 1 / -1;
}

.basic-info-create input,
.basic-info-create select,
.basic-info-create textarea,
.formula-editor input,
.process-copy textarea,
.result-section > textarea,
.module-name,
.extra-process textarea {
  width: 100%;
  color: var(--color-ink);
  background: #f7f9fc;
  border: 1px solid #dce3ee;
  border-radius: 6px;
  outline: 0;
}

.basic-info-create input,
.basic-info-create select {
  height: 36px;
  padding: 0 10px;
}

.basic-info-create textarea {
  min-height: 68px;
  padding: 9px 10px;
  resize: vertical;
}

.basic-info-create :is(input, select, textarea):focus,
.formula-editor input:focus,
.process-copy textarea:focus,
.result-section > textarea:focus,
.module-name:focus,
.extra-process textarea:focus {
  background: var(--color-paper);
  border-color: var(--color-accent);
  box-shadow: 0 0 0 3px rgb(37 99 235 / 10%);
}

.section-heading,
.section-heading > div,
.extra-module > header,
.extra-process > header,
.subheading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.module-add-button,
.text-action,
.icon-action {
  display: inline-flex;
  align-items: center;
  color: var(--color-accent);
  background: transparent;
  border: 0;
  border-radius: 5px;
  cursor: pointer;
  gap: 5px;
}

.module-add-button {
  height: 30px;
  padding: 0 9px;
  background: var(--color-accent-soft);
  border: 1px solid rgb(37 99 235 / 18%);
}

.text-action {
  height: 30px;
  padding: 0 6px;
}

.text-action:hover,
.icon-action:hover:not(:disabled) {
  background: var(--color-paper-3);
}

.icon-action {
  width: 30px;
  height: 30px;
  justify-content: center;
  padding: 0;
  color: var(--color-ink-4);
}

.icon-action.danger:hover:not(:disabled) {
  color: #dc2626;
  background: #fff1f2;
}

.formula-wrap {
  margin-top: 13px;
  overflow-x: auto;
  border: 1px solid var(--color-rule);
  border-radius: 7px;
}

.formula-editor {
  width: 100%;
  min-width: 700px;
  border-spacing: 0;
  border-collapse: collapse;
}

.formula-editor th {
  min-width: 180px;
  padding: 6px 8px;
  color: var(--color-ink-3);
  background: #f6f8fc;
  border-bottom: 1px solid var(--color-rule);
}

.formula-editor td {
  padding: 5px 7px;
  border-bottom: 1px solid var(--color-rule);
}

.formula-editor tr:last-child td {
  border-bottom: 0;
}

.formula-editor input {
  height: 38px;
  padding: 0 10px;
}

.formula-editor input:disabled,
.process-copy textarea:disabled,
.result-section > textarea:disabled {
  color: var(--color-ink);
  opacity: 1;
}

.formula-column-head {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 30px;
  align-items: center;
  gap: 5px;
}

.formula-editor .column-name {
  height: 30px;
  padding: 0 5px;
  font-size: 12px;
  font-weight: 650;
  background: transparent;
  border-color: transparent;
}

.formula-editor .row-action-column {
  width: 46px;
  min-width: 46px;
  text-align: center;
}

.extra-module,
.extra-process {
  margin-top: 14px;
  padding-top: 14px;
  border-top: 1px dashed var(--color-rule-2);
}

.module-name {
  width: min(320px, 50%);
  height: 34px;
  padding: 0 9px;
  font-weight: 600;
}

.process-copy > .section-heading {
  margin-bottom: 13px;
}

.process-copy > textarea,
.extra-process textarea,
.result-section > textarea {
  min-height: 124px;
  padding: 12px 13px;
  line-height: 1.75;
  resize: vertical;
}

.extra-process header {
  margin-bottom: 8px;
}

.process-media {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 130px;
  margin-top: 16px;
  gap: 12px;
}

.subheading h3 span,
.attachment-heading h4 span {
  color: var(--color-accent);
}

.subheading small,
.attachment-heading small {
  color: var(--color-ink-4);
  font-size: 12px;
}

.image-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  margin-top: 10px;
  gap: 10px;
}

.image-item {
  position: relative;
  min-width: 0;
  margin: 0;
  overflow: hidden;
  background: #f7f9fc;
  border: 1px solid var(--color-rule);
  border-radius: 7px;
}

.preview-button {
  display: block;
  width: 100%;
  padding: 0;
  background: transparent;
  border: 0;
  cursor: zoom-in;
}

.image-item img {
  display: block;
  width: 100%;
  height: 112px;
  object-fit: cover;
}

.image-item > span {
  display: block;
  overflow: hidden;
  padding: 7px 9px;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.remove-media {
  position: absolute;
  top: 7px;
  right: 7px;
  display: grid;
  width: 25px;
  height: 25px;
  padding: 0;
  color: #fff;
  background: rgb(15 23 42 / 72%);
  border: 0;
  border-radius: 50%;
  cursor: pointer;
  place-items: center;
}

.upload-tile {
  display: flex;
  height: 142px;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  color: var(--color-accent);
  background: #f8faff;
  border: 1px dashed rgb(37 99 235 / 42%);
  border-radius: 7px;
  cursor: pointer;
  gap: 7px;
}

.upload-tile svg {
  width: 24px;
  height: 24px;
}

.upload-tile input,
.upload-file input {
  display: none;
}

.result-section {
  position: relative;
}

.result-section > textarea {
  margin-top: 13px;
}

.char-count {
  display: block;
  margin-top: 5px;
  color: var(--color-ink-4);
  font-size: 12px;
  text-align: right;
}

.result-attachments {
  display: grid;
  grid-template-columns: 150px minmax(0, 1fr) 130px;
  align-items: center;
  margin-top: 14px;
  padding-top: 14px;
  border-top: 1px solid var(--color-rule);
  gap: 14px;
}

.attachment-heading h4 {
  font-size: 14px;
}

.attachment-heading small {
  display: block;
  margin-top: 4px;
}

.file-list {
  display: flex;
  min-width: 0;
  flex-wrap: wrap;
  gap: 8px;
}

.file-chip {
  display: grid;
  min-width: 240px;
  max-width: 360px;
  height: 46px;
  grid-template-columns: 20px minmax(0, 1fr) auto 24px;
  align-items: center;
  padding: 0 9px;
  background: #f7f9fc;
  border: 1px solid var(--color-rule);
  border-radius: 7px;
  gap: 7px;
}

.file-chip span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-chip small {
  color: var(--color-ink-4);
  font-size: 12px;
}

.file-chip button {
  display: grid;
  width: 24px;
  height: 24px;
  padding: 0;
  color: var(--color-ink-4);
  background: transparent;
  border: 0;
  cursor: pointer;
  place-items: center;
}

.upload-file {
  display: inline-flex;
  height: 38px;
  align-items: center;
  justify-content: center;
  color: var(--color-accent);
  background: #f8faff;
  border: 1px solid rgb(37 99 235 / 25%);
  border-radius: 6px;
  cursor: pointer;
  gap: 6px;
}

.record-actions {
  position: absolute;
  z-index: 20;
  right: 16px;
  bottom: 14px;
  display: flex;
  padding: 8px;
  background: rgb(255 255 255 / 92%);
  border: 1px solid var(--color-rule);
  border-radius: 9px;
  box-shadow: 0 8px 24px rgb(15 23 42 / 10%);
  backdrop-filter: blur(8px);
  gap: 9px;
}

.workspace-empty {
  min-height: 100%;
}

.workspace-empty h2 {
  margin: 14px 0 4px;
  font-size: 18px;
}

.workspace-empty p {
  margin: 0;
}

.image-preview {
  position: fixed;
  z-index: 1300;
  inset: 0;
  display: grid;
  padding: 48px;
  background: rgb(15 23 42 / 70%);
  backdrop-filter: blur(6px);
  place-items: center;
}

.image-preview figure {
  display: flex;
  max-width: min(980px, calc(100vw - 96px));
  max-height: calc(100vh - 96px);
  flex-direction: column;
  margin: 0;
  gap: 10px;
}

.image-preview img {
  max-width: 100%;
  max-height: calc(100vh - 150px);
  object-fit: contain;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 18px 60px rgb(0 0 0 / 35%);
}

.image-preview figcaption {
  color: #fff;
  text-align: center;
}

.preview-close {
  position: absolute;
  top: 24px;
  right: 24px;
  display: grid;
  width: 38px;
  height: 38px;
  padding: 0;
  color: var(--color-ink);
  background: #fff;
  border: 0;
  border-radius: 50%;
  cursor: pointer;
  place-items: center;
}

.copy-empty,
.filter-empty {
  padding: 28px 12px;
  color: var(--color-ink-4);
  text-align: center;
}

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}

@media (max-width: 1280px) {
  .eln-page {
    --list-width: 435px;
  }

  .basic-info-grid,
  .basic-info-create {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .result-attachments {
    grid-template-columns: 1fr;
  }

  .upload-file {
    width: 150px;
  }
}
</style>
