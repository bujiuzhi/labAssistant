<div align="center">
  <h1>🔬 材料实验助手</h1>

### 面向材料研发团队的项目协作与电子实验记录本

**从项目计划到实验结果，让团队协作与研发资料有迹可循。**

![Java](https://img.shields.io/badge/Java-25-ed8b00) ![Vue](https://img.shields.io/badge/Vue-3-42b883?logo=vuedotjs&logoColor=white) ![PostgreSQL](https://img.shields.io/badge/PostgreSQL-18-4169e1?logo=postgresql&logoColor=white) ![Deployment](https://img.shields.io/badge/Docker-Compose-2496ed?logo=docker&logoColor=white) [![GitHub Stars](https://img.shields.io/github/stars/bujiuzhi/labAssistant?style=flat&logo=github&label=Stars)](https://github.com/bujiuzhi/labAssistant/stargazers)

[English](README.md) · **简体中文**

[![License: AGPL-3.0-only](https://img.shields.io/badge/license-AGPL--3.0--only-663399)](LICENSE)

[快速部署](#快速部署) · [核心能力](#核心能力) · [账号与权限](#账号与权限) · [升级与维护](#升级与维护) · [本地开发](#本地开发) · [文档导航](#文档导航)
</div>

---

材料实验助手将组织、项目、实验计划、过程记录与文件资料集中在一个自托管工作台中。适合需要按组织隔离数据、按项目分配人员，并持续积累实验记录的材料研发团队。

| 🧪 实验记录 | 👥 团队协作 | 📦 自主部署 |
| :---: | :---: | :---: |
| 计划、过程图片与结果附件统一管理 | 组织隔离、角色权限与项目成员范围 | Docker Compose，数据保存在自己的服务器 |

## 快速部署

服务器需要 Docker Engine、Docker Compose **2.24.4+**、Git、Bash、OpenSSL 和常用归档工具。Java 与前端构建环境由镜像提供。

### 1. 获取代码与配置

```bash
mkdir -p ~/work/server
git clone --branch main https://github.com/bujiuzhi/labAssistant.git ~/work/server/labAssistant
cd ~/work/server/labAssistant
if [ ! -e .env.production ]; then
  (umask 077; cp infra/.env.production.example .env.production)
fi
```

默认配置可以直接使用，只修改需要调整的值：

```ini
APP_PORT=13501
ADMIN_USERNAME=admin
ADMIN_DISPLAY_NAME=平台管理员
```

无需填写公网 IP、目录或 Git 版本。脚本自动准备依赖镜像，按当前用户主目录生成项目专属路径，生成缺失密钥，并为当前提交创建唯一发布标签。

### 2. 安装并访问

```bash
bash scripts/deploy.sh install
```

浏览器打开 **`http://服务器IP:13501`**。首次只创建平台管理员及内部平台组织，业务组织数量为 **0**，没有测试项目或开发账号。

初始密码保存在 `~/work/server/labAssistant/data/production-secrets/bootstrap_platform_admin_password`，登录名默认为 `admin`。升级会保留原密码；更改配置中的管理员信息不会重置已有账号。

> [!IMPORTANT]
> `install` 仅用于新部署。已有完整 `MATERIALS_LAB_*` 配置继续兼容，请保留原文件；不要用新模板覆盖旧部署配置。HTTP 为明文传输，应将入口端口限制到可信来源。默认支持 IPv4 地址访问。

## 核心能力

| 能力 | 说明 |
| --- | --- |
| 项目总览 | 查看有权限访问的项目卡片、项目与实验指标 |
| 项目协作 | 管理目标、里程碑、负责人、项目成员与归档状态 |
| 电子实验记录本 | 记录实验计划、参与人、实验过程、图片和结果附件 |
| 实验状态管理 | 跟踪未开始、进行中和已完成状态；完成后记录只读 |
| 资料管理 | 项目文档分类、上传、授权下载及支持格式的浏览器预览 |
| 多租户与 RBAC | 组织隔离、角色动作权限与项目成员数据范围 |
| 账号管理 | 邀请码注册、管理员创建账号、停用、逻辑删除和密码管理 |
| 部署与恢复 | 项目独立 Compose，支持升级备份、应用回退与空目标恢复 |

PostgreSQL 保存业务元数据，RustFS 保存新上传的文档与附件正文。当前版本不依赖 Redis，也未接入 LibreOffice 文档转换。能力边界见[概要设计](docs/overview-design.md)。

## 账号与权限

平台管理员开通组织时，同时创建该组织首个超级管理员。组织管理员随后管理本组织成员与邀请码；一个账号只属于一个组织。

| 身份 | 职责与范围 |
| --- | --- |
| 平台管理员 | 查看和开通组织，不读取租户业务数据 |
| 组织超级管理员 | 管理本组织人员、邀请码及组织内业务 |
| 项目管理员 | 管理有权限的项目、成员、文档与实验 |
| 实验员 | 查看分配的项目，操作与自己相关且状态允许的实验 |

角色决定可执行的动作，项目成员等关联关系决定可访问的数据；后端同时校验这两个条件。完整规则见[授权设计](docs/detailed-design.md)。

## 升级与维护

在项目目录同步已评审的 `main` 代码，再执行升级：

```bash
git pull --ff-only origin main
bash scripts/deploy.sh upgrade
```

升级自动构建新版本、停写、备份 PostgreSQL 与 RustFS，然后启动服务。构建失败不停止旧服务；进入停写阶段后失败会保持写入口停止，避免自动退回可能不兼容的旧版本。

| 操作 | 命令 |
| --- | --- |
| 查看状态 | `bash scripts/deploy.sh status` |
| 重启当前版本 | `bash scripts/deploy.sh restart` |
| 检查健康状态 | `bash scripts/deploy.sh check` |
| 创建独立备份并保持停写 | `bash scripts/deploy.sh backup` |
| 备份后恢复服务 | `bash scripts/deploy.sh up` |
| 继续启动失败部署的目标版本 | `bash scripts/deploy.sh resume` |
| 卸载服务，保留数据、密钥及镜像 | `bash scripts/deploy.sh uninstall` |

修改端口后使用 `upgrade` 应用；`restart` 始终使用上次成功部署的配置和版本。首次初始化中断、旧完整配置、数据库恢复和兼容性回退见[生产部署指南](docs/production-deployment.md)。

## 技术栈与目录

| 层级 | 技术 |
| --- | --- |
| 前端 | Vue 3、TypeScript、Element Plus、Vite |
| 后端 | Java 25、Spring Boot、MyBatis |
| 业务数据 | PostgreSQL、Flyway |
| 文件存储 | RustFS |
| 部署 | Docker Compose、Nginx |

```text
labAssistant/
├── backend/       # API、业务逻辑、数据库迁移与测试
├── frontend/      # 页面、组件与前端测试
├── infra/         # Compose、Dockerfile、Nginx 与配置模板
├── scripts/       # 生产运维脚本与隔离测试
├── contracts/     # OpenAPI 接口契约
├── docs/          # 当前设计与运维文档
└── audit/         # 历史来源、重要变更与验证记录
```

生产代码与私有配置放在 `~/work/server/labAssistant`，持久化数据放在 `~/work/data/labAssistant`，分别按项目隔离。

## 本地开发

日常开发使用 `dev` 分支。宿主机运行后端需要 JDK 25，前端使用项目锁定的 pnpm 版本。开发 Compose 会初始化开发数据，应使用独立数据库和存储。

环境初始化与启动命令见[开发与运维指南](docs/development-operations-guide.md)。按改动范围执行检查：

```bash
mvn -f backend/pom.xml verify
pnpm --dir frontend test
pnpm --dir frontend run build
node --test scripts/tests/production.test.mjs
git diff --check
```

贡献流程见[贡献指南](CONTRIBUTING.zh-CN.md)。构建、测试和健康检查通过，不能替代目标服务器的登录、权限、附件和恢复验收。

## 文档导航

| 文档 | 内容 |
| --- | --- |
| [文档索引](docs/README.zh-CN.md) | 中英文技术文档入口 |
| [生产部署](docs/production-deployment.md) | 首次部署、升级、备份、恢复与回退 |
| [概要设计](docs/overview-design.md) | 产品范围、角色与架构 |
| [详细设计](docs/detailed-design.md) | 数据、接口、授权与状态规则 |
| [开发与运维](docs/development-operations-guide.md) | 开发环境、配置、验证与排障 |
| [OpenAPI](contracts/openapi.yaml) | HTTP 接口契约 |
| [安全策略](SECURITY.zh-CN.md) | 漏洞报告方式与部署责任 |

## 许可证

材料实验助手采用 [GNU Affero General Public License v3.0 only](LICENSE)（`AGPL-3.0-only`）。允许在遵守许可证的前提下商用；分发受协议约束的作品时须履行对应源码提供义务，修改版通过网络向用户提供服务时，也须向这些用户提供对应源代码。

许可证包含第 11 条规定的贡献者专利许可。第三方组件仍遵循各自的许可证。完整授权条件及免责声明以 [LICENSE](LICENSE) 为准。
