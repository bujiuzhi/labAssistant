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
} from "vue";
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
const experimentTypes = ["单体", "聚合", "其他"];
const LIST_MIN_WIDTH = 390;
const LIST_MAX_WIDTH = 630;

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
const attachmentBusyId = ref("");
const listWidth = ref(390);
const resizing = ref(false);
const recordScroll = ref<HTMLElement | null>(null);
const copyPlanControl = ref<HTMLElement | null>(null);
const copyPlanTrigger = ref<HTMLButtonElement | null>(null);
const copyPlanSearchInput = ref<HTMLInputElement | null>(null);
const projectFilter = ref<HTMLElement | null>(null);
const projectFilterTrigger = ref<HTMLInputElement | null>(null);
const projectOptionList = ref<HTMLElement | null>(null);
const experimentNameInput = ref<HTMLInputElement | null>(null);
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
    (sessionStore.hasPermission("experiment.update") &&
      selectedExperiment.value?.can_edit === true),
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
  closeProjectFilter();
  isCreating.value = false;
  selectFirstVisible();
}

function updateProjectSearch(event: Event): void {
  projectSearch.value = (event.target as HTMLInputElement).value;
}

/**
 * 打开项目筛选下拉并按需将焦点移入当前选项
 *
 * @param focusOption 是否聚焦当前选项或首个选项
 */
function openProjectFilter(focusOption = false): void {
  closeCopyPanel();
  projectSearch.value = "";
  projectFilterOpen.value = true;
  void nextTick(() => {
    if (focusOption) {
      focusSelectedProjectOption();
      return;
    }
    projectFilterTrigger.value?.focus();
    projectFilterTrigger.value?.select();
  });
}

/**
 * 关闭项目筛选下拉
 *
 * @param returnFocus 是否把焦点还给筛选输入框
 */
function closeProjectFilter(returnFocus = false): void {
  projectFilterOpen.value = false;
  projectSearch.value = "";
  if (returnFocus) {
    void nextTick(() => projectFilterTrigger.value?.focus());
  }
}

/** 聚焦已选择的项目选项，未选择时聚焦第一个选项。 */
function focusSelectedProjectOption(): void {
  const options = Array.from(
    projectOptionList.value?.querySelectorAll<HTMLButtonElement>('[role="option"]') ??
      [],
  );
  const selected =
    options.find((option) => option.getAttribute("aria-selected") === "true") ??
    options[0];
  selected?.focus();
}

/**
 * 处理项目筛选输入框的键盘操作
 *
 * @param event 键盘事件
 */
function handleProjectFilterKeydown(event: KeyboardEvent): void {
  if (event.key === "ArrowDown" || event.key === "ArrowUp") {
    event.preventDefault();
    if (!projectFilterOpen.value) {
      openProjectFilter(true);
    } else {
      focusSelectedProjectOption();
    }
    return;
  }
  if (event.key === "Enter" && projectFilterOpen.value) {
    const options = projectOptions.value;
    if (options.length === 1) {
      event.preventDefault();
      selectProject(options[0].id);
    }
    return;
  }
  if (event.key === "Escape" && projectFilterOpen.value) {
    event.preventDefault();
    closeProjectFilter(true);
  }
}

/**
 * 处理项目筛选选项的方向键、首尾键和确认键
 *
 * @param event 键盘事件
 * @param projectId 当前选项的项目 ID
 */
function handleProjectOptionKeydown(
  event: KeyboardEvent,
  projectId: string,
): void {
  const options = Array.from(
    projectOptionList.value?.querySelectorAll<HTMLButtonElement>('[role="option"]') ??
      [],
  );
  const current = event.currentTarget as HTMLButtonElement;
  const currentIndex = options.indexOf(current);
  if (event.key === "ArrowDown" || event.key === "ArrowUp") {
    event.preventDefault();
    const direction = event.key === "ArrowDown" ? 1 : -1;
    const nextIndex = Math.min(
      options.length - 1,
      Math.max(0, currentIndex + direction),
    );
    options[nextIndex]?.focus();
    return;
  }
  if (event.key === "Home" || event.key === "End") {
    event.preventDefault();
    options[event.key === "Home" ? 0 : options.length - 1]?.focus();
    return;
  }
  if (event.key === "Enter" || event.key === " ") {
    event.preventDefault();
    selectProject(projectId);
    return;
  }
  if (event.key === "Escape") {
    event.preventDefault();
    closeProjectFilter(true);
  }
}

