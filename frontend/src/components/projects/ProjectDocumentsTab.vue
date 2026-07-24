<script setup lang="ts">
import { Icon } from "@iconify/vue";
import { ElMessage } from "element-plus";
import { computed, onMounted, reactive, ref, watch } from "vue";

import { getProblemDetail } from "@/api/http";
import { projectApi } from "@/api/projects";
import type {
  ProjectDocument,
  ProjectDocumentCategory,
  ProjectDocumentFilters,
} from "@/types/api";

const props = defineProps<{
  projectId: string;
  canUpload: boolean;
}>();
const emit = defineEmits<{
  changed: [];
}>();

const categoryOptions: Array<{
  value: ProjectDocumentCategory | "";
  label: string;
  icon: string;
}> = [
  { value: "", label: "全部文档", icon: "tabler:files" },
  { value: "project_plan", label: "项目方案", icon: "tabler:folder" },
  { value: "literature", label: "文献资料", icon: "tabler:folder" },
  { value: "experiment_plan", label: "实验方案", icon: "tabler:folder" },
  { value: "stage_report", label: "阶段报告", icon: "tabler:folder" },
  { value: "meeting_minutes", label: "会议纪要", icon: "tabler:folder" },
  { value: "other", label: "其他", icon: "tabler:folder" },
];

const documents = ref<ProjectDocument[]>([]);
const total = ref(0);
const categoryCounts = ref<Record<ProjectDocumentCategory, number>>({
  project_plan: 0,
  literature: 0,
  experiment_plan: 0,
  stage_report: 0,
  meeting_minutes: 0,
  other: 0,
});
const loading = ref(false);
const uploading = ref(false);
const uploadVisible = ref(false);
const previewDocument = ref<ProjectDocument | null>(null);
const search = ref("");
const category = ref<ProjectDocumentCategory | "">("");
const fileType = ref<ProjectDocumentFilters["file_type"]>("");
const updatedRange = ref<ProjectDocumentFilters["updated_range"]>("");
const uploadForm = reactive<{
  file: File | null;
  category: ProjectDocumentCategory;
  relatedContent: string;
  versionLabel: string;
}>({
  file: null,
  category: "project_plan",
  relatedContent: "项目整体",
  versionLabel: "V1.0",
});
let searchTimer: number | undefined;

const previewKind = computed(() => {
  const extension = previewDocument.value?.extension.toLowerCase() ?? "";
  if (["png", "jpg", "jpeg", "webp"].includes(extension)) return "image";
  if (extension === "pdf") return "pdf";
  if (["xls", "xlsx", "csv"].includes(extension)) return "sheet";
  if (["ppt", "pptx"].includes(extension)) return "slide";
  if (["doc", "docx"].includes(extension)) return "word";
  return "generic";
});

/**
 * 查询当前项目文档和分类统计
 */
async function loadDocuments(): Promise<void> {
  loading.value = true;
  try {
    const response = await projectApi.listDocuments(props.projectId, {
      search: search.value.trim(),
      category: category.value,
      file_type: fileType.value,
      updated_range: updatedRange.value,
    });
    documents.value = response.data;
    total.value = response.meta.total;
    categoryCounts.value = response.meta.category_counts;
  } catch (error) {
    const problem = getProblemDetail(error);
    ElMessage.error(problem?.detail ?? "项目文档加载失败");
  } finally {
    loading.value = false;
  }
}

/**
 * 返回分类对应的实际文档数量
 *
 * @param value 文档分类，空值表示全部
 * @returns 文档数量
 */
function categoryCount(value: ProjectDocumentCategory | ""): number {
  return value ? categoryCounts.value[value] : total.value;
}

/**
 * 格式化字节数
 *
 * @param value 文件大小字节数
 * @returns 便于阅读的文件大小
 */
