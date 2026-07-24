# 项目编辑能力实现记录

## 1. 触发原因

项目详情原有“编辑”弹窗只有展示绑定，“保存修改”仅关闭弹窗，没有提交项目 API，用户无法修改并持久化项目。

## 2. 编辑边界

依据原型 `项目数据/project-detail.js`，本次接通以下字段：

- 项目名称、项目类型、项目经理。
- 计划开始时间、计划结束时间。
- 人员组成。
- 指标性研发目标，多行分别保存为目标列表。
- 项目里程碑的新增、修改、删除、日期和状态。

项目编号、项目进度、文档/实验/数据资源计数和项目状态不在本表单直接编辑。

## 3. 权限与状态

- 超级管理员和项目负责人可编辑。
- 研究人员、检验人员只读。
- 草稿、待开始、进行中、有风险和暂停项目可编辑。
- 已完成、已归档项目只读。
- 人员组成更新额外校验 `project.manage_members`。

## 4. 变更资产

| 资产域 | 说明 |
|---|---|
| `frontend/src/views/projects/ProjectDetailView.vue` | 基础信息、人员和里程碑真实编辑表单 |
| `frontend/src/api/users.ts` | 当前组织用户选择项接口 |
| `backend/apps/projects/` | 业务编号解析、扩展字段、成员同步和乐观锁更新 |
| `backend/apps/identity/` | 当前组织有效用户选项接口 |
| `backend/tests/` | 业务编号更新、成员同步、归档只读与用户范围回归 |
| `contracts/openapi.yaml` | 项目标识、成员字段和用户选项契约 |

## 5. 数据变更

远程部署需先执行项目迁移 `projects.0002_project_prototype_fields`，再运行
`seed_development_projects` 初始化 20 个原型项目。迁移只增加项目阶段、进度、计数、目标和里程碑字段，不删除现有数据。

## 6. 远程执行与验证

- 数据备份：`/home/bujiu/work/data/docker/materials-lab-assistant/backups/projects-before-editing-20260724.json`。
- 迁移：`projects.0002_project_prototype_fields` 执行成功。
- 初始化：保留并更新 4 个旧项目，新增 16 个，共 20 个原型项目；再次执行为新增 0、更新 20。
- 后端回归：`11 passed`。
- 前端检查：Vue 类型检查与 Vite 生产构建通过。
- 真实 API：以管理员会话按 `PRJ-2026-PI-005` 读取项目，使用 `If-Match: "1"` 保存基础信息、成员和里程碑，
  再次读取返回版本 `2` 及保存后的目标、3 个成员和3条里程碑。
- 运行状态：后端健康检查返回 `{"status":"ok"}`。
- 浏览器插件仍拒绝访问本地转发地址，未声明截图与浏览器交互验收通过。
