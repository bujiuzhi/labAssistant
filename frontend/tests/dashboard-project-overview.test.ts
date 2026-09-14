import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const dashboardView = readFileSync(new URL("../src/views/DashboardView.vue", import.meta.url), "utf8");

test("项目总览应展示未结束项目及其状态", () => {
  assert.match(dashboardView, /overview_projects/);
  assert.match(dashboardView, /<h2>项目概览<\/h2>/);
  assert.match(dashboardView, /projectStatusLabel\(project\.status\)/);
  assert.match(dashboardView, /description="暂无未结束项目"/);
});
