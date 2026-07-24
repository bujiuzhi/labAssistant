# 2026-07-24 首轮远程开发记录

## 1. 开发范围

本批次完成可运行的首个纵向切片：

- 组织范围身份认证、Session 与 CSRF；
- 工作台及固定侧栏、顶部栏、路由标签页布局；
- 项目列表、筛选、新建、详情与乐观锁更新；
- PostgreSQL、Redis 远程开发基础设施；
- Django migrations、核心 API 测试与浏览器验收。

实验计划、ELN、检测结果、报告和系统管理当前只保留导航占位，不计入已交付功能。

## 2. 资产与运行边界

| 类型 | 位置或范围 |
|---|---|
| 远程代码 | `/home/bujiu/work/code/materials-lab-assistant` |
| 远程运行数据 | `/home/bujiu/work/data/docker/materials-lab-assistant` |
| PostgreSQL | 容器 `materials-lab-assistant-postgres`，服务器回环端口 `15432` |
| Redis | 容器 `materials-lab-assistant-redis`，服务器回环端口 `16379` |
| Django | 远程端口 `8000` |
| Vite | 远程端口 `5173` |

仓库新增 `backend/`、`frontend/`、`infra/`、`environment.yml`、迁移文件和本记录。
运行密钥保存在远程 `.env`，未进入 Git；依赖、缓存、构建产物和运行 PID 均被忽略。

## 3. 开发数据

- 组织：`LAB`；
- 权限：5 项；
- 角色：4 项；
- 开发管理员：`admin`，密码仅在远程初始化时注入；
- 项目：4 条种子数据；
- 浏览器验收项目：`PRJ-2026-000005`，完成创建、详情读取和版本更新。

上述数据均为开发数据，不得迁移到生产环境。

## 4. 验证结果

| 验证项 | 结果 |
|---|---|
| `ruff check backend` | 通过 |
| `pytest` | 7 项通过 |
| Django migration 一致性 | 无待生成迁移 |
| Vue 类型检查与生产构建 | 通过 |
| 存活与就绪健康检查 | HTTP 200，数据库就绪 |
| 浏览器主流程 | 登录、列表、新建、详情、编辑通过 |
| 浏览器控制台 | 未发现 warning 或 error |

## 5. 已知问题与后续处理

1. TypeScript 7.0.2 与当前 `vue-tsc` 存在兼容性问题，暂固定为已验证的 TypeScript 6.0.3。
2. Element Plus 当前采用整包引入，生产构建主分块约 856 KB；进入性能优化批次后改为自动按需引入和路由分包。
3. 当前使用 Django `runserver` 与 Vite 开发服务器，只适用于远程开发；生产部署需切换 Gunicorn/Uvicorn、Nginx、TLS 和静态资源构建。
4. 下一纵向切片应实现实验计划与 ELN 数据模型、接口、权限和页面。

## 6. 清理与重建边界

- 停止基础设施：在远程项目目录执行
  `docker compose --env-file .env -f infra/docker-compose.yml down`；
- 重建环境：保留仓库，重新创建同名 Conda 环境并执行 migrations；
- 清理开发数据时，只处理
  `/home/bujiu/work/data/docker/materials-lab-assistant`，不得扩大到其父目录；
- 数据库物理结构以 migrations 为唯一正式来源，开发种子数据以两个 management command 为边界。
