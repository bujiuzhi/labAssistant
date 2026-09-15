<script setup lang="ts">
import type { Component } from "vue";
import {
  computed,
  defineAsyncComponent,
  onBeforeUnmount,
  ref,
  shallowRef,
  watch,
} from "vue";

import { getProblemDetail } from "@/api/http";
import { projectApi } from "@/api/projects";
import type { ProjectDocument } from "@/types/api";
import {
  decodeTextDocument,
  parseCsvPreview,
  resolveDocumentPreviewMode,
  type DocumentPreviewMode,
} from "@/utils/documentPreview";

const VueOfficeDocx = defineAsyncComponent(async () => {
  await import("@vue-office/docx/lib/index.css");
  const module = await import("@vue-office/docx");
  return module.default as unknown as Component;
});
const VueOfficeExcel = defineAsyncComponent(async () => {
  await import("@vue-office/excel/lib/index.css");
  const module = await import("@vue-office/excel");
  return module.default as unknown as Component;
});
const VueOfficePptx = defineAsyncComponent(async () => {
  const module = await import("@vue-office/pptx");
  return module.default as unknown as Component;
});

const props = defineProps<{
  projectId: string;
  documentItem: ProjectDocument;
}>();

const previewMode = ref<DocumentPreviewMode>("unsupported");
const sourceData = shallowRef<ArrayBuffer | null>(null);
const nativePdfUrl = ref("");
const textContent = ref("");
const loading = ref(false);
const errorMessage = ref("");
let requestSequence = 0;
const activePreviewSequence = ref(0);

const normalizedExtension = computed(() =>
  props.documentItem.extension.trim().toLowerCase(),
);
const viewerKey = computed(
  () => `${props.documentItem.id}-${props.documentItem.updated_at}-${previewMode.value}`,
);
const imageUrl = computed(() =>
  projectApi.documentPreviewUrl(props.projectId, props.documentItem.id),
);
const csvRows = computed(() =>
  normalizedExtension.value === "csv" ? parseCsvPreview(textContent.value) : [],
);
const previewMethodLabel = computed(() => {
  const labels: Record<DocumentPreviewMode, string> = {
    docx: "Word 组件预览",
    spreadsheet: "Excel 组件预览",
    pptx: "PowerPoint 组件预览",
    pdf: "浏览器 PDF 预览",
    "native-pdf": "浏览器 PDF 预览",
    image: "图片预览",
    text: normalizedExtension.value === "csv" ? "CSV 表格预览" : "文本预览",
    unsupported: "暂不支持预览",
  };
  return labels[previewMode.value];
});
const previewEventHandlers = computed(() => {
  const sequence = activePreviewSequence.value;
  return {
    rendered: () => handleRendered(sequence),
    nativeError: () => handleNativeError(sequence),
    rendererError: () => handleRendererError(sequence),
  };
});

/**
 * 加载当前文档的首选预览内容
 */
async function loadPreview(): Promise<void> {
  const sequence = ++requestSequence;
  activePreviewSequence.value = sequence;
  const nextMode = resolveDocumentPreviewMode(
    props.documentItem.extension,
    props.documentItem.file_size,
  );
  previewMode.value = nextMode;
  sourceData.value = null;
  textContent.value = "";
  errorMessage.value = "";
  resetNativePdfUrl();
  loading.value = true;

  if (nextMode === "image") {
    loading.value = false;
    return;
  }
  if (nextMode === "unsupported") {
    loading.value = false;
    errorMessage.value = "该格式暂不支持在线预览，请下载原文件查看。";
    return;
  }

  try {
    const content = await projectApi.getDocumentContent(
      props.projectId,
      props.documentItem.id,
    );
    if (sequence !== requestSequence) return;
    if (nextMode === "text") {
      textContent.value = decodeTextDocument(content);
      loading.value = false;
    } else if (nextMode === "native-pdf") {
      nativePdfUrl.value = URL.createObjectURL(
        new Blob([content], { type: "application/pdf" }),
      );
      loading.value = false;
    } else {
      sourceData.value = content;
    }
  } catch (error) {
    if (sequence !== requestSequence) return;
    loading.value = false;
    const problem = getProblemDetail(error);
    errorMessage.value =
      problem?.detail ?? "文档内容读取失败，请重试或下载原文件查看。";
  }
}

