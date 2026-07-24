# 项目详情业务页签开发记录

## 1. 变更边界

本次按 `/Users/bujiuzhi/Downloads/实验助手/项目数据` 中的当前原型补齐项目详情导航：

- “文档资料”实现分类统计、关键词/文件类型/更新时间筛选、上传、列表、预览和下载。
- “实验管理”复用现有实验与 ELN 聚合接口，实现项目内分组列表、详情、计划新增/编辑和 ELN 定位。
- “数据资产”按原型保留“研发中，敬请期待”，未自行扩展数据建模和接口。

## 2. 新增资产

| 类型 | 资产 |
|---|---|
| 数据表 | `project_document`，迁移 `backend/apps/projects/migrations/0003_project_document.py` |
| 后端接口 | 项目文档列表/上传、内容预览/下载 |
| 后端命令 | `seed_development_documents`，幂等补齐原型演示文档 |
| 前端组件 | `ProjectDocumentsTab.vue`、`ProjectExperimentsTab.vue`、`ProjectDataAssetsTab.vue` |
| 权限 | `document.view`、`document.upload` |
| 测试 | `backend/tests/test_project_document_api.py` |

项目文档二进制文件进入 `MEDIA_ROOT/project-documents/{organization_id}/{project_id}/`，
数据库仅保存文件路径和可查询元数据。清空重做时先删除 `project_document` 记录，再按同一业务前缀清理文件，
不得扩大到整个媒体目录。

## 3. 数据与迁移

远程开发库应用 `projects.0003_project_document` 后执行：

```bash
conda run -n materials-lab-assistant python3 backend/manage.py seed_development_documents
```

命令以项目和文件名幂等创建合成演示资料，不覆盖用户上传文件，并同步项目 `document_count`。

## 4. 验证证据

2026-07-24 在远程 PostgreSQL 开发环境和 `1660 × 950` 浏览器视口完成：

- Django 测试：21 项通过。
- Vue 生产构建通过。
- 文档分类、PDF 预览、实验状态筛选、计划编辑弹窗、ELN 精确定位和数据资产占位均通过。
- 浏览器控制台错误与警告为 0。

验收截图已归档：

- `audit/evidence/development/materials-lab-project-documents-2026-07-24.png`
- `audit/evidence/development/materials-lab-project-experiments-2026-07-24.png`
- `audit/evidence/development/materials-lab-project-data-assets-2026-07-24.png`
