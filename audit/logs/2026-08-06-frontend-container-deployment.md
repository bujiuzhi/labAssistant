# 前端容器化部署恢复记录

- 变更时间：2026-08-06
- 变更范围：远程开发环境的 Vue/Vite 前端服务
- 数据影响：无数据库、接口或业务数据变更

## 原因

远程开发机原前端以宿主机 Vite 进程运行。更新后重启时发现非交互 SSH 环境未提供 Node/npm 路径，无法可靠恢复该进程。

## 变更资产

- `infra/docker-compose.yml`：新增 `materials-lab-assistant-frontend` 服务，以项目独立的 Node 容器运行 Vite，并使用 `unless-stopped` 自动恢复。
- `frontend/vite.config.ts`：将 API 代理地址改为可配置环境变量；宿主机开发仍默认 `127.0.0.1:8000`，Compose 服务使用 `http://api:8000`。
- `.env.example`：补充前端端口和绑定地址说明；开发环境默认开放局域网访问，生产环境应改为 `127.0.0.1`。

## 验证计划

- 前端 `npm run build` 与 `npm test` 已在提交前通过。
- 远程更新后通过 Compose 启动前端服务，并验证 `5173` 页面响应、`/api` 代理可用及全部基础设施服务运行。