function formatSize(value: number): string {
  if (value < 1024) return `${value} B`;
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`;
  return `${(value / 1024 / 1024).toFixed(1)} MB`;
}

/**
 * 格式化更新时间
 *
 * @param value ISO 时间
 * @returns 本地日期时间文本
 */
function formatDateTime(value: string): string {
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
 * 返回原型一致的文件类型图标
 *
 * @param extension 小写扩展名
 * @returns Iconify 图标名
 */
function fileIcon(extension: string): string {
  if (["doc", "docx"].includes(extension)) return "tabler:file-type-docx";
  if (extension === "pdf") return "tabler:file-type-pdf";
  if (["xls", "xlsx", "csv"].includes(extension)) return "tabler:file-spreadsheet";
  if (["ppt", "pptx"].includes(extension)) return "tabler:presentation";
  if (["png", "jpg", "jpeg", "webp"].includes(extension)) return "tabler:photo";
  return "tabler:file";
}

/**
 * 打开上传对话框
 */
function openUpload(): void {
  if (!props.canUpload) {
    ElMessage.warning("当前账号或项目状态不允许上传文档");
    return;
  }
  uploadForm.file = null;
  uploadForm.category = "project_plan";
  uploadForm.relatedContent = "项目整体";
  uploadForm.versionLabel = "V1.0";
  uploadVisible.value = true;
}

/**
 * 读取文件输入框选择结果
 *
 * @param event 文件输入事件
 */
function selectUploadFile(event: Event): void {
  const target = event.target as HTMLInputElement;
  uploadForm.file = target.files?.[0] ?? null;
}

/**
 * 上传文档并刷新列表
 */
async function submitUpload(): Promise<void> {
  if (!uploadForm.file) {
    ElMessage.warning("请选择待上传文件");
    return;
  }
  if (!uploadForm.relatedContent.trim() || !uploadForm.versionLabel.trim()) {
    ElMessage.warning("请填写关联内容和版本");
    return;
  }
  uploading.value = true;
  try {
    await projectApi.uploadDocument(props.projectId, {
      file: uploadForm.file,
      category: uploadForm.category,
      related_content: uploadForm.relatedContent.trim(),
      version_label: uploadForm.versionLabel.trim(),
    });
    uploadVisible.value = false;
    ElMessage.success("文档上传成功");
    await loadDocuments();
    emit("changed");
  } catch (error) {
    const problem = getProblemDetail(error);
    ElMessage.error(problem?.detail ?? "文档上传失败");
  } finally {
    uploading.value = false;
  }
}

/**
 * 下载指定项目文档
 *
 * @param item 项目文档
 */
function downloadDocument(item: ProjectDocument): void {
  const link = window.document.createElement("a");
  link.href = projectApi.documentContentUrl(props.projectId, item.id, true);
  link.download = item.name;
  window.document.body.appendChild(link);
  link.click();
  link.remove();
}

watch(
  () => props.projectId,
  () => void loadDocuments(),
);
watch([category, fileType, updatedRange], () => void loadDocuments());
watch(search, () => {
  window.clearTimeout(searchTimer);
  searchTimer = window.setTimeout(() => void loadDocuments(), 260);
});
onMounted(loadDocuments);
</script>

<template>
  <section class="documents-layout">
    <aside class="folder-panel">
      <h2>文档分类</h2>
      <button
        v-for="item in categoryOptions"
        :key="item.label"
        type="button"
        :class="{ active: category === item.value }"
        @click="category = item.value"
      >
        <Icon :icon="item.icon" />
        <span>{{ item.label }}</span>
        <b>{{ categoryCount(item.value) }}</b>
      </button>
    </aside>

    <div class="documents-main">
      <section class="documents-toolbar">
        <label class="document-search">
          <Icon icon="tabler:search" />
          <input
            v-model="search"
            type="search"
            placeholder="搜索文档名称、编号或更新人"
          />
        </label>
        <span />
        <select v-model="fileType" aria-label="文件类型">
          <option value="">全部类型</option>
          <option value="word">Word</option>
          <option value="pdf">PDF</option>
          <option value="excel">Excel</option>
          <option value="powerpoint">PowerPoint</option>
          <option value="image">图片</option>
        </select>
        <select v-model="updatedRange" aria-label="更新时间">
          <option value="">最近更新</option>
          <option value="week">本周</option>
          <option value="month">本月</option>
        </select>
        <button
          class="upload-button"
          type="button"
          :disabled="!canUpload"
          @click="openUpload"
        >
          <Icon icon="tabler:upload" />上传文档
        </button>
      </section>

      <div v-loading="loading" class="documents-table-scroll">
        <table class="documents-table">
          <thead>
            <tr>
              <th>文档名称</th>
              <th>分类</th>
              <th>关联内容</th>
              <th>版本</th>
              <th>更新人</th>
              <th>更新时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in documents" :key="item.id">
              <td>
                <span class="file-name">
                  <i><Icon :icon="fileIcon(item.extension)" /></i>
                  <span>
                    <strong>{{ item.name }}</strong>
                    <small>{{ formatSize(item.file_size) }}</small>
                  </span>
                </span>
              </td>
              <td>{{ item.category_label }}</td>
              <td>{{ item.related_content }}</td>
              <td>{{ item.version_label }}</td>
              <td>{{ item.uploaded_by_name }}</td>
              <td>{{ formatDateTime(item.updated_at) }}</td>
              <td>
                <span class="document-actions">
                  <button
                    type="button"
                    aria-label="预览文件"
                    title="预览"
                    @click="previewDocument = item"
                  >
                    <Icon icon="tabler:eye" />
                  </button>
                  <button
                    type="button"
                    aria-label="下载文件"
                    title="下载"
                    @click="downloadDocument(item)"
                  >
                    <Icon icon="tabler:download" />
                  </button>
                </span>
              </td>
            </tr>
            <tr v-if="!loading && !documents.length">
              <td colspan="7" class="empty-row">没有匹配文档</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <el-dialog
      v-model="uploadVisible"
      title="上传项目文档"
      width="540px"
      align-center
    >
      <div class="upload-form">
        <label>
          <span>选择文件 <i>*</i></span>
          <input type="file" @change="selectUploadFile" />
          <small>单个文件不超过 25 MB</small>
        </label>
        <div>
          <label>
            <span>文档分类 <i>*</i></span>
            <select v-model="uploadForm.category">
              <option
                v-for="item in categoryOptions.slice(1)"
                :key="item.value"
                :value="item.value"
              >
                {{ item.label }}
              </option>
            </select>
          </label>
          <label>
            <span>版本 <i>*</i></span>
            <input v-model="uploadForm.versionLabel" maxlength="32" />
          </label>
        </div>
        <label>
          <span>关联内容 <i>*</i></span>
          <input
            v-model="uploadForm.relatedContent"
            maxlength="200"
            placeholder="项目整体或实验编号"
          />
        </label>
      </div>
      <template #footer>
        <el-button @click="uploadVisible = false">取消</el-button>
        <el-button type="primary" :loading="uploading" @click="submitUpload">
          上传
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      :model-value="Boolean(previewDocument)"
      class="document-preview-dialog"
      width="82%"
      align-center
      destroy-on-close
      @close="previewDocument = null"
    >
      <template #header>
        <strong>{{ previewDocument?.name }}</strong>
      </template>
      <div v-if="previewDocument" class="preview-body">
        <img
          v-if="previewKind === 'image'"
          :src="projectApi.documentContentUrl(projectId, previewDocument.id)"
          :alt="previewDocument.name"
        />
        <article v-else-if="previewKind === 'pdf'" class="pdf-preview">
          <span>1 / 12</span>
          <h2>{{ previewDocument.name.replace(/\.pdf$/i, "") }}</h2>
          <h3>摘要</h3>
          <p>
            本文梳理项目相关材料体系的制备方法、结构调控机制及性能评价指标，
            并结合阶段实验目标提出材料筛选与工艺优化建议。
          </p>
          <h3>关键词</h3>
          <p>材料研发；配方筛选；结构调控；性能评价</p>
        </article>
        <div v-else-if="previewKind === 'sheet'" class="sheet-preview">
          <table>
            <thead>
              <tr>
                <th>配方编号</th><th>PLA / phr</th><th>PBAT / phr</th>
                <th>相容剂 / phr</th><th>拉伸强度 / MPa</th>
              </tr>
            </thead>
            <tbody>
              <tr><td>F-301</td><td>70</td><td>30</td><td>2.0</td><td>48.6</td></tr>
              <tr><td>F-302</td><td>65</td><td>35</td><td>2.5</td><td>46.9</td></tr>
              <tr><td>F-303</td><td>60</td><td>40</td><td>3.0</td><td>44.7</td></tr>
            </tbody>
          </table>
        </div>
        <article v-else-if="previewKind === 'slide'" class="slide-preview">
          <span>项目中期汇报</span>
          <h2>{{ previewDocument.related_content }}</h2>
          <p>阶段研究进展与性能验证</p>
          <small>{{ previewDocument.version_label }}</small>
        </article>
        <article v-else class="word-preview">
          <h2>{{ previewDocument.name }}</h2>
          <dl>
            <div><dt>文档分类</dt><dd>{{ previewDocument.category_label }}</dd></div>
            <div><dt>关联内容</dt><dd>{{ previewDocument.related_content }}</dd></div>
            <div><dt>业务版本</dt><dd>{{ previewDocument.version_label }}</dd></div>
            <div><dt>更新人员</dt><dd>{{ previewDocument.uploaded_by_name }}</dd></div>
          </dl>
          <h3>项目实施方案</h3>
          <p>
            围绕材料性能、加工稳定性与实验可追溯性开展方案验证，形成标准化实验流程、
            阶段成果及完整的数据链路。
          </p>
        </article>
      </div>
      <template #footer>
        <el-button
          type="primary"
          @click="previewDocument && downloadDocument(previewDocument)"
        >
          下载原文件
        </el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.documents-layout {
  display: grid;
  min-height: 440px;
  grid-template-columns: 192px minmax(0, 1fr);
  margin-top: 14px;
  overflow: hidden;
  background: var(--color-paper);
  border: 1px solid var(--color-rule);
  border-radius: 8px;
  box-shadow: var(--shadow-whisper);
}

.folder-panel {
  padding: 16px 10px;
  background: var(--color-paper-2);
  border-right: 1px solid var(--color-rule);
}

.folder-panel h2 {
  padding: 0 10px 10px;
  margin: 0;
  font-size: 15px;
}

.folder-panel button {
  display: grid;
  width: 100%;
  height: 40px;
  align-items: center;
  grid-template-columns: 20px 1fr auto;
  padding: 0 10px;
  color: var(--color-ink-2);
  font: inherit;
  text-align: left;
  background: transparent;
  border: 0;
  border-radius: 6px;
  cursor: pointer;
  gap: 7px;
}

.folder-panel button.active {
  color: var(--color-accent);
  font-weight: 650;
  background: var(--color-accent-soft);
}

.folder-panel svg {
  width: 17px;
  height: 17px;
}

.folder-panel b {
  color: var(--color-muted);
  font-size: 12px;
  font-weight: 500;
}

.documents-main {
  min-width: 0;
  padding: 14px;
}

.documents-toolbar {
  display: grid;
  align-items: center;
  grid-template-columns: minmax(260px, 320px) 1fr 130px 120px auto;
  gap: 10px;
}

.document-search {
  display: flex;
  height: 38px;
  align-items: center;
  padding: 0 11px;
  background: var(--color-paper);
  border: 1px solid var(--color-rule-2);
  border-radius: 5px;
  gap: 7px;
}

.document-search svg {
  flex: none;
  width: 17px;
  color: var(--color-muted);
}

.document-search input {
  width: 100%;
  color: var(--color-ink);
  font: inherit;
  background: transparent;
  border: 0;
  outline: 0;
}

.documents-toolbar select,
.upload-button,
.upload-form select,
.upload-form input {
  height: 38px;
  padding: 0 10px;
  color: var(--color-ink-2);
  font: inherit;
  background: var(--color-paper);
  border: 1px solid var(--color-rule-2);
  border-radius: 5px;
}

.upload-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0 14px;
  color: #ffffff;
  background: var(--color-accent);
  border-color: var(--color-accent);
  cursor: pointer;
  gap: 6px;
}

.upload-button:disabled {
  cursor: not-allowed;
  opacity: 0.48;
}

.upload-button svg {
  width: 16px;
}

.documents-table-scroll {
  margin-top: 12px;
  overflow-x: auto;
}

.documents-table {
  width: 100%;
  min-width: 880px;
  border-collapse: collapse;
}

.documents-table th,
.documents-table td {
  height: 58px;
  padding: 8px 12px;
  color: var(--color-ink-2);
  font-size: 13px;
  text-align: left;
  border-bottom: 1px solid var(--color-rule);
}

.documents-table th {
  height: 38px;
  color: var(--color-muted);
  font-weight: 550;
  background: var(--color-paper-2);
}

.documents-table th:first-child {
  width: 31%;
}

.file-name {
  display: flex;
  align-items: center;
  gap: 9px;
}

.file-name > i {
  display: grid;
  flex: none;
  width: 34px;
  height: 34px;
  color: var(--color-accent);
  font-size: 20px;
  font-style: normal;
  background: var(--color-accent-soft);
  border-radius: 6px;
  place-items: center;
}

.file-name strong,
.file-name small {
  display: block;
}

.file-name strong {
  max-width: 280px;
  overflow: hidden;
  color: var(--color-ink);
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-name small {
  margin-top: 2px;
  color: var(--color-muted);
  font-size: 11px;
}

.document-actions {
  display: flex;
  gap: 5px;
}

.document-actions button {
  display: grid;
  width: 30px;
  height: 30px;
  color: var(--color-muted);
  background: var(--color-paper);
  border: 1px solid var(--color-rule-2);
  border-radius: 5px;
  cursor: pointer;
  place-items: center;
}

.document-actions button:hover {
  color: var(--color-accent);
  background: var(--color-accent-soft);
  border-color: var(--color-accent);
}

.empty-row {
  height: 180px !important;
  color: var(--color-muted) !important;
  text-align: center !important;
}

.upload-form {
  display: grid;
  gap: 15px;
}

.upload-form > div {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.upload-form label {
  display: grid;
  gap: 7px;
  color: var(--color-ink-2);
}

.upload-form label > span {
  font-weight: 600;
}

.upload-form label i {
  color: var(--color-danger);
  font-style: normal;
}

.upload-form input[type="file"] {
  height: auto;
  padding: 8px;
}

.upload-form small {
  color: var(--color-muted);
}

.preview-body {
  min-height: 480px;
  max-height: 70vh;
  padding: 22px;
  overflow: auto;
  background: #eef1f5;
}

.preview-body > img {
  display: block;
  width: 100%;
  min-height: 520px;
  object-fit: contain;
  background: #ffffff;
  border: 0;
}

.word-preview,
.pdf-preview,
.sheet-preview,
.slide-preview {
  width: min(820px, 100%);
  min-height: 460px;
  padding: 48px 54px;
  margin: 0 auto;
  background: #ffffff;
  box-shadow: 0 12px 36px rgb(20 32 50 / 12%);
}

.word-preview h2 {
  margin: 0 0 28px;
  text-align: center;
}

.pdf-preview > span {
  float: right;
  color: var(--color-muted);
}

.pdf-preview h2 {
  padding: 58px 0 28px;
  margin: 0;
  font-size: 24px;
  text-align: center;
}

.pdf-preview h3 {
  margin-top: 28px;
  font-size: 15px;
}

.pdf-preview p {
  color: var(--color-ink-2);
  line-height: 1.9;
}

.word-preview dl {
  display: grid;
  grid-template-columns: 1fr 1fr;
  padding: 18px;
  border: 1px solid var(--color-rule);
  gap: 14px;
}

.word-preview dl div {
  display: flex;
  gap: 10px;
}

.word-preview dt {
  color: var(--color-muted);
}

.word-preview dd {
  margin: 0;
}

.word-preview h3 {
  margin-top: 30px;
}

.word-preview p {
  color: var(--color-ink-2);
  line-height: 1.9;
}

.sheet-preview {
  overflow-x: auto;
}

.sheet-preview table {
  width: 100%;
  border-collapse: collapse;
}

.sheet-preview th,
.sheet-preview td {
  padding: 12px;
  border: 1px solid #ccd3dc;
}

.sheet-preview th {
  background: #edf3fb;
}

.slide-preview {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  color: #ffffff;
  text-align: center;
  background: linear-gradient(135deg, #1e407e, #527fe8);
}

.slide-preview h2 {
  max-width: 620px;
  margin: 26px 0 12px;
  font-size: 30px;
}

@media (max-width: 900px) {
  .documents-layout {
    grid-template-columns: 156px minmax(0, 1fr);
  }

  .documents-toolbar {
    grid-template-columns: minmax(220px, 1fr) 120px 110px auto;
  }

  .documents-toolbar > span {
    display: none;
  }
}
</style>
