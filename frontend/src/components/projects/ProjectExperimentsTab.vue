<script setup lang="ts">
import { Icon } from "@iconify/vue";
import { ElMessage } from "element-plus";
import { computed, onMounted, reactive, ref, watch } from "vue";
import { useRouter } from "vue-router";

import { experimentApi } from "@/api/experiments";
import { getProblemDetail } from "@/api/http";
import type {
  Experiment,
  ExperimentStatus,
  ExperimentWriteInput,
  OrganizationUserOption,
} from "@/types/api";

const props = defineProps<{
  projectId: string;
  projectName: string;
  ownerId: string;
  userOptions: OrganizationUserOption[];
  canCreate: boolean;
  canEdit: boolean;
}>();

const emit = defineEmits<{
  changed: [];
}>();

interface PlanFormulaRow {
  material: string;
  batch: string;
  amount: string;
  unit: string;
}

interface PlanEditor {
  name: string;
  experimentType: string;
  ownerId: string;
  participantIds: string[];
  estimatedStart: string;
  estimatedEnd: string;
  purpose: string;
  formulaRows: PlanFormulaRow[];
  processText: string;
}

const router = useRouter();
const experiments = ref<Experiment[]>([]);
const selectedExperimentId = ref("");
const loading = ref(false);
const saving = ref(false);
const editorVisible = ref(false);
const editingExperiment = ref<Experiment | null>(null);
const search = ref("");
const statusFilter = ref<ExperimentStatus | "">("");
const phaseFilter = ref("");
const startDate = ref("");
const endDate = ref("");
const editor = reactive<PlanEditor>(emptyEditor());

const experimentTypes = [
  "配方筛选",
  "性能测试",
  "热分析",
  "结构表征",
  "工艺优化",
  "可靠性测试",
];
const statusGroups: Array<{
  value: ExperimentStatus;
  label: string;
  className: string;
}> = [
  { value: "in_progress", label: "进行中", className: "running" },
  { value: "completed", label: "已完成", className: "completed" },
  { value: "not_started", label: "未开始", className: "waiting" },
];

function emptyEditor(): PlanEditor {
  return {
    name: "",
    experimentType: "配方筛选",
    ownerId: props.ownerId,
    participantIds: [],
    estimatedStart: "",
    estimatedEnd: "",
    purpose: "",
    formulaRows: [
      { material: "", batch: "", amount: "", unit: "g" },
      { material: "", batch: "", amount: "", unit: "g" },
    ],
    processText: "",
  };
}

const filteredExperiments = computed(() => {
  const keyword = search.value.trim().toLowerCase();
  return experiments.value.filter((item) => {
    if (
      keyword &&
      !`${item.name} ${item.experiment_no} ${item.owner_display_name}`
        .toLowerCase()
        .includes(keyword)
    ) {
      return false;
    }
    if (statusFilter.value && item.status !== statusFilter.value) return false;
    if (phaseFilter.value && item.phase !== phaseFilter.value) return false;
    const plannedStart = item.estimated_start?.slice(0, 10) ?? "";
    const plannedEnd = item.estimated_end?.slice(0, 10) ?? "";
    if (startDate.value && plannedEnd && plannedEnd < startDate.value) return false;
    if (endDate.value && plannedStart && plannedStart > endDate.value) return false;
    return true;
  });
});

const groupedExperiments = computed(() =>
  statusGroups.map((group) => ({
    ...group,
    items: filteredExperiments.value.filter((item) => item.status === group.value),
  })),
);

const selectedExperiment = computed(
  () =>
    experiments.value.find((item) => item.id === selectedExperimentId.value) ??
    null,
);

const phases = computed(() =>
  [...new Set(experiments.value.map((item) => item.phase).filter(Boolean))].sort(),
);

const formulaRows = computed(() => selectedExperiment.value?.record.formula_rows ?? []);

const processSteps = computed(() => {
  const labels = ["原料干燥", "高速混合", "双螺杆挤出", "冷却切粒", "注塑制样"];
  const current = selectedExperiment.value?.status === "completed" ? 5 : 1;
  return labels.map((label, index) => ({
    label,
    state:
      selectedExperiment.value?.status === "not_started"
        ? "todo"
        : index + 1 < current
          ? "done"
          : index + 1 === current
            ? "current"
            : "todo",
  }));
});

