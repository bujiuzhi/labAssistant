# 原型保真重构记录

## 1. 触发原因

用户验收指出首个纵向切片采用通用后台布局，未严格复刻
`/Users/bujiuzhi/Downloads/实验助手` 原型。2026-07-24 起将当前原型源码和最新 QA 截图作为
独立 Web 项目的强制视觉基线。

## 2. 本批资产

| 资产 | 动作 | 说明 |
|---|---|---|
| `docs/12-prototype-fidelity-ledger.md` | 新建 | 页面映射、视觉约束、差异和验收方法 |
| `frontend/src/layouts/AppShell.vue` | 重构 | 玄鉴顶栏、浅色侧栏、三项产品导航 |
| `frontend/src/views/DashboardView.vue` | 重构 | 双指标组、项目图表、三列项目卡片 |
| `frontend/src/components/dashboard/PrototypeCharts.vue` | 新建 | ECharts 环图和 30 天趋势图 |
| `frontend/src/views/projects/ProjectListView.vue` | 重构 | 原型筛选、项目表格和右侧新建抽屉 |
| `frontend/src/views/ElnView.vue` | 新建 | 实验列表、基础信息、配方、过程与结果编辑 |
| `frontend/src/data/prototype-dashboard.ts` | 新建 | 前端保真阶段的原型展示数据 |
| `backend/apps/projects/migrations/0002_project_prototype_fields.py` | 新建 | 项目阶段、进度、数量、目标和里程碑字段 |
| `seed_development_projects` | 重构 | 按原型初始化 20 个项目和 92 个实验计数 |
| `contracts/openapi.yaml` | 更新 | 同步会话角色和项目扩展字段 |

## 3. 远程执行状态

- 原型静态服务：远程 `4173`，本地转发 `14173`。
- 独立 Web：远程 Vite `5173`，Django `8000`；本地转发 `15173`、`18000`。
- 全局外壳与项目总览已同步远程，`pnpm run build` 通过，Vite `/dashboard` 返回 `200`。
- 项目数据、ELN 和后端字段完成本地同步源修改后，远程同步授权被平台配额拦截，尚未上传和执行迁移。
- 内置浏览器当前拒绝访问两个本地转发地址，因此尚未完成实现截图和交互验收。

## 4. 清理边界

如需撤回本批：

1. 先回退本记录“本批资产”中的代码和文档变更。
2. 若迁移已执行，再通过 Django migration 回退 `projects.0002`。
3. 演示项目由 `seed_development_projects` 以 `project_no` 幂等维护；需要清理时必须限定组织代码
   `LAB` 和本命令内 20 个明确编号，禁止删除其他项目。
