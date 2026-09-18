# 材料实验助手

[English](README.md) | [简体中文](README.zh-CN.md)

材料实验助手是面向材料研发团队的多租户 Web 应用，提供组织管理、项目协作、电子实验记录本（ELN）与受控文件管理能力，可通过自托管 Docker Compose 部署。

> 仓库已公开，但当前**尚未授予开源许可证**。在复制、分发、修改或再利用代码前，请先阅读[许可证](#许可证)。

## 核心能力

- **多租户与 RBAC**：平台管理员开通组织；一个账号仅属于一个组织；组织角色决定动作权限，项目成员关系决定业务数据范围。
- **项目协作**：项目、目标、里程碑、成员、操作记录与项目文档管理。
- **ELN 工作流**：实验计划、参与人、过程图片、结果附件、状态流转及完成后只读。
- **私有文件存储**：PostgreSQL 保存业务元数据和授权关系，RustFS 保存项目文档与实验附件正文。
- **自托管运维**：生产 Compose 隔离 PostgreSQL、RustFS、API 与 Web，提供受控安装、发布、恢复和回退入口。

当前版本不使用 Redis，也未接入 LibreOffice 文档转换。

## 架构

```text
浏览器
  │ HTTP（当前生产方案）
  ▼
Nginx / Web ─────────────► Spring Boot API
                                  ├── PostgreSQL：业务数据、ELN、RBAC
                                  └── RustFS：项目文档与实验附件
```

生产 Compose 只映射 Web 入口到宿主机；API、PostgreSQL 与 RustFS 保持在内部 Docker 网络。

## 开发快速开始

开发 Compose 与生产环境隔离，且会初始化开发夹具。严禁连接生产 PostgreSQL、RustFS、凭据或持久化目录。

### 前置条件

- Docker Engine 与 Docker Compose v2
- 仅在宿主机直接执行 Maven 时需要 JDK 25
- 仅在宿主机执行前端命令时需要 Node.js 与 Corepack/pnpm；pnpm 版本以 [`frontend/package.json`](frontend/package.json) 为准

```bash
git clone https://github.com/bujiuzhi/labAssistant.git
cd labAssistant

if [ ! -e .env ]; then
  (umask 077; cp .env.example .env)
fi

pnpm --dir frontend install --frozen-lockfile
docker compose --env-file .env -f infra/docker-compose.yml config --quiet
docker compose --env-file .env -f infra/docker-compose.yml up -d
docker compose --env-file .env -f infra/docker-compose.yml ps
```

- Web：`http://127.0.0.1:5173/`
- API 存活检查：`http://127.0.0.1:8000/api/v1/health/live`
- API 就绪检查：`http://127.0.0.1:8000/api/v1/health/ready`

环境变量、初始化行为、数据目录与排障方式见[开发与运维指南](docs/development-operations-guide.md)。

## 质量检查

```bash
# 后端：需要 JDK 25
mvn -f backend/pom.xml verify

# 前端：需要已安装锁定依赖
pnpm --dir frontend test
pnpm --dir frontend run build

# 生产运维脚本测试
node --test scripts/tests/production.test.mjs

# 文本与补丁完整性
git diff --check
```

通过这些检查不等同于生产登录、权限、上传下载、防火墙或恢复验收。

## 生产生命周期

生产代码与私有配置位于 `~/work/server/labAssistant`，持久化数据位于 `~/work/data/labAssistant`。将 [`infra/.env.production.example`](infra/.env.production.example) 复制为私有 `.env.production` 后填写实际值；该文件不得提交 Git。

| 场景 | 命令 | 行为 |
| --- | --- | --- |
| 空库首次部署 | `install` | 创建缺失密钥和目录、构建、初始化空生产库并启动服务。 |
| 发布已评审版本 | `build` 后执行 `upgrade` | 构建或导入不可变镜像；`upgrade` 停写、创建 PostgreSQL + RustFS 恢复组，再启动新版本。 |
| 安全重启服务 | `restart` | 仅重启 API/Web，等待健康检查。 |
| 独立恢复点 | `backup` | 停止写入并创建恢复组；服务保持停止，需执行 `up` 恢复。它不是无感的日常在线备份。 |

完整命令和前置条件见[生产 Compose 部署](docs/production-deployment.md)。`install` 只能用于空库；`upgrade` 不会使用未评审工作区构建镜像，且它本身已经创建恢复组，正常升级前无需再额外执行一次手工 `backup`。

当前最简部署方案使用 `http://<公网 IPv4>:15105` 向少量可信用户开放。HTTP 会明文传输登录口令、会话 Cookie 与上传内容；必须限制 `15105/TCP` 来源。扩大访问范围或处理更高敏感度数据前，应切换到 HTTPS。

## 文档

| English | 中文 |
| --- | --- |
| [Documentation index](docs/README.md) | [文档索引](docs/README.zh-CN.md) |
| [System overview](docs/overview-design.en.md) | [系统概要设计](docs/overview-design.md) |
| [Detailed design](docs/detailed-design.en.md) | [详细设计](docs/detailed-design.md) |
| [Production deployment](docs/production-deployment.en.md) | [生产 Compose 部署](docs/production-deployment.md) |
| [Development and operations](docs/development-operations-guide.en.md) | [开发与运维指南](docs/development-operations-guide.md) |
| [OpenAPI contract](contracts/openapi.yaml) | [OpenAPI 契约](contracts/openapi.yaml) |

[audit/](audit/README.md) 保存历史来源、决策与验证证据；它们是历史记录，不能替代当前运行行为说明。

## 目录结构

```text
labAssistant/
├── backend/       Spring Boot API、MyBatis、Flyway 与 JUnit 测试
├── frontend/      Vue 应用、TypeScript 类型与前端测试
├── infra/         Compose、Dockerfile、Nginx 与配置模板
├── scripts/       生产运维脚本与隔离测试
├── contracts/     OpenAPI 契约
├── docs/          当前设计与运维文档
└── audit/         历史来源、决策与验证证据
```

## 贡献

提交 Issue 或 Pull Request 前请阅读 [CONTRIBUTING.zh-CN.md](CONTRIBUTING.zh-CN.md)。授权、数据、存储、配置、迁移或生产运维相关变更必须补充对应测试和文档。

## 安全

不要在公开 Issue 中披露漏洞细节、真实凭据、会话信息或业务数据。报告方式与部署责任见 [SECURITY.zh-CN.md](SECURITY.zh-CN.md)。

## 许可证

仓库当前未授予开源许可证。公开可见不等同于允许复制、分发、修改或再许可；只有权利人补充许可证后，相关授权才会生效。
