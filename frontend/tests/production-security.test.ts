/** 生产前端安全边界的静态回归测试。 */

import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const loginView = readFileSync(new URL("../src/views/LoginView.vue", import.meta.url), "utf8");
const documentPreview = readFileSync(
  new URL("../src/components/projects/DocumentPreviewViewer.vue", import.meta.url),
  "utf8",
);
const mainEntry = readFileSync(new URL("../src/main.ts", import.meta.url), "utf8");

test("生产登录页不应打包开发账号或默认口令", () => {
  assert.doesNotMatch(loginView, /开发账号|00000000|development-accounts/);
});

test("登录页不展示用户不可操作的会话实现提示", () => {
  assert.doesNotMatch(loginView, /登录会话仅保存在受保护的浏览器 Cookie 中/);
});

test("图标与PDF预览不应依赖运行时第三方资源", () => {
  assert.match(documentPreview, /浏览器 PDF 预览/);
  assert.doesNotMatch(documentPreview, /@vue-office\/pdf|unpkg\.com/);
  assert.match(mainEntry, /@iconify\/vue\/offline/);
  assert.match(mainEntry, /@iconify-json\/tabler\/icons\.json/);
});
