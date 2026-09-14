# 2026-09-11 本机生产身份数据重置

- 操作时间：2026-09-11 17:49:09 至 17:50:33，Asia/Shanghai
- 环境：本机 Docker Compose 项目 `materials-lab-local-production`，公开入口 `127.0.0.1:15105`
- 数据目录：`/Users/bujiu/work/data/labAssistant/local-production-88b0b8d`
- 操作授权：用户明确确认清空非平台组织及其旧账号数据，并重置为 0 个业务组织。

## 备份、范围与执行

- 执行前通过 `bash scripts/production.sh --env .env.production backup --confirm materials-lab-local-production` 停止 API/Web 写入口并生成恢复组：`backups/20260911-174909-40440.dump` 与同名 RustFS 归档、校验文件和元数据文件。
- 在单一数据库事务中仅删除非平台组织及其账号、角色、用户角色、角色权限、邀请码和关联业务记录；未删除 `PLATFORM`、`platform_admin`、全局权限字典、Flyway 历史或备份文件。
- 删除前无项目、实验或文档记录，因此没有业务文件正文需要从 RustFS 删除。
- 完成后使用受控 `up` 恢复 API/Web；未执行 bootstrap、身份升级、迁移或数据库结构变更。

## 验证与恢复

- 已验证：仅剩 `platform_admin`，其标记为 `is_platform_admin=true`、`is_super_admin=false`，且归属内部 `PLATFORM` 组织；业务组织、业务账号、角色、用户角色、邀请码、项目、实验和文档计数均为 0。
- 已验证：`bash scripts/production.sh --env .env.production check` 与 `GET /api/v1/health/ready` 通过，PostgreSQL、RustFS、API 与 Web 健康。
- 保留项：历史一次性 bootstrap/identity-upgrade 容器仍为已退出状态；未在本次执行 `--remove-orphans` 或删除容器。
- 恢复要点：如需恢复清空前的本机业务数据，停止写入口后仅使用该恢复组的 `restore-new` 恢复到新的隔离 Compose 项目和全新数据目录，禁止覆盖当前已重置环境。
