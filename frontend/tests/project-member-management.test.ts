/** 项目成员编辑必须使用已有 project.update 权限，避免引用不存在的权限码导致控件永久隐藏。 */

import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const projectDetail = readFileSync(
  new URL("../src/views/projects/ProjectDetailView.vue", import.meta.url),
  "utf8",
);

test("项目管理员可在项目编辑中提交成员组成", () => {
  assert.match(projectDetail, /hasPermission\("project\.update"\)/);
  assert.doesNotMatch(projectDetail, /project\.manage_members/);
  assert.match(projectDetail, /member_ids: basicForm\.memberIds/);
});
