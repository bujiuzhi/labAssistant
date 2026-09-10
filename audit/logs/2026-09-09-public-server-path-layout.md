# 公网服务器部署路径调整记录

时间：2026-09-09 16:45:00（Asia/Shanghai）

## 决策

- 公网服务器的项目代码、部署配置、生产密钥和 TLS 证书统一位于 `~/work/server/labAssistant`，避免与其他项目共用目录；不再使用 `~/work/code/labAssistant` 作为生产部署根目录。
- PostgreSQL 数据、RustFS 数据和日志、恢复组备份、发布清单及隔离验收数据统一位于 `~/work/data/labAssistant`。
- 生产脚本拒绝 `~/work/code/labAssistant/data` 及未分项目的 `~/work/server/data` 等旧私有配置路径，只接受数据根及实际项目部署目录 `~/work/server/labAssistant/data/` 子目录，避免代码、密钥和持久化数据混放或误挂载。

## 验证与恢复

- 更新生产环境模板、部署文档、开发/服务器操作说明、隔离验收路径和运维脚本路径校验。
- 未连接公网服务器，未创建、迁移或删除任何服务器目录、容器、数据或密钥。
