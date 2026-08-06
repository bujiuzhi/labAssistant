/** 全局按钮设计系统的静态回归测试。 */

import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const buttonStyles = readFileSync(
  new URL("../src/styles/buttons.css", import.meta.url),
  "utf8",
);
const designTokens = readFileSync(
  new URL("../src/styles/tokens.css", import.meta.url),
  "utf8",
);
const userManagementView = readFileSync(
  new URL("../src/views/system/UserManagementView.vue", import.meta.url),
  "utf8",
);

test("按钮基础参数符合统一设计规范", () => {
  assert.match(buttonStyles, /height:\s*var\(--button-height\)/);
  assert.match(buttonStyles, /padding:\s*0 var\(--button-padding-x\)/);
  assert.match(buttonStyles, /border-radius:\s*var\(--radius-control\)/);
  assert.match(designTokens, /--button-height:\s*36px/);
  assert.match(designTokens, /--button-padding-x:\s*16px/);
  assert.match(designTokens, /--radius-control:\s*6px/);
  assert.match(buttonStyles, /font-size:\s*14px/);
  assert.match(buttonStyles, /font-weight:\s*500/);
  assert.match(buttonStyles, /gap:\s*6px/);
});

test("按钮四种语义及交互状态均有正式定义", () => {
  for (const variant of ["primary", "secondary", "light", "danger"]) {
    assert.match(buttonStyles, new RegExp(`ui-button--${variant}`));
  }
  for (const state of [":hover", ":active", ":focus-visible", ":disabled"]) {
    assert.ok(buttonStyles.includes(state), `缺少 ${state} 状态`);
  }
  assert.match(buttonStyles, /transform:\s*scale\(0\.97\)/);
  assert.match(buttonStyles, /outline-offset:\s*2px/);
  assert.match(buttonStyles, /:is\(body, #app\)/);
});

test("页面与卡片间距使用统一令牌", () => {
  assert.match(designTokens, /--page-padding-x:\s*20px/);
  assert.match(designTokens, /--space-card:\s*16px/);
  assert.match(designTokens, /--space-card-title:\s*12px/);
  assert.match(designTokens, /--space-control:\s*8px/);
});

test("危险和禁用按钮使用规定的语义颜色", () => {
  assert.match(buttonStyles, /outline-color:\s*var\(--color-danger\)/);
  assert.match(buttonStyles, /background:\s*var\(--color-danger-soft\)/);
  assert.match(buttonStyles, /background:\s*var\(--color-paper-2\)/);
  assert.match(buttonStyles, /cursor:\s*not-allowed/);
});

test("用户管理操作按钮保持单行排列", () => {
  assert.match(userManagementView, /class="user-row-actions"/);
  assert.match(userManagementView, /flex-wrap:\s*nowrap/);
  assert.match(userManagementView, /\.el-button \+ \.el-button/);
  assert.match(userManagementView, /margin-left:\s*0/);
});
