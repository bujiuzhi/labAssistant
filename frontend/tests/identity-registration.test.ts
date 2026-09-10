/** 邀请注册和自助改密入口的静态回归测试。 */

import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const authApi = readFileSync(new URL("../src/api/auth.ts", import.meta.url), "utf8");
const loginView = readFileSync(new URL("../src/views/LoginView.vue", import.meta.url), "utf8");
const appShell = readFileSync(new URL("../src/layouts/AppShell.vue", import.meta.url), "utf8");
const userManagement = readFileSync(new URL("../src/views/system/UserManagementView.vue", import.meta.url), "utf8");

test("注册仅通过邀请码接口，且前端不承诺自动登录", () => {
  assert.match(authApi, /"\/auth\/register"/);
  assert.match(loginView, /邀请码由组织超级管理员签发/);
  assert.match(loginView, /注册完成，请使用新密码登录/);
});

test("超级管理员可签发和撤销邀请码，所有登录用户都有自助改密入口", () => {
  assert.match(userManagement, /createRegistrationInvitation/);
  assert.match(userManagement, /revokeRegistrationInvitation/);
  assert.match(userManagement, /邀请码仅显示一次/);
  assert.match(appShell, /修改密码/);
  assert.match(authApi, /"\/auth\/password"/);
  assert.match(appShell, /sessionStore\.clearSession/);
});
