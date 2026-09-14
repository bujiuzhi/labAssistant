# 2026-09-11 零业务组织首次初始化

- 操作时间：2026-09-11 16:48:15，Asia/Shanghai
- 仓库与分支：`labAssistant`，`dev`
- 代码基线：`3cf9427 docs: 记录本机生产身份升级` 之后的未提交重构工作区
- 本次对象：生产 bootstrap、Compose Secret、生产脚本、隔离生产验收、身份初始化测试与正式部署文档

## 决策与影响

- 全新生产空库的 `bootstrap` 只创建内部平台组织、一个平台管理员和全局权限字典；业务组织、组织超级管理员、角色、用户角色、项目和测试数据均为零。
- 平台管理员仅使用组织目录与开通接口。平台开通一个业务组织时，在单个事务中创建该组织、三个内置角色、角色权限和首个组织超级管理员；平台管理员本身不取得租户用户管理或业务数据权限。
- 删除首次部署所需的 `BOOTSTRAP_ORGANIZATION_*`、`BOOTSTRAP_ADMIN_*` 配置和组织管理员 Secret。新环境只需配置并保管平台管理员凭据。
- 已部署环境的数据和现有业务组织不在本次重构范围内，未执行清库、迁移、重启或重建。新规则仅在使用新版本对全新空库执行 `bootstrap` 时生效。

## 验证与恢复

- 已执行：`bash -n scripts/production.sh`；`node --test scripts/tests/production.test.mjs`（39 项通过）；项目固定 `maven:3.9.12-eclipse-temurin-25` Docker 构建阶段的 `mvn --batch-mode --no-transfer-progress verify`（70 项通过）；`pnpm --dir frontend run build`；`docker compose --project-directory infra --env-file .env.production -f infra/compose.production.yml config -q`；`node --check scripts/tests/production-acceptance.mjs`；`git diff --check`。
- 未执行：基于本次未提交源码新建发布镜像后的隔离 Compose HTTP/备份恢复验收、目标服务器部署、已部署环境升级、真实公网访问与浏览器业务验收。
- 恢复要点：未运行过新 bootstrap 时可撤销源码和文档改动；新 bootstrap 已写入的数据库不可删除 Flyway 历史或手工改库，应使用完整恢复组恢复，或通过平台组织开通流程继续初始化业务租户。
