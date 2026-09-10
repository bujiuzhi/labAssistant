# 项目文档索引与维护规范

_材料实验助手正式文档入口；更新时间：2026-09-08，时区：Asia/Shanghai。_

---

## 📚 正式文档

设计、开发运维与生产操作分别维护；本文件只负责索引和维护规则，模块 README 只提供导航及必要命令。

| 文档 | 负责内容 | 主要读者 |
| --- | --- | --- |
| [概要设计](overview-design.md) | 目标、能力边界、角色、系统架构、验收范围 | 产品、研发与交接负责人 |
| [详细设计](detailed-design.md) | 数据模型、接口语义、权限、状态、页面、已知限制 | 开发、测试、评审人员 |
| [开发部署与运维指南](development-operations-guide.md) | 环境、配置、新服务器部署、验证、备份、恢复 | 开发、运维、发布人员 |
| [生产 Compose 部署](production-deployment.md) | 生产资产、HTTP IP:15105、初始化、升级、备份与隔离恢复 | 运维、发布人员 |

从[根 README](../README.md)了解项目，再按职责阅读对应文档。后端和前端入口分别见
[后端 README](../backend/README.md)、[前端 README](../frontend/README.md)。

## 🔗 权威来源与优先级

| 内容 | 单一权威来源 | 文档的职责 |
| --- | --- | --- |
| 操作授权与工程规则 | 当前明确要求和适用项目规范 | 不通过文档扩大授权 |
| 实际 HTTP 行为 | Controller、Service、安全配置 | [OpenAPI](../contracts/openapi.yaml)描述已实现契约 |
| 数据结构 | [Flyway 迁移](../backend/src/main/resources/db/migration/) | 详细设计提供导航与语义，不复制完整 DDL |
| 当前运行参数 | [开发 Compose](../infra/docker-compose.yml)、[生产 Compose](../infra/compose.production.yml)、[应用配置](../backend/src/main/resources/application.yml)与对应 Profile | 运维指南说明生效范围与缺口 |
| 依赖版本 | [Maven](../backend/pom.xml)、[前端包清单](../frontend/package.json)及锁文件 | 文档不维护第二份升级清单 |
| 架构图 | 概要设计中的 Mermaid 源码 | 其他文档引用，不另建独立图片版本 |
| 历史来源和验收 | [audit](../audit/README.md) | 只证明对应时间、版本和环境的观察 |

需求目标与当前实现不一致时，分别注明“当前行为”和“待实现/待验证”。
不能用规范文本、前端按钮、已有数据库表或历史截图证明业务接口已接通。

## 🔄 变更与兼容性

- 变更功能边界更新概要设计；变更接口、数据、权限或状态更新详细设计与 OpenAPI。
- 环境变量、启动、网络、备份或发布方式变化更新运维指南；根 README 只更新入口和概要。
- 影响接口、数据、安全、兼容或部署的重要调整在 `audit/logs/` 留下决策、影响、验证及恢复要点。
- 已执行迁移只新增版本；历史来源、哈希清单和审计记录保留原意，不改写成当前验收结论。
- 本次文档按既有协议记录实现，不将现有成功响应改写成尚未实现的 `{code,msg,data}`。
- 工程协作、语言、授权和 Git 规则复用适用规范，不在多份文档中各维护一套。

## ✅ 文档验证

交付前检查相对链接和锚点、标题层级、代码围栏、命令引用、OpenAPI 语法与内部引用，
并对图示做语法与内容核对。浏览器渲染和视觉检查分别记录，未执行时不写“通过”。

时间显示约定与实际实现差异见[时间语义](detailed-design.md#time-semantics)；
部署人工记录使用 `YYYY-MM-DD HH:mm:ss` 并注明 `Asia/Shanghai`。
