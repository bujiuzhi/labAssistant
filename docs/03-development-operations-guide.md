# 材料实验助手开发、部署与运维指南

## 1. 目的与适用范围

本指南是项目唯一的开发、测试、部署、运维和协作规范，适用于当前 `dev` 分支。系统当前由 Vue 前端、Django API、PostgreSQL/SQLite、项目媒体文件和 LibreOffice 预览组件构成。

## 2. 目录与资产边界

```text
项目根目录/
├── backend/          # Django 应用、Migration 与 pytest
├── frontend/         # Vue 应用与前端构建配置
├── docs/             # 三份正式规范与索引
├── contracts/        # OpenAPI 草案
├── infra/            # Docker Compose 基础设施配置
├── audit/            # 原型证据和实施审计，不属于正式规范
└── .env              # 本地/服务器密钥配置，不提交 Git
```

- `backend/apps/*/migrations/` 是数据库物理结构的唯一来源。
- `backend/tests/` 是后端核心链路的自动化验证来源。
- `frontend/src/` 和后端接口共同定义实际页面行为。
- 上传文件、预览缓存、数据库、依赖目录和 `.env` 不得提交 Git。

## 3. 环境准备

### 3.1 Conda 环境

项目使用现有 Conda 环境 `materials-lab-assistant`。新环境按 `environment.yml` 创建；Python 命令统一使用 `python3`。

```bash
conda env create -f environment.yml
conda run -n materials-lab-assistant python3 backend/manage.py migrate
conda run -n materials-lab-assistant python3 backend/manage.py bootstrap_development
```

### 3.2 配置文件

从 `.env.example` 创建 `.env`，按实际环境配置以下类别：

| 类别 | 关键配置 | 说明 |
|---|---|---|
| Django | `DJANGO_SECRET_KEY`、`DJANGO_DEBUG`、`ALLOWED_HOSTS`、`CSRF_TRUSTED_ORIGINS` | 生产环境关闭调试并限制来源 |
| 数据库 | `DATABASE_ENGINE`、`POSTGRES_*` | 切换数据库后先执行 Migration |
| 缓存/任务 | `CELERY_BROKER_URL`、`CELERY_RESULT_BACKEND` | 异步任务启用时必须可用 |
| 开发初始化 | `MATERIALS_LAB_DEVELOPMENT_PASSWORD` | 仅开发环境使用 |
| Docker 数据卷 | `MATERIALS_LAB_DATA_ROOT` | 指向独立数据目录，禁止映射到仓库 |

`.env`、密码、Cookie、访问令牌和生产数据均不得出现在代码、文档提交或日志中。

## 4. 本地与服务器运行

### 4.1 开发启动

```bash
conda run -n materials-lab-assistant python3 backend/manage.py runserver 0.0.0.0:8000
conda run -n materials-lab-assistant npm --prefix frontend run dev -- --host 0.0.0.0
```

前端默认监听 `5173`，后端默认监听 `8000`。前端通过 `/api/v1/` 访问 API；登录前先获取 CSRF，认证采用 Session Cookie。

### 4.2 基础设施

```bash
docker compose --env-file .env -f infra/docker-compose.yml up -d
docker compose --env-file .env -f infra/docker-compose.yml ps
```

Compose 提供 PostgreSQL 和 Redis。开发服务器的 `MEDIA_ROOT` 保存项目文档、实验附件和办公文档 PDF 预览缓存。运行项目文档预览前，服务器必须安装 `libreoffice` 或 `soffice`。

## 5. 测试与质量门禁

| 层级 | 命令/方法 | 通过标准 |
|---|---|---|
| 后端 | `conda run -n materials-lab-assistant pytest backend -q` | 认证、项目、文档、实验、ELN 和附件核心测试通过 |
| 前端类型与构建 | `conda run -n materials-lab-assistant npm --prefix frontend run build` | TypeScript 检查与 Vite 构建通过 |
| 数据库 | 执行 `migrate` 并验证关键查询 | Migration 可重复执行，约束和索引生效 |
| 页面 | 登录、项目编辑、文档预览、ELN 写入等人工/浏览器验证 | 无空白页、无框架错误、权限与状态符合预期 |

当前已实现链路的回归重点：

- 项目创建使用 `Idempotency-Key`，编辑使用 `If-Match`。
- 项目文档上传、下载、PDF/图片原件预览和办公文档转 PDF 预览。
- 实验创建、复制、状态迁移、ELN 写入和真实附件读取。
- 超级管理员用户管理与对象范围越权检查。

## 6. 发布、备份与回滚

### 6.1 发布顺序

1. 确认 `dev` 分支已通过测试并完成代码审查。
2. 备份数据库和 `MEDIA_ROOT`，记录备份时间、版本和恢复位置。
3. 拉取目标提交，安装已锁定依赖，执行 `migrate`。
4. 构建前端，重启 API、静态资源服务和必要的 Worker。
5. 验证健康接口、登录、项目列表、文档预览和 ELN 保存。

### 6.2 回滚原则

- 先停止新版本写流量，再回滚应用版本。
- 存在数据库 Migration 时，必须先评估可逆性；不可逆 Migration 采用向前修复或恢复备份，不允许直接删除生产数据。
- 文件路径、预览缓存与业务记录一起备份；文件丢失时不得用模拟内容替代。
- 每次恢复演练记录耗时、失败原因和改进措施。

## 7. 安全与运维检查

- 生产环境使用 HTTPS、精确 `ALLOWED_HOSTS` 和 `CSRF_TRUSTED_ORIGINS`，禁止 `DEBUG=true`。
- 数据库、Redis 和媒体目录仅对必要进程开放；Docker 端口优先绑定服务器回环地址。
- 每次请求必须经过 Session、权限码、组织范围、对象范围和状态校验。
- 日志应保留请求编号、资源和错误码，不记录密码、Cookie、完整 ELN 正文或文件内容。
- 监控 API 可用性、错误率、数据库连接、磁盘空间、媒体增长、LibreOffice 转换失败和任务积压。

## 8. Git 与文档维护

### 8.1 Git 工作流

- 日常开发在 `dev` 分支进行；提交信息格式为 `feat: 中文摘要`、`fix: 中文摘要`、`docs: 中文摘要` 等。
- 提交前检查工作区，缓存、构建产物、密钥、媒体文件和临时文件不应进入提交。
- 远程开发服务器应快进到已验证提交；本地与 Codeup `dev` 保持同一提交基线。

### 8.2 文档维护

- 范围、架构或已实现边界变化：更新《概要设计》。
- 表、接口、权限、页面或测试变化：更新《详细设计》。
- 环境、部署、测试、发布或协作变化：更新本指南。
- 重大技术取舍在相应规范的变更记录中说明，并保证代码、Migration 和测试可追溯。