/** 打开复制计划下拉并聚焦搜索框。 */
function openCopyPanel(): void {
  closeProjectFilter();
  copySearch.value = "";
  copySelectedId.value = "";
  copyPanelOpen.value = true;
  void nextTick(() => copyPlanSearchInput.value?.focus());
}

/**
 * 关闭复制计划下拉
 *
 * @param returnFocus 是否把焦点还给复制计划按钮
 */
function closeCopyPanel(returnFocus = false): void {
  copyPanelOpen.value = false;
  if (returnFocus) {
    void nextTick(() => copyPlanTrigger.value?.focus());
  }
}

/** 切换复制计划下拉。 */
function toggleCopyPanel(): void {
  if (copyPanelOpen.value) {
    closeCopyPanel();
    return;
  }
  openCopyPanel();
}

/**
 * 处理复制计划按钮的方向键与 Esc
 *
 * @param event 键盘事件
 */
function handleCopyTriggerKeydown(event: KeyboardEvent): void {
  if (event.key === "ArrowDown" || event.key === "ArrowUp") {
    event.preventDefault();
    if (!copyPanelOpen.value) openCopyPanel();
    else copyPlanSearchInput.value?.focus();
    return;
  }
  if (event.key === "Escape" && copyPanelOpen.value) {
    event.preventDefault();
    closeCopyPanel(true);
  }
}

/**
 * 点击两个下拉区域外部时关闭浮层
 *
 * @param event 鼠标事件
 */
function handleDocumentClick(event: MouseEvent): void {
  const target = event.target;
  if (!(target instanceof Node)) return;
  if (
    projectFilterOpen.value &&
    !projectFilter.value?.contains(target)
  ) {
    closeProjectFilter();
  }
  if (copyPanelOpen.value && !copyPlanControl.value?.contains(target)) {
    closeCopyPanel();
  }
}

/**
 * 处理页面级 Esc 操作
 *
 * @param event 键盘事件
 */
