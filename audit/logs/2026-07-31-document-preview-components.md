# 常用文档组件预览补齐记录

## 变更目标

将项目文档从“所有办公格式统一嵌入服务端 PDF”调整为“专用组件优先、服务端转换兜底”，
并补齐常用办公、开放文档、文本和图片格式的真实预览能力。

## 资产边界

- 前端组件：`DocumentPreviewViewer.vue`。
- 格式策略：`frontend/src/utils/documentPreview.ts`。
- 后端格式单一来源：`backend/apps/projects/document_formats.py`。
- 上传安全：扩展名、文件签名、可信 MIME、办公压缩包体积与路径检查。
- 服务端兜底：沿用受权限保护的 `/preview` 接口和 RustFS PDF 缓存。
- 第三方依赖：固定版本的 `@vue-office/docx`、`excel`、`pdf`、`pptx` 与 `vue-demi`。

## 格式矩阵

| 格式 | 首选实现 | 兜底 |
|---|---|---|
| DOCX、XLS/XLSX、PPTX、PDF | Vue Office 专用组件 | LibreOffice PDF 或浏览器 PDF |
| DOC、PPT、RTF、ODT、ODS、ODP | LibreOffice PDF | 下载原件 |
| TXT、CSV | 文本或有界 CSV 表格组件 | 大文件 PDF |
| PNG、JPG/JPEG、WebP、GIF、BMP | 浏览器图片组件 | 下载原件 |

## 安全与性能

- 不调用 Google Docs、Microsoft Office Online 等第三方公网预览服务。
- 组件只读取当前 Session 有权访问的同源二进制接口。
- 前端组件文件上限 25 MB，文本直接解析上限 5 MB。
- CSV 最多预览 200 行、50 列，避免大文件阻塞浏览器。
- 办公压缩包最多 10,000 个条目、解压后 512 MB，拒绝加密文件和目录穿越路径。
- 组件错误自动回退，不以模拟文本替代真实文件。

## 验证清单

- 后端 46 项测试通过。
- 前端 6 项单元测试通过。
- TypeScript 检查和 Vite 生产构建通过。
- 远程浏览器分别验证 DOCX、XLSX、PPTX、PDF 的组件渲染。
- 验证页面无空白、无框架错误覆盖层，相关控制台错误已清零。

## 远程实测结果

验证环境为 `192.168.0.156`，原件从 RustFS 私有存储读取，测试项目为
`PRJ-2026-REC-017`：

| 文件 | 接口结果 | 页面证据 |
|---|---|---|
| `项目实施方案V3.docx` | 原件 200，37,000 字节 | Word 组件显示正文与项目编号表格 |
| `第三轮配方筛选记录.xlsx` | 原件 200，5,122 字节 | Excel 组件生成 1 个 Canvas、6 个表格节点并显示样品数据 |
| `项目中期汇报-202607.pptx` | 原件 200，28,410 字节 | PowerPoint 组件显示标题及项目内容 |
| `废旧复合材料化学回收工艺验证研究综述.pdf` | 原件 200，2,550 字节 | PDF 组件生成 Canvas 并显示 PDF 正文 |

服务端兜底接口对上述四份文件均返回真实 `application/pdf`，转换结果分别为
29,106、2,550、22,131、14,087 字节。另用 LibreOffice 24.2.7.2 将真实样例转换为
DOC、ODT、RTF、XLS、ODS、PPT、ODP，再逐一转回 PDF，七种格式均生成有效单页 PDF。

首次浏览器验证发现原件请求自定义 `Accept: application/octet-stream` 后被 DRF 内容协商
提前返回 406。已删除二进制接口的错误 `Accept` 覆盖，保留 `responseType: arraybuffer`
和 60 秒超时；修复后四个原件接口均返回 200，浏览器应用控制台无错误或警告。