/**
 * 当前版本未部署文档转换器；办公组件解析失败时明确提示下载，避免把原文件误标为 PDF。
 */
async function useServerPdfFallback(sequence: number): Promise<void> {
  if (sequence !== requestSequence) return;
  loading.value = false;
  errorMessage.value = "当前服务器未启用文档转换，请下载原文件使用本地软件查看。";
}

/** 清理当前 PDF Blob URL，避免连续预览时遗留浏览器内存。 */
function resetNativePdfUrl(): void {
  if (nativePdfUrl.value.startsWith("blob:")) URL.revokeObjectURL(nativePdfUrl.value);
  nativePdfUrl.value = "";
}

/**
 * 处理第三方预览组件的渲染错误
 */
function handleRendererError(sequence: number): void {
  if (sequence !== requestSequence) return;
  if (["docx", "spreadsheet", "pptx"].includes(previewMode.value)) {
    void useServerPdfFallback(sequence);
    return;
  }
  loading.value = false;
  errorMessage.value = "文档组件渲染失败，请重试或下载原文件查看。";
}

function handleRendered(sequence: number): void {
  if (sequence !== requestSequence) return;
  loading.value = false;
  errorMessage.value = "";
}

function handleNativeError(sequence: number): void {
  if (sequence !== requestSequence) return;
  loading.value = false;
  errorMessage.value = "浏览器无法显示该文件，请下载原文件查看。";
}

watch(
  () => [
    props.projectId,
    props.documentItem.id,
    props.documentItem.updated_at,
  ],
  () => void loadPreview(),
  { immediate: true },
);
onBeforeUnmount(() => {
  requestSequence += 1;
  resetNativePdfUrl();
});
</script>

<template>
  <section
    class="document-preview-viewer"
    :aria-busy="loading"
    :aria-label="`${documentItem.name} 在线预览`"
  >
    <header class="preview-method">
      <span>{{ previewMethodLabel }}</span>
      <small>{{ documentItem.extension.toUpperCase() }}</small>
    </header>

    <div v-if="loading" class="preview-status preview-loading" aria-live="polite">
      <span class="loading-ring" />
      <p>正在加载真实文档内容…</p>
    </div>

    <img
      v-if="previewMode === 'image' && !errorMessage"
      class="image-viewer"
      :src="imageUrl"
      :alt="documentItem.name"
      @load="previewEventHandlers.rendered"
      @error="previewEventHandlers.nativeError"
    />

    <iframe
      v-else-if="previewMode === 'native-pdf' && nativePdfUrl && !errorMessage"
      class="native-pdf-viewer"
      :src="nativePdfUrl"
      :title="`${documentItem.name} PDF 预览`"
      @load="previewEventHandlers.rendered"
      @error="previewEventHandlers.nativeError"
    />

    <VueOfficeDocx
      v-else-if="previewMode === 'docx' && sourceData && !errorMessage"
      :key="viewerKey"
      class="office-viewer"
      :src="sourceData"
      @rendered="previewEventHandlers.rendered"
      @error="previewEventHandlers.rendererError"
    />

    <VueOfficeExcel
      v-else-if="
        previewMode === 'spreadsheet' && sourceData && !errorMessage
      "
      :key="viewerKey"
      class="office-viewer spreadsheet-viewer"
      :src="sourceData"
      :options="{ showContextmenu: false }"
      @rendered="previewEventHandlers.rendered"
      @error="previewEventHandlers.rendererError"
    />

    <VueOfficePptx
      v-else-if="previewMode === 'pptx' && sourceData && !errorMessage"
      :key="viewerKey"
      class="office-viewer presentation-viewer"
      :src="sourceData"
      @rendered="previewEventHandlers.rendered"
      @error="previewEventHandlers.rendererError"
    />

    <div
      v-else-if="previewMode === 'text' && !errorMessage"
      class="text-viewer"
    >
      <div v-if="normalizedExtension === 'csv'" class="csv-table-scroll">
        <table v-if="csvRows.length">
          <thead>
            <tr>
              <th v-for="(cell, index) in csvRows[0]" :key="index">
                {{ cell || `第 ${index + 1} 列` }}
              </th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(row, rowIndex) in csvRows.slice(1)" :key="rowIndex">
              <td v-for="(cell, cellIndex) in row" :key="cellIndex">
                {{ cell }}
              </td>
            </tr>
          </tbody>
        </table>
        <p v-else>CSV 文件没有可显示的数据。</p>
      </div>
      <pre v-else>{{ textContent }}</pre>
    </div>

    <div v-if="errorMessage" class="preview-status preview-error" role="alert">
      <strong>无法完成在线预览</strong>
      <p>{{ errorMessage }}</p>
      <button
        class="ui-button ui-button--secondary"
        type="button"
        @click="loadPreview"
      >
        重新加载
      </button>
    </div>
  </section>
