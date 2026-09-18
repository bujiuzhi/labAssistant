/** 业务选择器统一由 Element Plus 渲染，避免浏览器原生菜单覆盖页面控件。 */

import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const businessViews = [
  "../src/views/ElnView.vue",
  "../src/views/projects/ProjectListView.vue",
  "../src/views/projects/ProjectDetailView.vue",
  "../src/components/projects/ProjectDocumentsTab.vue",
];

test("业务页面不使用原生 select，统一使用 Element Plus 选择器", () => {
  for (const view of businessViews) {
    const source = readFileSync(new URL(view, import.meta.url), "utf8");
    assert.match(source, /<el-select\b/, view);
    assert.doesNotMatch(source, /<\/?select\b|<\/?option\b/, view);
  }
});
