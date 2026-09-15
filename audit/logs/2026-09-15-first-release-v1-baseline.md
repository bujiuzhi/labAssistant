# 2026-09-15 首版 V1 基线收敛

## 决策

首个正式发布仅保留 `V1__materials_lab_schema.sql`。该迁移覆盖已确认的多租户 RBAC、平台控制面账号、邀请码、逻辑删除、项目/实验对象元数据及 RustFS 关联结构。旧的 V2–V4 文件和只面向旧双身份数据的一次性升级容器已移除。

V1 只适用于尚未初始化的空数据库。正式发布后 V1 固定不再改写；后续正式结构变更从 V2 追加。

## 影响与恢复

已有本机生产库记录的是旧 V1–V3 历史，不能直接运行此首版基线。若要切换到本基线，须经明确授权停止服务、备份后清空该项目的 PostgreSQL 与 RustFS 数据目录，再执行 `bootstrap`；不得通过改写 Flyway 历史或 `repair` 绕过。

本次未执行清库、迁移、重启或发布，现有部署继续运行先前镜像。

## 验证

- `pnpm --dir frontend test`：24 项通过。
- `node --test scripts/tests/production.test.mjs`：38 项通过。
- `docker build --target build --file infra/Dockerfile.api .`：Java 25 构建及 75 项后端测试通过。
- 临时无卷 PostgreSQL 18.4 容器成功执行 V1 DDL，创建 20 张业务表，并验证平台管理员与组织超级管理员的合法归属；容器已自动删除。
- `git diff --check`：通过。
