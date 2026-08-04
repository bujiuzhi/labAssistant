/** 电子实验记录本列表滚动与拖拽边界的静态回归测试。 */

import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const elnView = readFileSync(
  new URL("../src/views/ElnView.vue", import.meta.url),
  "utf8",
);

test("实验列表隐藏滚动条但保留滚动能力", () => {
  assert.match(elnView, /\.experiment-list\s*\{[\s\S]*?overflow:\s*auto/);
  assert.match(elnView, /scrollbar-width:\s*none/);
  assert.match(elnView, /\.experiment-list::-webkit-scrollbar/);
  assert.match(elnView, /overscroll-behavior:\s*contain/);
});

test("拖拽边界并入列表卡片且不再占用独立网格列", () => {
  assert.equal((elnView.match(/class="layout-resizer"/g) ?? []).length, 1);
  assert.match(
    elnView,
    /<section class="experiment-list-card">[\s\S]*class="layout-resizer"[\s\S]*<\/section>\s*<\/aside>/,
  );
  assert.match(
    elnView,
    /grid-template-columns:\s*var\(--list-width\) minmax\(0, 1fr\)/,
  );
  assert.doesNotMatch(
    elnView,
    /grid-template-columns:\s*var\(--list-width\) 6px/,
  );
  assert.match(elnView, /\.layout-resizer\s*\{[\s\S]*?position:\s*absolute/);
  assert.match(elnView, /right:\s*-1px/);
});

test("拖拽边界悬停、聚焦和拖拽时显示蓝色阴影", () => {
  assert.match(elnView, /\.layout-resizer:hover::after/);
  assert.match(elnView, /\.layout-resizer:focus-visible::after/);
  assert.match(elnView, /\.eln-page\.is-resizing \.layout-resizer::after/);
  assert.match(
    elnView,
    /background:\s*color-mix\(in srgb, var\(--color-accent\) 62%, white\)/,
  );
  assert.match(elnView, /box-shadow:[\s\S]*?rgb\(8 124 240 \/ 22%\)/);
});
