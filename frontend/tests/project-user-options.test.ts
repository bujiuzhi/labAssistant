/** 项目人员选项必须与当前组织内的接口契约一致。 */

import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const projectList = readFileSync(
  new URL("../src/views/projects/ProjectListView.vue", import.meta.url),
  "utf8",
);
const apiTypes = readFileSync(
  new URL("../src/types/api.ts", import.meta.url), "utf8");

test("项目人员选项不依赖当前接口未返回的组织名称", () => {
  const optionType = apiTypes.match(/export interface OrganizationUserOption \{[\s\S]*?\n\}/)?.[0] ?? "";
  assert.doesNotMatch(optionType, /organization_(id|name)/);
  assert.doesNotMatch(projectList, /organization_name/);
  assert.match(projectList, /:label="`\$\{user\.display_name\}（\$\{user\.username\}）`"/);
  assert.match(projectList, /placeholder="输入姓名或账号筛选"/);
});
