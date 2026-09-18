# 贡献指南

[English](CONTRIBUTING.md) | [简体中文](CONTRIBUTING.zh-CN.md)

感谢参与材料实验助手的改进。请保持每项贡献聚焦，说明兼容性影响，并提供与风险相称的验证证据。

## 开始前

- 阅读[文档索引](docs/README.zh-CN.md)、[安全策略](SECURITY.zh-CN.md)和相关设计文档。
- 授权、租户、数据存储、迁移、部署或恢复相关变更，应在实现前先明确方案与边界。
- 不得将开发 Compose、开发夹具或本地配置连接到生产数据库、RustFS、凭据或持久化目录。

## 本地准备

```bash
pnpm --dir frontend install --frozen-lockfile
docker compose --env-file .env -f infra/docker-compose.yml config --quiet
```

宿主机 Maven 需要 JDK 25。pnpm 版本以 `frontend/package.json` 和 `frontend/pnpm-lock.yaml` 为准。

## 变更要求

- 不提交 `.env`、凭据、生产连接信息、生成产物、依赖目录、业务数据或敏感日志。
- 结构变更只能通过新增 Flyway 迁移完成；不得改写可能已经执行的迁移。
- 行为变更时同步更新相关设计文档、OpenAPI、配置模板和/或审计记录。
- 使用既有提交前缀，如 `feat:`、`fix:`、`docs:`、`test:`、`refactor:` 或 `chore:`；中文摘要应简洁准确。
- 为新增或变化的授权、持久化、生命周期或 UI 行为补充面向行为的测试；不得为通过检查而弱化既有断言。

## 验证

按变更范围执行必要检查：

```bash
mvn -f backend/pom.xml verify
pnpm --dir frontend test
pnpm --dir frontend run build
node --test scripts/tests/production.test.mjs
git diff --check
```

不适用或未执行的检查应说明原因。构建和单元测试通过不等同于生产权限、文件上传、网络或恢复验收。

## Pull Request

日常集成使用 `dev`，正式发布使用 `main`。普通开发不得直接以 `main` 为目标分支。

每个 Pull Request 应包含：

- 变更目的与范围；
- 受影响的接口、配置、迁移、授权或用户行为；
- 已执行的验证命令及结果；
- 部署、回退、数据迁移或安全影响（如有）。

维护者可在合并前要求缩小差异、补充测试、文档、兼容性说明或隔离环境验证。