/**
 * 加载当前项目的实验计划
 */
async function loadExperiments(): Promise<void> {
  loading.value = true;
  try {
    const response = await experimentApi.list({
      project_id: props.projectId,
      page_size: 100,
    });
    experiments.value = response.data;
    if (!experiments.value.some((item) => item.id === selectedExperimentId.value)) {
      selectedExperimentId.value =
        experiments.value.find((item) => item.status === "in_progress")?.id ??
        experiments.value[0]?.id ??
        "";
    }
  } catch (error) {
    const problem = getProblemDetail(error);
    ElMessage.error(problem?.detail ?? "项目实验加载失败");
  } finally {
    loading.value = false;
  }
}

/**
 * 重置实验筛选条件
 */
function resetFilters(): void {
  search.value = "";
  statusFilter.value = "";
  phaseFilter.value = "";
  startDate.value = "";
  endDate.value = "";
}

/**
 * 返回实验状态中文标签
 *
 * @param status 实验状态
 * @returns 中文状态
 */
function statusLabel(status: ExperimentStatus): string {
  return statusGroups.find((item) => item.value === status)?.label ?? status;
}

/**
 * 格式化日期时间
 *
 * @param value ISO 时间
 * @returns 日期时间文本
 */
function formatDateTime(value: string | null): string {
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

/**
 * 转换为 datetime-local 输入值
 *
 * @param value ISO 时间
 * @returns 本地输入值
 */
function toInputDateTime(value: string | null): string {
  if (!value) return "";
  const date = new Date(value);
  const local = new Date(date.getTime() - date.getTimezoneOffset() * 60_000);
  return local.toISOString().slice(0, 16);
}

/**
 * 根据常见原料名称返回原型 CAS 号
 *
 * @param material 原料名称
 * @returns CAS 号或占位符
 */
function materialCas(material: unknown): string {
  const name = String(material ?? "");
  if (name.includes("PLA")) return "9051-89-2";
  if (name.includes("PBAT")) return "55231-08-8";
  if (name.includes("相容")) return "7440-22-4";
  if (name.includes("抗氧")) return "6683-19-8";
  return "—";
}

/**
 * 打开新建实验计划弹窗
 */
function openCreate(): void {
  if (!props.canCreate) {
    ElMessage.warning("当前账号无实验计划创建权限");
    return;
  }
  editingExperiment.value = null;
  Object.assign(editor, emptyEditor());
  editorVisible.value = true;
}

/**
 * 打开实验计划编辑弹窗
 */
function openEdit(): void {
  const item = selectedExperiment.value;
  if (!item || !props.canEdit || item.status === "completed") {
    ElMessage.warning("当前实验计划不可编辑");
    return;
  }
  editingExperiment.value = item;
  const rows = item.record.formula_rows.map((row) => ({
    material: String(row.material ?? ""),
    batch: String(row.batch ?? ""),
    amount: String(row.amount ?? ""),
    unit: String(row.unit ?? "g"),
  }));
  Object.assign(editor, {
    name: item.name,
    experimentType: item.experiment_type,
    ownerId: item.owner_id,
    participantIds: item.participant_ids.filter((id) => id !== item.owner_id),
    estimatedStart: toInputDateTime(item.estimated_start),
    estimatedEnd: toInputDateTime(item.estimated_end),
    purpose: item.purpose,
    formulaRows: rows.length ? rows : emptyEditor().formulaRows,
    processText: item.record.process_text,
  });
  editorVisible.value = true;
}

/**
 * 新增配方原料行
 */
function addFormulaRow(): void {
  editor.formulaRows.push({ material: "", batch: "", amount: "", unit: "g" });
}

/**
 * 删除配方原料行
 *
 * @param index 配方行索引
 */
function removeFormulaRow(index: number): void {
  if (editor.formulaRows.length === 1) {
    ElMessage.warning("至少保留一行原料");
    return;
  }
  editor.formulaRows.splice(index, 1);
}

/**
 * 保存新建或编辑的实验计划
 */
async function submitPlan(): Promise<void> {
  if (
    !editor.name.trim() ||
    !editor.experimentType ||
    !editor.ownerId ||
    !editor.estimatedStart ||
    !editor.estimatedEnd ||
    !editor.purpose.trim()
  ) {
    ElMessage.warning("请填写实验计划全部必填项");
    return;
  }
  if (editor.estimatedEnd < editor.estimatedStart) {
    ElMessage.warning("预估结束时间不能早于开始时间");
    return;
  }
  const rows = editor.formulaRows
    .map((row) => ({
      material: row.material.trim(),
      batch: row.batch.trim(),
      amount: row.amount.trim(),
      unit: row.unit,
    }))
    .filter((row) => row.material);
  const payload: ExperimentWriteInput = {
    project_id: props.projectId,
    name: editor.name.trim(),
    experiment_type: editor.experimentType,
    purpose: editor.purpose.trim(),
    estimated_start: editor.estimatedStart,
    estimated_end: editor.estimatedEnd,
    owner_id: editor.ownerId,
    participant_ids: editor.participantIds.filter((id) => id !== editor.ownerId),
    formula_columns: [
      { id: "material", label: "原料名称" },
      { id: "batch", label: "规格/批次" },
      { id: "amount", label: "计划用量" },
      { id: "unit", label: "单位" },
    ],
    formula_rows: rows,
    process_text: editor.processText.trim(),
  };

  saving.value = true;
  try {
    const saved = editingExperiment.value
      ? await experimentApi.update(
          editingExperiment.value.experiment_no,
          editingExperiment.value.version,
          payload,
        )
      : await experimentApi.create(payload);
    const index = experiments.value.findIndex((item) => item.id === saved.id);
    if (index >= 0) experiments.value.splice(index, 1, saved);
    else experiments.value.unshift(saved);
    selectedExperimentId.value = saved.id;
    editorVisible.value = false;
    emit("changed");
    ElMessage.success(editingExperiment.value ? "实验计划已更新" : "实验计划已创建");
  } catch (error) {
    const problem = getProblemDetail(error);
    if (problem?.status === 412) {
      ElMessage.warning("实验计划已被其他人修改，正在刷新");
      await loadExperiments();
    } else {
      ElMessage.error(problem?.detail ?? "实验计划保存失败");
    }
  } finally {
    saving.value = false;
  }
}

/**
 * 进入电子实验记录本并定位当前实验
 */
function openElectronicNotebook(): void {
  if (!selectedExperiment.value) return;
  void router.push({
    path: "/eln",
    query: { experiment: selectedExperiment.value.experiment_no },
  });
}

watch(
  () => props.projectId,
  () => void loadExperiments(),
);
watch(filteredExperiments, (items) => {
  if (!items.some((item) => item.id === selectedExperimentId.value)) {
    selectedExperimentId.value = items[0]?.id ?? "";
  }
});
onMounted(loadExperiments);
</script>

<template>
  <section class="experiment-module">
    <section class="experiment-toolbar">
      <label class="experiment-search">
        <Icon icon="tabler:search" />
        <input
          v-model="search"
          type="search"
          placeholder="搜索实验名称、ID或负责人"
        />
      </label>
      <select v-model="statusFilter" aria-label="实验状态">
        <option value="">全部状态</option>
        <option
          v-for="item in statusGroups"
          :key="item.value"
          :value="item.value"
        >
          {{ item.label }}
        </option>
      </select>
      <select v-model="phaseFilter" aria-label="所属阶段">
        <option value="">全部阶段</option>
        <option v-for="item in phases" :key="item">{{ item }}</option>
      </select>
      <label class="date-range">
        <Icon icon="tabler:calendar" />
        <input v-model="startDate" type="date" aria-label="计划开始日期" />
        <span>至</span>
        <input v-model="endDate" type="date" aria-label="计划结束日期" />
      </label>
      <button type="button" class="reset-button" @click="resetFilters">重置</button>
      <button
        v-if="canCreate"
        type="button"
        class="create-button"
        @click="openCreate"
      >
        <Icon icon="tabler:plus" />新建实验
      </button>
    </section>

    <section v-loading="loading" class="experiment-workspace">
      <aside class="experiment-master">
        <header>
          <strong>实验列表</strong>
          <span>（共 {{ filteredExperiments.length }} 个）</span>
        </header>
        <div class="experiment-groups">
          <section
            v-for="group in groupedExperiments"
            :key="group.value"
            v-show="group.items.length"
            class="experiment-group"
          >
            <h3 :class="group.className">
              <i />{{ group.label }}（{{ group.items.length }}）
            </h3>
            <button
              v-for="item in group.items"
              :key="item.id"
              type="button"
              :class="{ selected: selectedExperimentId === item.id }"
              @click="selectedExperimentId = item.id"
            >
              <span>
                <strong>{{ item.name }}</strong>
                <small>{{ item.experiment_no }}　·　{{ item.owner_display_name }}</small>
              </span>
              <em :class="group.className"><i />{{ group.label }}</em>
            </button>
          </section>
          <div v-if="!filteredExperiments.length && !loading" class="experiment-empty">
            没有匹配实验
          </div>
        </div>
      </aside>

      <article v-if="selectedExperiment" class="experiment-detail">
        <header class="experiment-detail-head">
          <div>
            <span class="detail-title">
              <h2>{{ selectedExperiment.name }}</h2>
              <b :class="selectedExperiment.status">
                {{ statusLabel(selectedExperiment.status) }}
              </b>
            </span>
          </div>
          <div class="detail-actions">
            <button type="button" @click="openElectronicNotebook">查看详情</button>
            <button
              v-if="canEdit && selectedExperiment.status !== 'completed'"
              type="button"
              @click="openEdit"
            >
              编辑计划
            </button>
            <button class="primary" type="button" @click="openElectronicNotebook">
              进入电子实验记录本
            </button>
          </div>
        </header>

        <dl class="experiment-facts">
          <div><dt>实验 ID</dt><dd>{{ selectedExperiment.experiment_no }}</dd></div>
          <div><dt>实验类型</dt><dd>{{ selectedExperiment.experiment_type }}</dd></div>
          <div><dt>负责人</dt><dd>{{ selectedExperiment.owner_display_name }}</dd></div>
          <div>
            <dt>参与人</dt>
            <dd>{{ selectedExperiment.participant_names.join("、") || "—" }}</dd>
          </div>
          <div><dt>所属阶段</dt><dd>{{ selectedExperiment.phase }}</dd></div>
          <div><dt>计划开始</dt><dd>{{ formatDateTime(selectedExperiment.estimated_start) }}</dd></div>
        </dl>

        <section class="experiment-purpose">
          <h3>实验目的</h3>
          <p>{{ selectedExperiment.purpose || "暂未填写实验目的与判定标准" }}</p>
        </section>

        <section class="formula-process">
          <div>
            <h3>原料配方</h3>
            <div class="formula-table-scroll">
              <table>
                <thead>
                  <tr>
                    <th>原料名称</th>
                    <th>CAS号</th>
                    <th>规格</th>
                    <th>计划用量</th>
                    <th>实际用量</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="(row, index) in formulaRows" :key="index">
                    <td>{{ row.material || "—" }}</td>
                    <td>{{ materialCas(row.material) }}</td>
                    <td>{{ row.batch || "—" }}</td>
                    <td>
                      {{ row.amount || "—" }}{{ row.amount ? ` ${row.unit || ""}` : "" }}
                    </td>
                    <td>{{ selectedExperiment.status === "completed" ? row.amount || "—" : "—" }}</td>
                  </tr>
                  <tr v-if="!formulaRows.length">
                    <td colspan="5">暂无配方</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
          <div>
            <h3>工艺流程</h3>
            <ol class="process-flow">
              <li
                v-for="(step, index) in processSteps"
                :key="step.label"
                :class="step.state"
              >
                <i>{{ index + 1 }}</i>
                <strong>{{ step.label }}</strong>
                <small>
                  {{ step.state === "done" ? "已完成" : step.state === "current" ? "进行中" : "待开始" }}
                </small>
              </li>
            </ol>
          </div>
        </section>

        <section class="experiment-result">
          <h3>实验结果</h3>
          <div>
            <Icon icon="tabler:file-description" />
            <span>
              <strong>
                {{ selectedExperiment.record.result_text ? "已记录" : "记录中" }}
              </strong>
              <small>
                {{
                  selectedExperiment.record.result_text ||
                  "实验尚未完成，结果将在全部步骤结束后汇总。"
                }}
              </small>
            </span>
            <button type="button" @click="openElectronicNotebook">
              查看记录数据
            </button>
          </div>
        </section>
      </article>
      <div v-else class="detail-empty">请选择实验查看详情</div>
    </section>

    <el-dialog
      v-model="editorVisible"
      :title="editingExperiment ? '编辑实验计划' : '新建实验计划'"
      width="820px"
      align-center
      destroy-on-close
    >
      <div class="plan-form">
        <section>
          <header>
            <h3>实验基础信息</h3>
            <p>{{ projectName }} · 保存后自动同步至电子实验记录本</p>
          </header>
          <div class="plan-grid">
            <label class="wide">
              <span>实验名称 <i>*</i></span>
              <input v-model="editor.name" maxlength="200" />
            </label>
            <label>
              <span>实验类型 <i>*</i></span>
              <select v-model="editor.experimentType">
                <option v-for="item in experimentTypes" :key="item">{{ item }}</option>
              </select>
            </label>
            <label>
              <span>实验负责人 <i>*</i></span>
              <select v-model="editor.ownerId">
                <option v-for="user in userOptions" :key="user.id" :value="user.id">
                  {{ user.display_name }}
                </option>
              </select>
            </label>
            <label>
              <span>预估开始时间 <i>*</i></span>
              <input v-model="editor.estimatedStart" type="datetime-local" />
            </label>
            <label>
              <span>预估结束时间 <i>*</i></span>
              <input v-model="editor.estimatedEnd" type="datetime-local" />
            </label>
            <label class="wide">
              <span>参与人员</span>
              <span class="participant-options">
                <label
                  v-for="user in userOptions.filter((item) => item.id !== editor.ownerId)"
                  :key="user.id"
                >
                  <input
                    v-model="editor.participantIds"
                    type="checkbox"
                    :value="user.id"
                  />
                  {{ user.display_name }}
                </label>
              </span>
            </label>
            <label class="wide">
              <span>实验目的与判定标准 <i>*</i></span>
              <textarea v-model="editor.purpose" rows="3" maxlength="10000" />
            </label>
          </div>
        </section>

        <section>
          <header class="formula-head">
            <div>
              <h3>原料配方表</h3>
              <p>与电子实验记录本保持一致，可按需增删原料</p>
            </div>
            <button type="button" @click="addFormulaRow">
              <Icon icon="tabler:row-insert-bottom" />添加原料
            </button>
          </header>
          <div class="formula-editor-head">
            <span>原料名称</span><span>规格/批次</span><span>计划用量</span>
            <span>单位</span><span />
          </div>
          <div
            v-for="(row, index) in editor.formulaRows"
            :key="index"
            class="formula-editor-row"
          >
            <input v-model="row.material" placeholder="原料名称" />
            <input v-model="row.batch" placeholder="规格/批次" />
            <input v-model="row.amount" placeholder="用量" />
            <select v-model="row.unit">
              <option>g</option><option>kg</option><option>mL</option><option>L</option>
            </select>
            <button
              type="button"
              aria-label="删除原料"
              @click="removeFormulaRow(index)"
            >
              <Icon icon="tabler:trash" />
            </button>
          </div>
        </section>

        <section>
          <header>
            <h3>计划实验步骤</h3>
            <p>填写计划执行顺序与关键工艺参数</p>
          </header>
          <textarea
            v-model="editor.processText"
            rows="4"
            placeholder="例如：1. 原料预干燥；2. 按配方称量；3. 设定温度与转速完成混炼……"
          />
        </section>
      </div>
      <template #footer>
        <el-button @click="editorVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitPlan">
          {{ editingExperiment ? "保存计划" : "创建计划" }}
        </el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.experiment-module {
  margin-top: 14px;
}

.experiment-toolbar {
  display: grid;
  align-items: center;
  grid-template-columns: minmax(240px, 1.2fr) 130px 130px minmax(310px, 1fr) auto auto;
  padding: 12px 14px;
  background: var(--color-paper);
  border: 1px solid var(--color-rule);
  border-radius: 8px 8px 0 0;
  gap: 10px;
}

.experiment-search,
.date-range {
  display: flex;
  height: 38px;
  align-items: center;
  padding: 0 10px;
  background: var(--color-paper);
  border: 1px solid var(--color-rule-2);
  border-radius: 5px;
  gap: 7px;
}

.experiment-search svg,
.date-range svg {
  flex: none;
  width: 17px;
  color: var(--color-muted);
}

.experiment-search input,
.date-range input {
  min-width: 0;
  color: var(--color-ink);
  font: inherit;
  background: transparent;
  border: 0;
  outline: 0;
}

.date-range input {
  width: 125px;
}

.date-range span {
  color: var(--color-muted);
}

.experiment-toolbar > select,
.reset-button,
.create-button {
  height: 38px;
  padding: 0 11px;
  color: var(--color-ink-2);
  font: inherit;
  background: var(--color-paper);
  border: 1px solid var(--color-rule-2);
  border-radius: 5px;
}

.reset-button,
.create-button {
  cursor: pointer;
}

.create-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: #ffffff;
  background: var(--color-accent);
  border-color: var(--color-accent);
  gap: 5px;
}