</template>

<style scoped>
.document-preview-viewer {
  position: relative;
  width: 100%;
  min-height: 520px;
  overflow: auto;
  background: #e9edf2;
}

.preview-method {
  position: sticky;
  z-index: 4;
  top: 0;
  display: flex;
  height: 38px;
  align-items: center;
  justify-content: space-between;
  padding: 0 14px;
  color: var(--color-ink-2);
  background: rgb(255 255 255 / 94%);
  border-bottom: 1px solid var(--color-rule);
  backdrop-filter: blur(8px);
}

.preview-method span {
  font-size: 13px;
  font-weight: 600;
}

.preview-method small {
  padding: 3px 8px;
  color: var(--color-muted);
  background: var(--color-paper-2);
  border-radius: 999px;
}

.preview-status {
  display: grid;
  min-height: 420px;
  place-content: center;
  justify-items: center;
  padding: 32px;
  color: var(--color-ink-2);
  text-align: center;
}

.preview-loading {
  position: absolute;
  z-index: 3;
  inset: 38px 0 0;
  background: rgb(238 241 245 / 88%);
}

.loading-ring {
  width: 32px;
  height: 32px;
  border: 3px solid #cdd8e6;
  border-top-color: var(--color-primary);
  border-radius: 50%;
  animation: preview-loading 0.8s linear infinite;
}

.preview-error strong {
  color: var(--color-danger);
  font-size: 17px;
}

.preview-error p {
  max-width: 560px;
  line-height: 1.7;
}

.preview-error button {
  height: 36px;
  padding: 0 18px;
  color: #fff;
  background: var(--color-primary);
  border: 0;
  border-radius: 6px;
  cursor: pointer;
}

.image-viewer {
  display: block;
  width: 100%;
  min-height: 520px;
  object-fit: contain;
  background: #fff;
}

.native-pdf-viewer {
  display: block;
  width: 100%;
  min-height: 66vh;
  background: #fff;
  border: 0;
}

.office-viewer {
  min-height: 66vh;
}

.spreadsheet-viewer,
.presentation-viewer,
.pdf-component-viewer {
  background: #fff;
}

.text-viewer {
  width: min(1080px, calc(100% - 32px));
  min-height: 460px;
  margin: 16px auto;
  overflow: auto;
  background: #fff;
  box-shadow: 0 10px 30px rgb(20 32 50 / 10%);
}

.text-viewer pre {
  min-width: max-content;
  padding: 28px 32px;
  margin: 0;
  color: #243247;
  font: 14px/1.8 ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  white-space: pre-wrap;
}

.csv-table-scroll {
  overflow: auto;
}

.csv-table-scroll table {
  min-width: 100%;
  border-spacing: 0;
  border-collapse: collapse;
  font-size: 13px;
}

.csv-table-scroll th,
.csv-table-scroll td {
  min-width: 120px;
  padding: 10px 12px;
  border: 1px solid #dce3ec;
  text-align: left;
  white-space: pre-wrap;
}

.csv-table-scroll th {
  position: sticky;
  z-index: 1;
  top: 0;
  color: #1f2d40;
  background: #f2f6fb;
}

.csv-table-scroll p {
  padding: 40px;
  text-align: center;
}

:deep(.vue-office-docx) {
  padding: 16px 0 28px;
  background: #e9edf2;
}

:deep(.vue-office-docx .docx-wrapper) {
  padding: 16px;
  background: transparent;
}

@keyframes preview-loading {
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 700px) {
  .document-preview-viewer {
    min-height: 420px;
  }

  .native-pdf-viewer,
  .office-viewer {
    min-height: 58vh;
  }

  .text-viewer {
    width: calc(100% - 16px);
    margin: 8px auto;
  }

  .text-viewer pre {
    padding: 18px;
  }
}
</style>
