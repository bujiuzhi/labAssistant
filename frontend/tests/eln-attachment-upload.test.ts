/** 电子实验记录本附件上传状态的静态回归测试。 */

import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const elnView = readFileSync(
  new URL("../src/views/ElnView.vue", import.meta.url),
  "utf8",
);
const experimentApi = readFileSync(
  new URL("../src/api/experiments.ts", import.meta.url),
  "utf8",
);

test("未保存的实验草稿不提供附件选择控件", () => {
  assert.match(
    elnView,
    /const canUploadAttachments = computed\([\s\S]*?canEdit\.value && selectedExperiment\.value !== null/,
  );
  assert.match(
    elnView,
    /v-if="canUploadAttachments" class="inline-upload-action"[\s\S]*?保存实验计划后可上传图片/,
  );
  assert.match(
    elnView,
    /v-if="canUploadAttachments" class="inline-upload-action"[\s\S]*?保存实验计划后可上传附件/,
  );
});

test("结果附件不限制格式并支持单文件 300 MB 上传时限", () => {
  assert.match(elnView, /<input type="file" multiple @change="addResultFiles" \/>/);
  assert.match(elnView, /file\.size > 300 \* 1024 \* 1024/);
  assert.match(elnView, /超过 300 MB/);
  assert.match(experimentApi, /\{ timeout: 600_000 \}/);
});
