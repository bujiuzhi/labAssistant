/** 项目文档列表字段与预览容器回归测试。 */

import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const projectDocumentsTab = readFileSync(
  new URL("../src/components/projects/ProjectDocumentsTab.vue", import.meta.url),
  "utf8",
);

test("文档列表和上传表单不再包含关联内容", () => {
  assert.doesNotMatch(projectDocumentsTab, /关联内容/);
  assert.doesNotMatch(projectDocumentsTab, /related_content|relatedContent/);
  assert.match(projectDocumentsTab, /<td colspan="6" class="empty-row">/);
});

test("文档预览弹窗使用响应式窄栏尺寸", () => {
  assert.match(
    projectDocumentsTab,
    /width="min\(960px, calc\(100vw - 32px\)\)"/,
  );
  assert.match(
    projectDocumentsTab,
    /height:\s*clamp\(360px, 64vh, 680px\)/,
  );
});