function handleDocumentKeydown(event: KeyboardEvent): void {
  if (event.key === "Escape" && previewImage.value) {
    previewImage.value = null;
  }
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
  closeCopyPanel();
  copySearch.value = "";
  copySelectedId.value = "";
  void nextTick(() => {
    if (recordScroll.value) recordScroll.value.scrollTop = 0;
    experimentNameInput.value?.focus();
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
  let succeeded = 0;
  const failures: string[] = [];
  for (const file of files) {
    if (
      !["image/jpeg", "image/png"].includes(file.type) ||
      file.size > 10 * 1024 * 1024
    ) {
      failures.push(`${file.name}：仅支持 10 MB 以内的 JPG、PNG`);
      continue;
    }
    try {
      const updated = await experimentApi.uploadAttachment(current.experiment_no, file, "process_image");
      replaceExperiment(updated);
      replaceEditor(recordToEditor(updated));
      succeeded += 1;
    } catch (error) {
      const problem = getProblemDetail(error);
      failures.push(`${file.name}：${problem?.detail ?? "上传失败"}`);
    }
  }
  input.value = "";
  if (succeeded) ElMessage.success(`已上传 ${succeeded} 张过程图片`);
  failures.forEach((message) => ElMessage.warning(message));
}

async function addResultFiles(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement;
  const current = selectedExperiment.value;
  if (!current) {
    ElMessage.warning("请先创建实验计划后再上传真实结果附件");
    input.value = "";
    return;
  }
  let succeeded = 0;
  const failures: string[] = [];
  for (const file of Array.from(input.files ?? [])) {
    if (file.size > 25 * 1024 * 1024) {
      failures.push(`${file.name}：超过 25 MB`);
      continue;
    }
    try {
      const updated = await experimentApi.uploadAttachment(current.experiment_no, file, "result_file");
      replaceExperiment(updated);
      replaceEditor(recordToEditor(updated));
      succeeded += 1;
    } catch (error) {
      const problem = getProblemDetail(error);
      failures.push(`${file.name}：${problem?.detail ?? "上传失败"}`);
    }
  }
  input.value = "";
  if (succeeded) ElMessage.success(`已上传 ${succeeded} 个结果附件`);
  failures.forEach((message) => ElMessage.warning(message));
}

async function removeAttachment(
  attachment: ProcessImage | ResultFile,
): Promise<void> {
  const current = selectedExperiment.value;
  if (!current || !attachment.id || attachmentBusyId.value) return;
  attachmentBusyId.value = attachment.id;
  try {
    const updated = await experimentApi.deleteAttachment(
      current.experiment_no,
      attachment.id,
    );
    replaceExperiment(updated);
    replaceEditor(recordToEditor(updated));
    ElMessage.success(`已删除“${attachment.name}”`);
  } catch (error) {
    const problem = getProblemDetail(error);
    ElMessage.error(problem?.detail ?? "附件删除失败");
  } finally {
    attachmentBusyId.value = "";
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
  if (!editor.purpose.trim()) {
    ElMessage.warning("请输入实验目的");
    return false;
  }
  if (
    editor.extra_processes.some(
      (process) => process.content.length > 1000,
    )
  ) {
    ElMessage.warning("单个新增实验过程不得超过 1000 字");
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

/** 保存当前记录后显式完成实验，完成前必须填写实验结果。 */
async function completeExperiment(): Promise<void> {
  const current = selectedExperiment.value;
  if (
    !current ||
    current.status !== "in_progress" ||
    saving.value ||
    !validateEditor()
  ) {
    return;
  }
  if (!editor.result_text.trim()) {
    ElMessage.warning("完成实验前必须填写实验结果");
    return;
  }
  saving.value = true;
  try {
    const updated = await experimentApi.update(
      current.experiment_no,
      current.version,
      payloadFromEditor(),
    );
    const completed = await experimentApi.transition(
      updated.experiment_no,
      updated.version,
      "completed",
    );
    replaceExperiment(completed);
    activeStatus.value = "completed";
    selectExperiment(completed);
    ElMessage.success("实验已完成，记录仍可继续保存修订");
  } catch (error) {
    const problem = getProblemDetail(error);
    ElMessage.error(problem?.detail ?? "实验完成失败");
  } finally {
    saving.value = false;
  }
}

function beginResize(event: PointerEvent): void {
  if (event.button !== 0) return;
  event.preventDefault();
  resizing.value = true;
  resizerStartX = event.clientX;
  resizerStartWidth = listWidth.value;
  window.addEventListener("pointermove", resizeList);
  window.addEventListener("pointerup", finishResize, { once: true });
  window.addEventListener("pointercancel", finishResize, { once: true });
}

function resizeList(event: PointerEvent): void {
  if (!resizing.value) return;
  listWidth.value = Math.min(
    LIST_MAX_WIDTH,
    Math.max(
      LIST_MIN_WIDTH,
      resizerStartWidth + event.clientX - resizerStartX,
    ),
  );
}

function finishResize(): void {
  resizing.value = false;
  window.removeEventListener("pointermove", resizeList);
  window.removeEventListener("pointerup", finishResize);
  window.removeEventListener("pointercancel", finishResize);
}

/** 使用键盘调整实验列表宽度。 */
function resizeListByKeyboard(event: KeyboardEvent): void {
  const step = event.shiftKey ? 40 : 10;
  let nextWidth = listWidth.value;
  if (event.key === "ArrowLeft") nextWidth -= step;
  else if (event.key === "ArrowRight") nextWidth += step;
  else if (event.key === "Home") nextWidth = LIST_MIN_WIDTH;
  else if (event.key === "End") nextWidth = LIST_MAX_WIDTH;
  else return;

  event.preventDefault();
  listWidth.value = Math.min(
    LIST_MAX_WIDTH,
    Math.max(LIST_MIN_WIDTH, nextWidth),
  );
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
    const requestedProject = String(route.query.project ?? "");
    if (requestedProject && projects.value.some((item) => item.id === requestedProject)) {
      selectedProjectId.value = requestedProject;
    }
    if (route.query.create === "1") {
      startCreate();
      return;
    }
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

onMounted(() => {
  document.addEventListener("click", handleDocumentClick);
  document.addEventListener("keydown", handleDocumentKeydown);
  void loadPage();
});
onBeforeUnmount(() => {
  document.removeEventListener("click", handleDocumentClick);
  document.removeEventListener("keydown", handleDocumentKeydown);
  window.removeEventListener("pointermove", resizeList);
  window.removeEventListener("pointerup", finishResize);
  window.removeEventListener("pointercancel", finishResize);
});
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
          <div ref="copyPlanControl" class="copy-plan-control">
            <button
              ref="copyPlanTrigger"
              class="ui-button ui-button--secondary button copy-plan-button"
              type="button"
              aria-haspopup="listbox"
              :aria-expanded="copyPanelOpen"
              aria-controls="copy-plan-dropdown"
              @click="toggleCopyPanel"
              @keydown="handleCopyTriggerKeydown"
            >
              <Icon icon="tabler:copy" />
              复制计划
              <Icon class="copy-chevron" icon="tabler:chevron-down" />
            </button>
            <div
              v-if="copyPanelOpen"
              id="copy-plan-dropdown"
              class="copy-plan-dropdown"
            >
              <label class="dropdown-search">
                <Icon icon="tabler:search" />
                <input
                  ref="copyPlanSearchInput"
                  v-model="copySearch"
                  type="search"
                  placeholder="搜索实验名称"
                  autocomplete="off"
                  @keydown.esc.stop.prevent="closeCopyPanel(true)"
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
                  class="ui-button ui-button--secondary button"
                  type="button"
                  @click="closeCopyPanel(true)"
                >
                  取消
                </button>
                <button
                  class="ui-button ui-button--primary button primary"
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
          <button
            class="ui-button ui-button--primary button primary"
            type="button"
            @click="startCreate()"
          >
            <Icon icon="tabler:plus" />
            新增计划
          </button>
        </header>

        <div ref="projectFilter" class="combined-filter">
          <label class="project-filter-trigger">
            <Icon icon="tabler:search" />
            <input
              ref="projectFilterTrigger"
              :value="projectFilterOpen ? projectSearch : selectedProjectName"
              type="text"
              placeholder="搜索项目名称或编号"
              autocomplete="off"
              role="combobox"
              :aria-label="`项目筛选，当前：${selectedProjectName}`"
              aria-haspopup="listbox"
              :aria-expanded="projectFilterOpen"
              aria-controls="project-filter-options"
              @click="projectFilterOpen ? projectFilterTrigger?.select() : openProjectFilter()"
              @input="updateProjectSearch"
              @keydown="handleProjectFilterKeydown"
            />
          </label>
          <div
            v-if="projectFilterOpen"
            id="project-filter-options"
            ref="projectOptionList"
            class="project-options"
            role="listbox"
            aria-label="按项目筛选实验"
          >
            <button
              v-for="project in projectOptions"
              :key="project.id || 'all'"
              class="project-option"
              :class="{ selected: selectedProjectId === project.id }"
              type="button"
              role="option"
              :aria-selected="selectedProjectId === project.id"
              @mousedown.prevent="selectProject(project.id)"
              @keydown="handleProjectOptionKeydown($event, project.id)"
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
        <div
          class="layout-resizer"
          role="separator"
          tabindex="0"
          aria-label="调整实验列表宽度"
          aria-orientation="vertical"
          :aria-valuemin="LIST_MIN_WIDTH"
          :aria-valuemax="LIST_MAX_WIDTH"
          :aria-valuenow="listWidth"
          :aria-valuetext="`${listWidth} 像素`"
          @pointerdown="beginResize"
          @keydown="resizeListByKeyboard"
        />
      </section>
    </aside>

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
                ref="experimentNameInput"
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
                class="ui-button ui-button--light module-add-button"
                type="button"
                :disabled="!canEdit"
                @click="addExtraTable"
              >
                <Icon icon="tabler:table-plus" />
                新增
              </button>
            </div>
            <div>
              <button
                class="ui-button ui-button--light text-action"
                type="button"
                :disabled="!canEdit"
                @click="addFormulaRow"
              >
                <Icon icon="tabler:row-insert-bottom" />
                添加行
              </button>
              <button
                class="ui-button ui-button--light text-action"
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
                        class="ui-button ui-button--danger ui-button--icon icon-action"
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
                      class="ui-button ui-button--danger ui-button--icon icon-action"
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
                  class="ui-button ui-button--light text-action"
                  type="button"
                  :disabled="!canEdit"
                  @click="addExtraTableRow(table)"
                >
                  <Icon icon="tabler:row-insert-bottom" />添加行
                </button>
                <button
                  class="ui-button ui-button--light text-action"
                  type="button"
                  :disabled="!canEdit"
                  @click="addExtraTableColumn(table)"
                >
                  <Icon icon="tabler:column-insert-right" />添加列
                </button>
                <button
                  class="ui-button ui-button--danger ui-button--icon icon-action danger"
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
                          class="ui-button ui-button--danger ui-button--icon icon-action"
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
                        class="ui-button ui-button--danger ui-button--icon icon-action"
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
                class="ui-button ui-button--light module-add-button"
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
                  class="ui-button ui-button--danger ui-button--icon icon-action danger"
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
                maxlength="1000"
                placeholder="输入实验过程内容"
              />
              <small class="process-char-count">
                {{ process.content.length }} / 1000
              </small>
            </section>
          </div>

          <div class="process-media">
            <div>
              <div class="subheading">
                <h3>过程图片 <span>{{ editor.process_images.length }}</span></h3>
                <label v-if="canEdit" class="inline-upload-action">
                  <input
                    type="file"
                    accept="image/jpeg,image/png"
                    multiple
                    @change="addProcessImages"
                  />
                  <Icon icon="tabler:photo-plus" />
                  上传图片
                </label>
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
                  <button
                    v-if="canEdit && image.id"
                    class="ui-button ui-button--danger ui-button--icon remove-media"
                    type="button"
                    :disabled="attachmentBusyId === image.id"
                    :aria-label="`删除${image.name}`"
                    @click="removeAttachment(image)"
                  >
                    <Icon icon="tabler:x" />
                  </button>
                </figure>
              </div>
            </div>
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
              <label v-if="canEdit" class="inline-upload-action">
                <input type="file" multiple @change="addResultFiles" />
                <Icon icon="tabler:paperclip" />
                上传附件
              </label>
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
                <button
                  v-if="canEdit && file.id"
                  class="ui-button ui-button--danger ui-button--icon"
                  type="button"
                  :disabled="attachmentBusyId === file.id"
                  :aria-label="`删除${file.name}`"
                  @click="removeAttachment(file)"
                >
                  <Icon icon="tabler:trash" />
                </button>
              </div>
            </div>
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
          v-if="selectedExperiment?.status !== 'completed'"
          class="ui-button ui-button--secondary button"
          type="button"
          :disabled="saving"
          @click="persistRecord(true)"
        >
          暂存
        </button>
        <button
          class="ui-button ui-button--primary button primary"
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
        <button
          v-if="selectedExperiment?.status === 'in_progress'"
          class="ui-button ui-button--primary button complete-button"
          type="button"
          :disabled="saving"
          @click="completeExperiment"
        >
          <Icon icon="tabler:circle-check" />
          完成实验
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
        class="ui-button ui-button--light ui-button--icon preview-close"
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
  --list-width: 390px;
  display: grid;
  grid-template-columns: var(--list-width) minmax(0, 1fr);
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
}

.experiment-list-card {
  position: relative;
  display: flex;
  min-width: 0;
  min-height: 0;
  flex: 1;
  flex-direction: column;
  margin: 8px 0;
  overflow: visible;
  background: var(--color-paper);
  border: 1px solid var(--color-rule);
  border-radius: 9px;
}

.plan-actions {
  display: grid;
  height: 58px;
  flex: 0 0 58px;
  grid-template-columns: 1fr 1fr;
  align-items: center;
  padding: 8px 12px 6px;
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
  scrollbar-width: none;
}

.copy-plan-list::-webkit-scrollbar,
.project-options::-webkit-scrollbar,
.record-scroll::-webkit-scrollbar,
.formula-wrap::-webkit-scrollbar,
.eln-page::-webkit-scrollbar {
  display: none;
  width: 0;
  height: 0;
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
  margin: 0 12px;
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
  scrollbar-width: none;
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
  height: 44px;
  flex: 0 0 44px;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  margin: 0 12px;
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
  -ms-overflow-style: none;
  border-radius: 0 0 8px 8px;
  overscroll-behavior: contain;
  scrollbar-width: none;
  -webkit-overflow-scrolling: touch;
}

.experiment-list::-webkit-scrollbar {
  display: none;
  width: 0;
  height: 0;
}

.experiment-row {
  position: relative;
  width: 100%;
  min-height: 100px;
  padding: 11px 12px;
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
  font-size: 14px;
  line-height: 1.45;
}

.experiment-row small {
  margin-top: 3px;
  color: var(--color-ink-4);
  font-size: 12px;
}

.experiment-row footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 7px;
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
  position: absolute;
  z-index: 8;
  top: 0;
  right: -1px;
  bottom: 0;
  width: 10px;
  padding: 0;
  cursor: col-resize;
  background: transparent;
  border: 0;
  outline: 0;
  touch-action: none;
}

.layout-resizer::after {
  position: absolute;
  top: 10px;
  right: 0;
  bottom: 10px;
  width: 1px;
  background: var(--color-rule);
  border-radius: 999px;
  content: "";
  transition:
    width 140ms ease,
    background-color 140ms ease,
    box-shadow 140ms ease;
}

.layout-resizer:hover::after,
.layout-resizer:focus-visible::after,
.eln-page.is-resizing .layout-resizer::after {
  width: 2px;
  background: color-mix(in srgb, var(--color-accent) 62%, white);
  box-shadow:
    0 0 0 3px rgb(8 124 240 / 8%),
    0 0 8px rgb(8 124 240 / 22%);
}

.record-workspace {
  position: relative;
  display: flex;
  min-width: 0;
  min-height: 0;
  flex-direction: column;
  padding: 8px 6px 8px;
  overflow: hidden;
  background: #f5f7fb;
}

.record-scroll {
  min-height: 0;
  flex: 1;
  padding: 0 0 72px 8px;
  overflow: auto;
  overscroll-behavior: contain;
  scrollbar-width: none;
}

.record-section {
  margin-bottom: 8px;
  padding: 12px;
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
  font-size: 14px;
}

.record-summary {
  margin-top: 9px;
  padding-bottom: 9px;
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
  font-size: 18px;
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
  margin-top: 10px;
  gap: 9px 16px;
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
  padding: 0 4px;
  background: transparent;
  border: 0;
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
  margin-top: 9px;
  overflow-x: auto;
  scrollbar-width: none;
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
  padding: 4px 6px;
  color: var(--color-ink-3);
  background: #f6f8fc;
  border-bottom: 1px solid var(--color-rule);
}

.formula-editor td {
  padding: 3px 5px;
  border-bottom: 1px solid var(--color-rule);
}

.formula-editor tr:last-child td {
  border-bottom: 0;
}

.formula-editor input {
  height: 32px;
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
  justify-content: flex-start;
  margin-bottom: 9px;
}

.process-copy > textarea,
.extra-process textarea,
.result-section > textarea {
  min-height: 104px;
  padding: 9px 10px;
  line-height: 1.75;
  resize: vertical;
}

.extra-process header {
  margin-bottom: 8px;
}

.process-char-count {
  display: block;
  margin-top: 4px;
  color: var(--color-ink-4);
  font-size: 11px;
  text-align: right;
}

.process-media {
  margin-top: 10px;
}

.subheading h3 span,
.attachment-heading h4 span {
  color: var(--color-accent);
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

.inline-upload-action {
  display: inline-flex;
  height: 32px;
  align-items: center;
  justify-content: center;
  padding: 0 2px;
  color: var(--color-accent);
  background: transparent;
  border: 0;
  border-radius: 0;
  cursor: pointer;
  flex-direction: row;
  font-weight: 600;
  gap: 6px;
}

.inline-upload-action:hover {
  color: color-mix(in srgb, var(--color-accent) 78%, black);
}

.inline-upload-action input {
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
  margin-top: 14px;
  padding-top: 14px;
  border-top: 1px solid var(--color-rule);
}

.process-media .subheading,
.attachment-heading {
  display: flex;
  align-items: center;
  justify-content: flex-start;
  gap: 12px;
}

.attachment-heading h4 {
  font-size: 14px;
}

.file-list {
  display: flex;
  min-width: 0;
  flex-wrap: wrap;
  margin-top: 10px;
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

.file-chip span,
.file-chip a {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-chip a:hover {
  color: var(--color-accent);
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

.record-actions {
  position: absolute;
  z-index: 20;
  right: 12px;
  bottom: 8px;
  display: flex;
  padding: 0;
  background: transparent;
  border: 0;
  border-radius: 0;
  box-shadow: none;
  gap: 9px;
}

.record-actions .complete-button {
  color: #ffffff;
  background: #14804a;
  border-color: #14804a;
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
    --list-width: 390px;
  }

  .basic-info-grid,
  .basic-info-create {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

}

@media (max-width: 800px) {
  .eln-page {
    display: flex;
    height: 100%;
    flex-direction: column;
    overflow-y: auto;
    scrollbar-width: none;
  }

  .experiment-list-panel {
    min-width: 0;
    min-height: 390px;
    flex: 0 0 390px;
    border-right: 0;
    border-bottom: 1px solid var(--color-rule);
  }

  .experiment-list-card {
    margin: 8px;
  }

  .layout-resizer {
    display: none;
  }

  .record-workspace {
    min-height: 620px;
    flex: none;
    padding: 8px;
    overflow: visible;
  }

  .record-scroll {
    padding: 0 0 92px;
    overflow: visible;
  }

  .basic-info-grid,
  .basic-info-create {
    grid-template-columns: 1fr;
  }

  .record-actions {
    position: sticky;
    right: auto;
    bottom: 8px;
    justify-content: flex-end;
    flex-wrap: wrap;
    margin: 0 8px;
  }

  .image-preview {
    padding: 20px;
  }
}
</style>
