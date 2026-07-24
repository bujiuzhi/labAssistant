# 电子实验记录本纵向切片开发记录

## 1. 变更范围

- 原型来源：`/Users/bujiuzhi/Downloads/实验助手/电子实验记录本/`。
- 独立项目路由：`/eln`。
- 排除范围：玄鉴品牌栏、交通灯、问题反馈、在线状态、左侧菜单和历史会话。
- 实现范围：实验列表、项目筛选、状态及数量、计划新增与复制、基础信息、动态配方表、
  自定义表格、自定义过程、过程图片与预览、实验结果、附件元数据、暂存、开始实验、
  完成状态只读、对象范围和乐观锁。

## 2. 新增资产

| 资产 | 用途 |
|---|---|
| `backend/apps/experiments/` | 实验计划、记录、参与人、API、服务和演示数据 |
| `backend/apps/experiments/migrations/0001_initial.py` | 创建 ELN 三张业务表及约束索引 |
| `backend/tests/test_experiment_api.py` | 创建、更新、对象范围、复制、状态流转和只读回归 |
| `frontend/src/api/experiments.ts` | 实验接口客户端 |
| `frontend/src/views/ElnView.vue` | 原型复刻页面和完整交互 |
| `frontend/public/prototype/` | 原型演示过程图片 |

数据库新增：

- `experiment`
- `experiment_record`
- `experiment_participant`

开发数据命令：`python backend/manage.py seed_development_experiments`。该命令按原型生成
进行中 19、未开始 23、已完成 52 条实验；可重复执行且不删除用户自行创建的记录。

## 3. 权限

新增权限码：

- `experiment.view`
- `experiment.view_all`
- `experiment.create`
- `experiment.update`
- `experiment.execute`

超级管理员和项目负责人拥有全部权限；研究人员不含 `view_all`；检测人员仅查看。
非全量查看用户按实验负责人或参与人过滤。已完成实验禁止更新。

## 4. 验证记录

远程环境：`~/work/code/materials-lab-assistant`，Conda 环境 `materials-lab-assistant`。

| 检查 | 结果 |
|---|---|
| Django 迁移 | `experiments.0001_initial` 已应用 |
| 后端测试 | `19 passed` |
| Vue 类型检查与生产构建 | 通过 |
| 页面身份与非空首屏 | 通过 |
| 原型数量 `19/23/52` | 通过 |
| 项目筛选 | 通过；PLA 项目进行中数量变为 3 |
| 编辑、保存、刷新持久化 | 通过 |
| 新增计划表单 | 通过 |
| 复制计划基础信息、配方和过程 | 通过；结果与图片按原型清空 |
| 未开始转进行中 | 通过 |
| 已完成记录只读 | 通过 |
| 过程图片预览 | 通过 |
| 控制台应用错误 | 无 |

浏览器验收产生的临时记录修改已通过演示数据命令恢复。

## 5. 已知边界

- 结果附件当前按原型仅保存文件名和大小，不保存二进制内容。
- 过程图片首期以受限制的数据 URL 写入记录；正式环境按 ADR-003 迁移到对象存储。
- 提交、退回、归档、修订和内容哈希属于后续合规阶段；OpenAPI 中相关未来接口已标记为 `planned`。
- 当前产品既有桌面最小宽度为 960px，本轮按原型桌面工作台验收，未新增移动端布局。