.experiment-workspace {
  display: grid;
  min-height: 560px;
  max-height: calc(100vh - 274px);
  grid-template-columns: minmax(330px, 38%) minmax(0, 1fr);
  overflow: hidden;
  background: var(--color-paper);
  border: 1px solid var(--color-rule);
  border-top: 0;
  border-radius: 0 0 8px 8px;
}

.experiment-master {
  min-height: 0;
  overflow: hidden;
  border-right: 1px solid var(--color-rule);
}

.experiment-master > header {
  display: flex;
  height: 44px;
  align-items: center;
  padding: 0 14px;
  border-bottom: 1px solid var(--color-rule);
}

.experiment-master > header span {
  color: var(--color-muted);
}

.experiment-groups {
  height: calc(100% - 44px);
  overflow-y: auto;
}

.experiment-group h3 {
  display: flex;
  height: 36px;
  align-items: center;
  padding: 0 14px;
  margin: 0;
  color: var(--color-ink-2);
  font-size: 13px;
  background: var(--color-paper-2);
  border-bottom: 1px solid var(--color-rule);
  gap: 8px;
}

.experiment-group h3 > i,
.experiment-group em i {
  display: inline-block;
  width: 7px;
  height: 7px;
  background: #2ea568;
  border-radius: 999px;
}

