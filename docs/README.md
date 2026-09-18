# Documentation

[English](README.md) | [简体中文](README.zh-CN.md)

This directory contains the current design and operations documentation for Materials Lab Assistant. The project entry point is the [README](../README.md). Historical decisions and verification records are kept separately in [audit/](../audit/README.md); they do not define current behavior.

## Document map

| Topic | English | 中文 | Primary readers |
| --- | --- | --- | --- |
| Product and architecture | [System overview](overview-design.en.md) | [系统概要设计](overview-design.md) | Product, engineering, handover owners |
| Data, API, and authorization | [Detailed design](detailed-design.en.md) | [详细设计](detailed-design.md) | Engineering, QA, reviewers |
| Server deployment and recovery | [Production deployment](production-deployment.en.md) | [生产 Compose 部署](production-deployment.md) | Operators, release owners |
| Development and operations | [Development and operations](development-operations-guide.en.md) | [开发与运维指南](development-operations-guide.md) | Developers, operators |
| HTTP contract | [OpenAPI](../contracts/openapi.yaml) | [OpenAPI](../contracts/openapi.yaml) | API consumers |
| Contribution process | [Contributing](../CONTRIBUTING.md) | [贡献指南](../CONTRIBUTING.zh-CN.md) | Contributors |
| Security reporting | [Security](../SECURITY.md) | [安全策略](../SECURITY.zh-CN.md) | Maintainers and reporters |

The English and Chinese files describe the same supported release boundary. If a translation becomes inconsistent, source code, migrations, Compose configuration, and the OpenAPI contract take precedence until the documentation is corrected.

## Sources of truth

| Subject | Source of truth |
| --- | --- |
| HTTP behavior | [OpenAPI](../contracts/openapi.yaml), controllers, and services |
| Database structure and migration order | [Flyway migrations](../backend/src/main/resources/db/migration/) |
| Runtime configuration | Compose files, application profiles, and environment templates |
| Dependency versions | `pom.xml`, `package.json`, and lock files |
| Historical evidence | [audit/](../audit/README.md) |

Do not treat documentation as evidence that an unexecuted deployment, migration, login, upload, or recovery has succeeded.

## Documentation maintenance

- Product scope or role changes: update the system overview.
- API, data, authorization, or lifecycle changes: update the detailed design and OpenAPI contract.
- Deployment, environment, networking, backup, or recovery changes: update both operations guides.
- Security, compatibility, data, and release-sensitive changes: add an `audit/logs/` record without credentials or business content.
- Never rewrite an executed Flyway migration; add a new migration instead.

Before publishing, validate links, headings, commands, configuration keys, and contract references. Documentation review does not replace runtime acceptance.
