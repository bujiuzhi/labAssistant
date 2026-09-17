# 贡献指南

感谢参与材料实验助手的改进。提交前请先确认变更范围、兼容性影响和可验证的完成条件；涉及数据结构、权限、文件存储或生产运维时，优先发起讨论并说明迁移与恢复方案。

## 本地准备

按根目录 [README](README.md#快速开始开发) 配置开发环境。开发 Compose 会初始化开发数据，禁止指向生产数据库、生产对象存储或生产数据目录。

```bash
pnpm --dir frontend install --frozen-lockfile
docker compose --env-file .env -f infra/docker-compose.yml config --quiet
```

后端直接执行 Maven 时需要 JDK 25；前端依赖使用 `frontend/package.json` 指定的 pnpm 版本与锁文件。

## 提交要求

- 一个变更只解决一个清晰的问题，避免混入格式化、依赖升级或无关重构。
- 不提交 `.env`、密钥、生产连接信息、构建产物、依赖目录、业务数据或日志中的敏感内容。
- 结构变更仅通过 Flyway 新增迁移；不得改写已经执行的迁移。
- 变更接口、数据、授权、配置或部署行为时，同步更新 [docs/](docs/README.md)、OpenAPI、配置模板或审计记录。
- 使用项目既有提交前缀，例如 `feat:`、`fix:`、`docs:`、`test:`、`refactor:` 或 `chore:`，摘要使用中文并准确描述影响。

## 本地检查

按变更范围执行必要检查：

```bash
mvn -f backend/pom.xml verify
pnpm --dir frontend test
pnpm --dir frontend run build
node --test scripts/tests/production.test.mjs
git diff --check
```

不适用或未执行的检查应在 Pull Request 中说明原因。通过构建或单元测试不等同于生产部署、权限隔离、文件上传或恢复验收。

## Pull Request

请在描述中包含：

- 变更目的与范围；
- 受影响的接口、配置、数据库迁移或用户行为；
- 已执行的检查及结果；
- 部署、升级、回退、数据迁移或安全影响（如有）。

维护者在合并前可能要求补充测试、文档、兼容性说明或隔离环境验证。