.experiment-group .completed > i,
.experiment-group em.completed i {
  background: #2ea568;
}

.experiment-group .waiting > i,
.experiment-group em.waiting i {
  background: #a9b2bf;
}

.experiment-group > button {
  display: flex;
  width: 100%;
  min-height: 63px;
  align-items: center;
  justify-content: space-between;
  padding: 9px 14px;
  color: var(--color-ink);
  text-align: left;
  background: var(--color-paper);
  border: 0;
  border-bottom: 1px solid var(--color-rule);
  cursor: pointer;
  gap: 12px;
}

.experiment-group > button:hover,
.experiment-group > button.selected {
  background: #e8f1ff;
}

.experiment-group > button.selected {
  box-shadow: inset 3px 0 0 var(--color-accent);
}

.experiment-group button strong,
.experiment-group button small {
  display: block;
}

.experiment-group button strong {
  max-width: 280px;
  overflow: hidden;
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.experiment-group button small {
  margin-top: 5px;
  color: var(--color-muted);
  font-size: 11px;
}

.experiment-group em {
  display: flex;
  flex: none;
  align-items: center;
  color: var(--color-muted);
  font-size: 11px;
  font-style: normal;
  gap: 5px;
}

.experiment-empty,
.detail-empty {
  display: grid;
  min-height: 240px;
  color: var(--color-muted);
  place-items: center;
}

.experiment-detail {
  min-width: 0;
  overflow-y: auto;
}

.experiment-detail-head {
  display: flex;
  min-height: 74px;
  align-items: flex-start;
  justify-content: space-between;
  padding: 14px 16px;
  border-bottom: 1px solid var(--color-rule);
  gap: 16px;
}

.detail-title {
  display: flex;
  align-items: center;
  gap: 10px;
}

.detail-title h2 {
  margin: 0;
  font-size: 17px;
}

.detail-title b {
  padding: 3px 8px;
  color: #087d44;
  font-size: 11px;
  background: var(--color-success-soft);
  border-radius: 5px;
}

.detail-title b.not_started {
  color: var(--color-muted);
  background: var(--color-paper-3);
}

.detail-actions {
  display: flex;
  flex: none;
  gap: 7px;
}

.detail-actions button {
  height: 32px;
  padding: 0 10px;
  color: var(--color-ink-2);
  background: var(--color-paper);
  border: 1px solid var(--color-rule-2);
  border-radius: 5px;
  cursor: pointer;
}

.detail-actions button.primary {
  color: #ffffff;
  background: var(--color-accent);
  border-color: var(--color-accent);
}

.experiment-facts {
  display: grid;
  grid-template-columns: repeat(6, minmax(92px, 1fr));
  padding: 13px 16px;
  margin: 0;
  border-bottom: 1px solid var(--color-rule);
  gap: 12px;
}

.experiment-facts dt {
  color: var(--color-muted);
  font-size: 11px;
}

.experiment-facts dd {
  margin: 5px 0 0;
  overflow: hidden;
  font-size: 12px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.experiment-purpose,
.experiment-result {
  padding: 12px 16px;
  border-bottom: 1px solid var(--color-rule);
}

.experiment-detail h3 {
  margin: 0 0 9px;
  font-size: 14px;
}

.experiment-purpose p {
  padding: 10px 12px;
  margin: 0;
  color: var(--color-ink-2);
  font-size: 12px;
  line-height: 1.6;
  background: var(--color-paper-2);
  border: 1px solid var(--color-rule);
  border-radius: 5px;
}

.formula-process {
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(260px, 0.85fr);
  padding: 12px 16px;
  border-bottom: 1px solid var(--color-rule);
  gap: 18px;
}

.formula-table-scroll {
  overflow-x: auto;
}

.formula-process table {
  width: 100%;
  min-width: 520px;
  border-collapse: collapse;
}

.formula-process th,
.formula-process td {
  height: 34px;
  padding: 5px 8px;
  overflow: hidden;
  font-size: 11px;
  text-align: left;
  text-overflow: ellipsis;
  white-space: nowrap;
  border: 1px solid var(--color-rule);
}

.formula-process th {
  color: var(--color-ink-2);
  background: var(--color-paper-2);
}

.process-flow {
  display: grid;
  grid-template-columns: repeat(5, minmax(58px, 1fr));
  padding: 12px 0 0;
  margin: 0;
  list-style: none;
  gap: 0;
}

.process-flow li {
  position: relative;
  display: flex;
  align-items: center;
  flex-direction: column;
  text-align: center;
}

.process-flow li::after {
  position: absolute;
  top: 15px;
  left: calc(50% + 17px);
  width: calc(100% - 34px);
  height: 1px;
  background: var(--color-rule-2);
  content: "";
}

.process-flow li:last-child::after {
  display: none;
}

.process-flow li > i {
  z-index: 1;
  display: grid;
  width: 30px;
  height: 30px;
  color: var(--color-muted);
  font-size: 11px;
  font-style: normal;
  background: var(--color-paper);
  border: 1px solid var(--color-rule-2);
  border-radius: 999px;
  place-items: center;
}

.process-flow li.current > i,
.process-flow li.done > i {
  color: #ffffff;
  background: var(--color-accent);
  border-color: var(--color-accent);
}

.process-flow strong,
.process-flow small {
  display: block;
}

.process-flow strong {
  margin-top: 8px;
  font-size: 10px;
}

.process-flow small {
  margin-top: 3px;
  color: var(--color-muted);
  font-size: 9px;
}

.process-flow li.current small {
  color: var(--color-accent);
}

.experiment-result > div {
  display: flex;
  align-items: center;
  padding: 11px 13px;
  background: var(--color-paper-2);
  border: 1px solid var(--color-rule);
  border-radius: 6px;
  gap: 10px;
}

.experiment-result > div > svg {
  width: 32px;
  height: 32px;
  padding: 7px;
  color: var(--color-accent);
  background: var(--color-accent-soft);
  border-radius: 999px;
}

.experiment-result span {
  min-width: 0;
}

.experiment-result strong,
.experiment-result small {
  display: block;
}

.experiment-result small {
  margin-top: 3px;
  overflow: hidden;
  color: var(--color-muted);
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.experiment-result button {
  flex: none;
  margin-left: auto;
  color: var(--color-accent);
  font: inherit;
  background: transparent;
  border: 0;
  cursor: pointer;
}

.plan-form {
  max-height: 68vh;
  overflow-y: auto;
}

.plan-form > section {
  padding: 16px 2px;
  border-bottom: 1px solid var(--color-rule);
}

.plan-form > section:first-child {
  padding-top: 0;
}

.plan-form > section:last-child {
  border-bottom: 0;
}

.plan-form section > header h3,
.plan-form section > header p {
  margin: 0;
}

.plan-form section > header h3 {
  font-size: 15px;
}

.plan-form section > header p {
  margin-top: 3px;
  color: var(--color-muted);
  font-size: 12px;
}

.plan-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  margin-top: 13px;
  gap: 12px;
}

.plan-grid > label,
.plan-form > section:last-child {
  display: grid;
  gap: 6px;
}

.plan-grid label > span {
  color: var(--color-ink-2);
  font-weight: 600;
}

.plan-grid label i {
  color: var(--color-danger);
  font-style: normal;
}

.plan-grid .wide {
  grid-column: 1 / -1;
}

.plan-grid input,
.plan-grid select,
.plan-grid textarea,
.formula-editor-row input,
.formula-editor-row select,
.plan-form > section:last-child textarea {
  width: 100%;
  min-height: 38px;
  padding: 8px 10px;
  color: var(--color-ink);
  font: inherit;
  background: var(--color-paper);
  border: 1px solid var(--color-rule-2);
  border-radius: 5px;
  outline: none;
}

.participant-options {
  display: flex;
  min-height: 38px;
  align-items: center;
  flex-wrap: wrap;
  padding: 5px 9px;
  border: 1px solid var(--color-rule-2);
  border-radius: 5px;
  gap: 5px 16px;
}

.participant-options label {
  display: inline-flex;
  align-items: center;
  font-size: 12px;
  font-weight: 400;
  gap: 5px;
}

.participant-options input {
  width: 14px;
  min-height: 14px;
}

.formula-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.formula-head button {
  display: inline-flex;
  align-items: center;
  color: var(--color-accent);
  font: inherit;
  background: transparent;
  border: 0;
  cursor: pointer;
  gap: 4px;
}

.formula-editor-head,
.formula-editor-row {
  display: grid;
  grid-template-columns: 1.15fr 1.35fr 0.65fr 0.55fr 36px;
  margin-top: 9px;
  gap: 8px;
}

.formula-editor-head {
  padding: 0 2px;
  color: var(--color-muted);
  font-size: 11px;
}

.formula-editor-row button {
  display: grid;
  color: var(--color-danger);
  background: var(--color-paper);
  border: 1px solid var(--color-rule-2);
  border-radius: 5px;
  cursor: pointer;
  place-items: center;
}

@media (max-width: 1180px) {
  .experiment-toolbar {
    grid-template-columns: minmax(220px, 1fr) 120px 120px auto;
  }

  .date-range {
    display: none;
  }

  .experiment-workspace {
    grid-template-columns: 330px minmax(0, 1fr);
  }

  .experiment-facts {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}
</style>
