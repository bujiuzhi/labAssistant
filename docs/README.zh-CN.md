# 文档索引

[English](README.md) | [简体中文](README.zh-CN.md)

本目录保存材料实验助手当前有效的设计与运维文档。项目入口为[中文 README](../README.zh-CN.md)；历史决策和验证记录位于 [audit/](../audit/README.md)，不能作为当前行为说明。

## 文档地图

| 主题 | English | 中文 | 主要读者 |
| --- | --- | --- | --- |
| 产品与架构 | [System overview](overview-design.en.md) | [系统概要设计](overview-design.md) | 产品、研发、交接负责人 |
| 数据、接口与授权 | [Detailed design](detailed-design.en.md) | [详细设计](detailed-design.md) | 研发、测试、评审人员 |
| 服务器部署与恢复 | [Production deployment](production-deployment.en.md) | [生产 Compose 部署](production-deployment.md) | 运维、发布负责人 |
| 开发与运维 | [Development and operations](development-operations-guide.en.md) | [开发与运维指南](development-operations-guide.md) | 开发、运维人员 |
| HTTP 契约 | [OpenAPI](../contracts/openapi.yaml) | [OpenAPI](../contracts/openapi.yaml) | API 使用方 |
| 贡献流程 | [Contributing](../CONTRIBUTING.md) | [贡献指南](../CONTRIBUTING.zh-CN.md) | 贡献者 |
| 安全报告 | [Security](../SECURITY.md) | [安全策略](../SECURITY.zh-CN.md) | 维护者、报告人 |

中英文文件描述同一受支持发布边界。若翻译出现不一致，以源码、迁移、Compose 配置和 OpenAPI 契约为准，并尽快修正文档。

## 事实来源

| 内容 | 权威来源 |
| --- | --- |
| HTTP 行为 | [OpenAPI](../contracts/openapi.yaml)、Controller 与 Service |
| 数据结构与迁移顺序 | [Flyway 迁移](../backend/src/main/resources/db/migration/) |
| 运行配置 | Compose、应用 Profile 与环境模板 |
| 依赖版本 | `pom.xml`、`package.json` 与锁文件 |
| 历史证据 | [audit/](../audit/README.md) |

文档不能证明未执行的部署、迁移、登录、上传或恢复已成功。

## 维护规则

- 功能范围或角色调整：更新系统概要设计。
- 接口、数据、授权或生命周期调整：同步更新详细设计和 OpenAPI。
- 部署、环境变量、网络、备份或恢复调整：同步更新两份运维指南。
- 影响安全、兼容、数据或发布的重要变更：在 `audit/logs/` 记录，不记录凭据或业务正文。
- 已执行的 Flyway 迁移不得改写；只能新增迁移。

发布前核对链接、标题层级、命令、配置名和契约引用。文档审阅不替代实际运行验收。
