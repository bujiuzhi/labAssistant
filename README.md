# 材料实验助手

_面向材料研发团队的独立 Web 应用；本文是项目入口，当前实现以仓库源码为准。_

---

## 📋 项目范围

项目以组织、项目、实验和电子实验记录本（ELN）组织研发工作。当前后端为 Java，前端为 Vue；本仓库不包含 Electron 客户端。

已接通登录与会话、组织用户管理、项目查询与基础信息维护、关注和归档、项目文档上传与读取、实验创建与顺序状态迁移、ELN 文本及表格保存。

当前版本有以下边界，部署和验收必须据此确定范围：

- PostgreSQL 保存业务元数据、ELN 与权限关系；项目文档和实验附件的新正文保存于私有 RustFS 桶，旧 BYTEA 正文仅保留兼容读取。
- Redis 不接入当前 Java 业务链路；未实现 LibreOffice 转 PDF。
- 实验复制、参与人维护以及过程图片/结果附件上传、读取、删除已接通；项目成员维护等字段仍存在前后端未闭合项。
- `prod` 不运行开发种子初始化；一次性 `bootstrap` 仅建立内部平台组织及平台管理员，初始业务组织数量为 0。平台管理员通过“组织管理”开通每个业务组织及其首个组织管理员，不管理租户成员或业务数据。
- 开发和生产使用独立 Compose；生产不复用开发数据库、数据目录或默认账户。
- 当前用户与权限模型的正式名称为“多租户 RBAC + 项目成员数据范围”：账号绑定单一组织，组织内角色授予动作权限，项目成员和实验参与关系进一步收窄可操作对象；特权账号边界和后续演进见[权限模型](docs/detailed-design.md#access-model)。

完整能力边界见[概要设计](docs/overview-design.md)，接口限制见[详细设计](docs/detailed-design.md)。

## 📚 文档与目录

| 入口 | 用途 |
| --- | --- |
| [正式文档索引](docs/README.md) | 文档职责、权威来源和维护规则 |
| [概要设计](docs/overview-design.md) | 产品范围、角色、当前架构与验收边界 |
| [详细设计](docs/detailed-design.md) | 数据、接口、权限、状态、页面及实现限制 |
| [开发部署与运维指南](docs/development-operations-guide.md) | 新服务器开发部署、生产准备、验证、备份与恢复 |
| [生产 Compose 部署](docs/production-deployment.md) | 公网 HTTP IP:15105、首次真实身份初始化、构建、发布、备份与空库恢复 |
| [OpenAPI](contracts/openapi.yaml) | 当前后端已实现的 HTTP 接口 |
| [审计入口](audit/README.md) | 来源、历史证据、变更记录与验证缺口 |

```text
labAssistant/
├── backend/       # Spring Boot、MyBatis、Flyway、JUnit
├── frontend/      # Vue、TypeScript、页面与前端测试
├── infra/         # 独立开发/生产 Compose、Dockerfile、Nginx 与数据库初始化
├── scripts/       # 生产运维入口与隔离验收
├── contracts/     # OpenAPI 接口契约
├── docs/          # 正式设计与运维文档
├── audit/         # 来源、历史证据与变更审计
└── .env.example   # 开发配置模板，不是生产配置
```

工具版本沿用 [backend/pom.xml](backend/pom.xml)、[frontend/package.json](frontend/package.json)、
[前端锁文件](frontend/pnpm-lock.yaml)及 [Compose](infra/docker-compose.yml)，不按文档中的版本副本升级依赖。

## 🔧 开发环境启动

以下命令在已经取得代码、准备好 Docker Compose 与项目指定 Node/pnpm 的服务器执行。
先按[新服务器准备步骤](docs/development-operations-guide.md#development-deployment)创建服务器私有 `.env`，
使用实际绝对数据目录，并将端口绑定为回环地址或明确获准的私网地址。

```bash
cd ~/work/server/labAssistant
pnpm --dir frontend install --frozen-lockfile
docker compose --env-file .env -f infra/docker-compose.yml config --quiet
docker compose --env-file .env -f infra/docker-compose.yml up -d
docker compose --env-file .env -f infra/docker-compose.yml ps
curl --fail --silent --show-error http://127.0.0.1:8000/api/v1/health/live
curl --fail --silent --show-error http://127.0.0.1:8000/api/v1/health/ready
```

前端默认地址为 `http://127.0.0.1:5173`，经 Vite 将 `/api` 转发至 API。
远程访问方式、启动日志和端口限制见运维指南，不把旧服务器 IP 当作新部署参数。

开发 Profile 每次启动会补齐测试组织、角色、用户和示例业务数据，并刷新实验数量统计。
常用测试用户名为 `admin`、`manager`、`researcher`；密码由
`MATERIALS_LAB_DEVELOPMENT_PASSWORD` 在创建账户时设置。修改该变量不会重置已有密码。
测试账户及样例数据不得当作生产初始化方案。

## 📦 生产环境部署

以[生产 Compose 部署指南](docs/production-deployment.md)为单一操作入口。
先准备 `.env.production` 与独立凭据，再按 `preflight → build → bootstrap → up → check` 执行。
命令详见 `bash scripts/production.sh --help`；其中 `bootstrap` 仅用于空库，升级不重复建账。
生产构建在容器内完成，宿主无需安装 Java/Node；当前明确采用无 TLS 的公网 HTTP `IP:15105` 入口，风险与验收要求见生产部署指南。

## ✅ 验证与交付

业务代码变更通常执行：

```bash
mvn -f backend/pom.xml verify
pnpm --dir frontend test
pnpm --dir frontend run build
git diff --check
```

上述命令分别覆盖现有 Java 测试与打包、前端测试、类型检查与构建、差异格式；它们不替代运行验收。
纯文档变更执行链接、结构、契约及相关图示检查即可。验收方法和已有自动化覆盖见运维指南。

生产部署前需完成配置、身份初始化、文件安全与目标功能缺口评估；具体门槛见
[生产部署准备](docs/development-operations-guide.md#production-deployment)。
本仓库文档不代表新服务器已部署或已通过业务验收。
