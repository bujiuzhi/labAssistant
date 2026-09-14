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

test("首次上传会自动暂存计划，再将附件关联到草稿", () => {
  assert.match(
    elnView,
    /const canUploadAttachments = computed\(\(\) => canEdit\.value\)/,
  );
  assert.match(
    elnView,
    /async function ensureAttachmentExperiment\(\): Promise<Experiment \| null>[\s\S]*?experimentApi\.create\(payloadFromEditor\(\)\)/,
  );
  assert.equal(
    (elnView.match(/const current = await ensureAttachmentExperiment\(\);/g) ?? [])
      .length,
    2,
  );
  assert.match(elnView, /已自动暂存实验计划，正在上传附件/);
  assert.doesNotMatch(elnView, /保存实验计划后可上传/);
});

test("结果附件不限制格式并支持单文件 300 MB 上传时限", () => {
  assert.match(elnView, /<input type="file" multiple @change="addResultFiles" \/>/);
  assert.match(elnView, /file\.size > 300 \* 1024 \* 1024/);
  assert.match(elnView, /超过 300 MB/);
  assert.match(experimentApi, /\{ timeout: 600_000 \}/);
});
