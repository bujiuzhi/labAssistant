# 文档索引

本目录保留当前有效的设计与运维文档。项目入口是根目录 [README](../README.md)；历史来源、决策过程和验证证据位于 [audit/](../audit/README.md)，不能代替当前行为说明。

## 文档地图

| 文档 | 说明 | 读者 |
| --- | --- | --- |
| [概要设计](overview-design.md) | 产品范围、角色、架构、能力边界与验收范围 | 产品、研发、交接负责人 |
| [详细设计](detailed-design.md) | 数据、接口、授权范围、状态、页面语义与限制 | 开发、测试、评审人员 |
| [生产 Compose 部署](production-deployment.md) | 生产配置、首次部署、升级、备份、恢复与回退 | 运维、发布人员 |
| [开发与运维指南](development-operations-guide.md) | 开发环境、配置、验证、迁移与运维边界 | 开发、运维人员 |
| [贡献指南](../CONTRIBUTING.md) | 本地准备、质量检查和 Pull Request 要求 | 贡献者、评审人员 |
| [安全策略](../SECURITY.md) | 当前支持范围与私下报告原则 | 使用者、维护者 |

模块导航见 [backend/README](../backend/README.md) 与 [frontend/README](../frontend/README.md)。

## 事实来源

| 内容 | 权威来源 |
| --- | --- |
| HTTP 契约 | [OpenAPI](../contracts/openapi.yaml) 与 Controller/Service 实现 |
| 数据结构与迁移顺序 | [Flyway 迁移](../backend/src/main/resources/db/migration/) |
| 运行配置 | Compose、应用 Profile 和 `.env` 模板 |
| 依赖版本 | Maven、`package.json` 与锁文件 |
| 历史证据 | [audit/](../audit/README.md) |

文档不得替代代码、迁移或配置作为事实来源。未实现、待验证和历史行为必须明确标注。

## 维护规则

- 功能范围或角色调整：更新概要设计。
- 接口、数据、授权或状态调整：同步更新详细设计与 OpenAPI。
- 部署、环境变量、网络、备份或恢复调整：同步更新生产部署文档与运维指南。
- 影响安全、兼容、数据或发布的重要变更：在 `audit/logs/` 记录决策、影响、验证和恢复要点，不记录凭据或业务正文。
- 已执行的 Flyway 迁移不可修改；仅新增版本。

交付前核对链接、标题层级、命令、配置名和契约引用。文档检查不替代实际运行验收。
