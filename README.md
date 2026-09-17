# 材料实验助手

材料研发团队使用的多租户 Web 应用，提供组织开通、项目协作、电子实验记录本（ELN）及受控文件管理能力。

## 功能

- **多租户与 RBAC**：平台管理员开通组织；组织管理员在本组织内管理成员与邀请码；一个账号仅属于一个组织。
- **项目协作**：项目总览、基础信息、里程碑、实验计划、参与人、状态流转。
- **实验记录**：电子实验记录本、过程图片、结果附件与完成后只读控制。
- **文件管理**：项目文档和实验附件正文保存于私有 RustFS，PostgreSQL 保存业务元数据与授权关系。
- **运维交付**：生产 Compose 隔离 PostgreSQL、RustFS、API 与 Web，并提供部署、升级、备份、恢复和回退入口。

当前版本不使用 Redis，未接入 LibreOffice 文件转换。产品边界、接口限制与已知条件以 [docs/](docs/README.md) 中的正式设计为准。

## 架构

```text
浏览器
  │ HTTP（生产默认 IP:15105）
  ▼
Nginx / Web ─────────────► Spring Boot API
                                 ├── PostgreSQL（业务数据、权限、ELN）
                                 └── RustFS（项目文档、实验附件）
```

生产环境仅对外暴露 Web 入口；数据库、RustFS API 与控制台保持在主机回环或内部网络。当前部署模式按使用方选择的 HTTP `IP:15105` 运行，明文传输风险必须由部署方接受，并应限制访问来源。

## 前置条件

- Docker Engine 与 Docker Compose v2：用于开发 Compose 和生产 Compose。
- JDK 25：仅在宿主机直接执行后端 Maven 命令时需要。
- Node.js 与 Corepack/pnpm：仅在宿主机安装前端依赖、执行前端测试或构建时需要；pnpm 版本以 [`frontend/package.json`](frontend/package.json) 的 `packageManager` 为准。

## 快速开始（开发）

开发 Compose 与生产环境隔离，且会写入开发示例数据；不得连接生产数据库或生产对象存储。

```bash
git clone <repository-url> labAssistant
cd labAssistant

if [ ! -e .env ]; then
  (umask 077; cp .env.example .env)
fi

# 在目标 Linux 环境安装，勿复制 macOS 的 node_modules
pnpm --dir frontend install --frozen-lockfile

docker compose --env-file .env -f infra/docker-compose.yml config --quiet
docker compose --env-file .env -f infra/docker-compose.yml up -d
docker compose --env-file .env -f infra/docker-compose.yml ps
```

默认页面为 `http://127.0.0.1:5173/`；存活和就绪检查分别为 `http://127.0.0.1:8000/api/v1/health/live` 与 `http://127.0.0.1:8000/api/v1/health/ready`。开发环境变量、挂载路径、初始化行为和排障见[开发与运维指南](docs/development-operations-guide.md)。

## 配置

- 仅将 [`.env.example`](.env.example) 作为开发配置模板；复制后的 `.env` 不提交版本库。
- 生产环境使用 [`infra/.env.production.example`](infra/.env.production.example) 和独立的 `.env.production`；不得复用开发数据库、开发对象存储、开发口令或数据目录。
- 数据库密码、RustFS 凭据和会话密钥必须由部署方设置为独立强值；不要在 Issue、日志、提交或文档中写入真实值。

配置的生效范围、网络绑定和持久化目录说明见[开发与运维指南](docs/development-operations-guide.md)。

## 测试与检查

```bash
# 后端：需要 JDK 25
mvn -f backend/pom.xml verify

# 前端：需要已安装的锁定依赖
pnpm --dir frontend test
pnpm --dir frontend run build

# 生产运维脚本的隔离测试
node --test scripts/tests/production.test.mjs

# 文本与补丁完整性
git diff --check
```

这些检查分别验证编译、已有测试、构建或脚本行为，不替代目标服务器上的部署、登录、权限、上传和恢复验收。

## 生产部署

生产操作由统一脚本收敛，完整前置检查、配置、备份、恢复和回退规则见[生产 Compose 部署指南](docs/production-deployment.md)。在服务器完成 `.env.production` 配置后，常用入口如下：

```bash
cd ~/work/server/labAssistant
LABASSISTANT_PROD_ENV="$PWD/.env.production"

# 空库首次部署：生成缺失密钥、构建、初始化并启动
bash scripts/production.sh --env "$LABASSISTANT_PROD_ENV" install --confirm materials-lab-production

# 已同步经评审发布代码后：停写、备份并启动新版本
bash scripts/production.sh --env "$LABASSISTANT_PROD_ENV" upgrade --confirm materials-lab-production

# 日常维护
bash scripts/production.sh --env "$LABASSISTANT_PROD_ENV" restart --confirm materials-lab-production
bash scripts/production.sh --env "$LABASSISTANT_PROD_ENV" backup --confirm materials-lab-production
```

`install` 仅用于空库；`upgrade` 不会隐式构建工作区，发布前须同步受评审代码并执行 `build` 或导入已验证镜像。升级与恢复前必须按部署指南完成备份和停写确认。

## 文档

| 文档 | 内容 |
| --- | --- |
| [文档索引](docs/README.md) | 当前有效文档、权威来源和维护规则 |
| [概要设计](docs/overview-design.md) | 产品范围、角色、架构与验收边界 |
| [详细设计](docs/detailed-design.md) | 数据、接口、授权范围、状态与页面语义 |
| [生产 Compose 部署](docs/production-deployment.md) | 正式服务器部署、升级、备份、恢复与回退 |
| [开发与运维指南](docs/development-operations-guide.md) | 开发环境、配置、验证与运维边界 |
| [OpenAPI](contracts/openapi.yaml) | 已实现 HTTP 契约 |
| [审计记录](audit/README.md) | 历史来源、重要变更和验证证据 |

## 参与贡献

贡献流程、质量检查和 Pull Request 所需信息见 [CONTRIBUTING.md](CONTRIBUTING.md)。提交前请保持变更聚焦，并同步更新受影响的契约、迁移、配置或文档。

## 安全

请勿将漏洞细节、真实凭据或业务数据公开到 Issue。当前漏洞报告方式和支持范围见 [SECURITY.md](SECURITY.md)。

## 许可证

仓库当前未包含 `LICENSE` 文件，也未声明开源许可证。在获得权利人明确授权并补充许可证前，不应将本仓库内容视为可按开源许可证再分发或再许可的软件。

## 目录

```text
labAssistant/
├── backend/       Spring Boot API、MyBatis、Flyway、JUnit
├── frontend/      Vue 页面、类型定义与前端测试
├── infra/         Compose、Dockerfile、Nginx 与生产配置模板
├── scripts/       生产运维脚本与隔离测试
├── contracts/     OpenAPI 契约
├── docs/          当前有效的设计与运维文档
└── audit/         历史来源、决策和验证记录
```

依赖版本以 `backend/pom.xml`、`frontend/package.json`、`frontend/pnpm-lock.yaml` 和 Compose 文件为准。
