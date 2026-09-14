/** 电子实验记录本附件上传状态的静态回归测试。 */

import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const elnView = readFileSync(
  new URL("../src/views/ElnView.vue", import.meta.url),
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
