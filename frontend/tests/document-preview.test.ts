/** 文档预览策略与文本解析回归测试。 */

import assert from "node:assert/strict";
import test from "node:test";

import {
  MAX_COMPONENT_PREVIEW_BYTES,
  decodeTextDocument,
  parseCsvPreview,
  resolveDocumentPreviewMode,
} from "../src/utils/documentPreview.ts";

test("常用格式优先选择对应预览组件", () => {
  assert.equal(resolveDocumentPreviewMode("docx", 1024), "docx");
  assert.equal(resolveDocumentPreviewMode(".XLSX", 1024), "spreadsheet");
  assert.equal(resolveDocumentPreviewMode("xls", 1024), "spreadsheet");
  assert.equal(resolveDocumentPreviewMode("pptx", 1024), "pptx");
  assert.equal(resolveDocumentPreviewMode("pdf", 1024), "pdf");
  assert.equal(resolveDocumentPreviewMode("gif", 1024), "image");
  assert.equal(resolveDocumentPreviewMode("csv", 1024), "text");
});

test("旧版或大体积办公文档使用服务端 PDF 兜底", () => {
  assert.equal(resolveDocumentPreviewMode("doc", 1024), "converted-pdf");
  assert.equal(resolveDocumentPreviewMode("odt", 1024), "converted-pdf");
  assert.equal(resolveDocumentPreviewMode("ppt", 1024), "converted-pdf");
  assert.equal(
    resolveDocumentPreviewMode("docx", MAX_COMPONENT_PREVIEW_BYTES + 1),
    "converted-pdf",
  );
  assert.equal(
    resolveDocumentPreviewMode("pdf", MAX_COMPONENT_PREVIEW_BYTES + 1),
    "native-pdf",
  );
});

test("文本预览支持 UTF-8、UTF-16 和中文传统编码兜底", () => {
  assert.equal(
    decodeTextDocument(new TextEncoder().encode("真实实验记录").buffer),
    "真实实验记录",
  );
  const utf16 = Uint8Array.from([0xff, 0xfe, 0x41, 0x00, 0x42, 0x00]);
  assert.equal(decodeTextDocument(utf16.buffer), "AB");
  const gb18030 = Uint8Array.from([0xd6, 0xd0, 0xce, 0xc4]);
  assert.equal(decodeTextDocument(gb18030.buffer), "中文");
});

test("CSV 预览正确处理引号、逗号和换行", () => {
  assert.deepEqual(
    parseCsvPreview('材料,结果\n"环氧,树脂","合格"\n"双""引号",2'),
    [
      ["材料", "结果"],
      ["环氧,树脂", "合格"],
      ['双"引号', "2"],
    ],
  );
});
