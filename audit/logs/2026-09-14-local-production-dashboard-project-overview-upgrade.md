# 本机生产主页项目概览升级

- 操作时间：2026-09-14 16:47:18（Asia/Shanghai）
- 环境：本机受控 Compose，项目名 `materials-lab-local-production`，仅监听 `127.0.0.1:15105`
- 发布版本：`f61f7da`（`feat: 展示主页未结束项目卡片`）
- 数据范围：既有本机生产 PostgreSQL 与 RustFS 数据保留，未执行初始化、重置或迁移。

## 执行与验证

1. 通过 `scripts/production.sh ... build` 构建 API 与 Web 发布镜像，并生成对应发布清单。
2. 通过 `scripts/production.sh ... upgrade --confirm materials-lab-local-production` 在停止 Web/API 前创建恢复组，再重建 API 与 Web；数据库与对象存储未重建。
3. 恢复组：`20260914-164645-72209`，包含 PostgreSQL 转储与 RustFS 数据归档；尚未做隔离恢复演练。
4. `scripts/production.sh ... check` 返回 API、数据库、对象存储均为 `ready`；`status` 显示 API/Web 均使用 `f61f7da` 且健康；`http://127.0.0.1:15105/` 返回 HTTP 200。

## 边界与恢复

升级保留两项历史一次性初始化容器，它们均为退出状态，不影响当前运行服务；本次未用 `--remove-orphans` 清理。

如需恢复，先停止写入，再使用该恢复组按 [生产部署说明](../../docs/production-deployment.md) 的恢复步骤在隔离环境验证，确认迁移兼容性后再决定回退。业务界面中具体项目卡片的可视化验收仍待登录后的用户确认。
