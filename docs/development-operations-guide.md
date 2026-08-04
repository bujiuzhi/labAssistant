# 材料实验助手开发、部署与运维指南

## 1. 目的与适用范围

本指南是项目唯一的开发、测试、部署、运维和协作规范，适用于当前 `dev-java` 分支。系统当前由 Vue 前端、Spring Boot API、PostgreSQL、RustFS 私有对象存储和 LibreOffice 预览组件构成。

## 2. 目录与资产边界

```text
项目根目录/
├── backend/          # Spring Boot、MyBatis、Flyway 与 JUnit
├── frontend/         # Vue 应用与前端构建配置
├── docs/             # 三份正式规范与索引
├── contracts/        # OpenAPI 草案
├── infra/            # Docker Compose 基础设施配置
├── audit/            # 原型证据和实施审计，不属于正式规范
└── .env              # 本地/服务器密钥配置，不提交 Git
```

- `backend/src/main/resources/db/migration/` 是数据库物理结构的唯一来源。
- `backend/src/test/java/` 是后端核心链路的自动化验证来源。
- `frontend/src/` 和后端接口共同定义实际页面行为。
- 对象数据、预览缓存、数据库、依赖目录和 `.env` 不得提交 Git。

## 3. 环境准备

### 3.1 Conda 环境

项目使用现有 Conda 环境 `materials-lab-assistant`。新环境按 `environment.yml` 创建；后端命令统一使用 `mvn -f backend/pom.xml`。

```bash
conda env create -f environment.yml
conda run -n materials-lab-assistant mvn -f backend/pom.xml test
conda run -n materials-lab-assistant mvn -f backend/pom.xml spring-boot:run
```

### 3.2 配置文件

从 `.env.example` 创建 `.env`，按实际环境配置以下类别：

| 类别 | 关键配置 | 说明 |
|---|---|---|
| Spring Boot | `SERVER_*`、`SESSION_COOKIE_SECURE`、`DATABASE_MAX_POOL_SIZE` | 生产环境启用安全 Cookie 并限制网络入口 |
| 数据库 | `POSTGRES_*`、`JDBC_DATABASE_URL` | 启动时由 Flyway 执行 Migration |
| 对象存储 | `OBJECT_STORAGE_*` | RustFS 使用私有桶、SigV4 和 path-style，访问密钥不得提交 |
| 开发初始化 | `MATERIALS_LAB_DEVELOPMENT_PASSWORD` | 仅开发环境使用 |
| Docker 数据卷 | `MATERIALS_LAB_DATA_ROOT` | 指向独立数据目录，禁止映射到仓库 |

`.env`、密码、Cookie、访问令牌和生产数据均不得出现在代码、文档提交或日志中。

## 4. 本地与服务器运行

### 4.1 开发启动

```bash
conda run -n materials-lab-assistant mvn -f backend/pom.xml spring-boot:run
conda run -n materials-lab-assistant pnpm --dir frontend dev -- --host 0.0.0.0
```

前端默认监听 `5173`，后端默认监听 `8000`。前端通过 `/api/v1/` 访问 API；登录前先获取 CSRF，认证采用 Session Cookie。

### 4.2 基础设施

```bash
docker compose --env-file .env -f infra/docker-compose.yml up -d
docker compose --env-file .env -f infra/docker-compose.yml ps
```

Compose 提供 PostgreSQL、Redis 和 RustFS。RustFS S3 API 与控制台默认仅绑定服务器回环地址
`19000`、`19001`；Java API 使用私有桶保存项目文档、实验附件和办公文档 PDF 预览缓存。
运行项目前必须确保目标桶和访问密钥已初始化，运行办公文档预览前还必须安装
`libreoffice` 或 `soffice`。RustFS 单机数据映射到
`${MATERIALS_LAB_DATA_ROOT}/rustfs`，不得放入代码仓库。

### 4.3 运行前检查与故障定位

每次启动或重启后，按“进程—接口—页面—文件”顺序检查，避免仅以端口监听判断系统可用。

| 检查层次 | 命令或动作 | 预期结果 | 异常处理方向 |
|---|---|---|---|
| 依赖服务 | `docker compose --env-file .env -f infra/docker-compose.yml ps` | PostgreSQL、Redis、RustFS 为运行状态 | 查看容器日志和数据卷挂载，禁止重建并覆盖数据卷 |
| 对象存储桶 | 使用兼容 S3 的客户端检查桶 | 私有桶存在且凭据可读写 | 检查 RustFS、endpoint、SigV4、path-style 和密钥 |
| 数据库迁移 | `mvn -f backend/pom.xml spring-boot:run` | Flyway 历史无缺失 | 备份后由 Flyway 执行迁移，禁止手工修改表结构 |
| API 存活 | `GET /api/v1/health/live` | 返回 200 | 检查 Java 进程、端口、环境变量和日志 |
| API 就绪 | `GET /api/v1/health/ready` | 返回 200，依赖可连接 | 检查数据库连接、网络与配置 |
| 前端页面 | 浏览器打开前端地址并登录 | 无空白页、接口无跨域/会话错误 | 检查 Vite 代理、浏览器控制台和 API 地址 |
| 文件预览 | 分别打开 DOCX、XLSX、PPTX、PDF、图片和文本样例 | 首选组件渲染；组件失败时显示转换 PDF | 检查浏览器控制台、私有桶权限、LibreOffice、临时目录和磁盘空间 |

开发环境若服务器端口不对外开放，应使用 README 给出的 SSH 隧道访问；不得通过修改后端认证逻辑或关闭 CSRF 来规避访问问题。
二进制原件和 PDF 预览请求不要覆盖 `Accept` 为 DRF 未配置的媒体类型，否则请求可能在进入
文件视图前被内容协商返回 406；前端只需设置 `responseType: arraybuffer`。

## 5. 测试与质量门禁

| 层级 | 命令/方法 | 通过标准 |
|---|---|---|
| 后端 | `conda run -n materials-lab-assistant mvn -f backend/pom.xml test` | Java 单测和编译通过 |
| 后端静态检查 | `conda run -n materials-lab-assistant mvn -f backend/pom.xml verify` | 编译、测试和打包校验通过 |
| 前端单测 | `conda run -n materials-lab-assistant pnpm --dir frontend test` | 格式分流、文本解码和 CSV 解析通过 |
| 前端类型与构建 | `conda run -n materials-lab-assistant pnpm --dir frontend run build` | TypeScript 检查、组件懒加载和 Vite 构建通过 |
| 数据库 | 执行 `migrate` 并验证关键查询 | Migration 可重复执行，约束和索引生效 |
| 页面 | 登录、项目编辑、文档预览、ELN 写入等人工/浏览器验证 | 无空白页、无框架错误、权限与状态符合预期 |

当前已实现链路的回归重点：

- 项目创建使用 `Idempotency-Key`，编辑和归档使用 `If-Match`，关键动作写入业务操作日志。
- 项目文档上传、下载、签名与压缩包安全校验；DOCX/XLS/XLSX/PPTX/PDF 组件预览；
  图片、TXT/CSV 预览；旧格式及组件失败时转 PDF。
- 实验创建、复制、顺序状态迁移、完成后修订、ELN 写入和真实附件增删读取。
- 超级管理员用户管理、三类角色选项、下级组织和跨组织项目成员范围检查。

### 5.1 文档图示与实现一致性检查

每次涉及领域、接口或部署边界的变更，还应检查图示是否仍然正确。图示是设计索引，不是独立事实来源；出现冲突时，以 Migration、后端服务、自动化测试和实际部署配置为准，再回写文档。

| 图示 | 对应事实来源 | 触发更新的变更 |
|---|---|---|
| 用例图 | 权限初始化、路由守卫、服务层权限校验 | 新角色、权限码或业务入口 |
| 类图/ER 图 | Flyway Migration、领域对象 | 新实体、字段、关联、约束或索引 |
| 时序图/状态机 | View、Service、异常处理、测试 | 写入流程、并发策略、状态迁移 |
| 总体架构/部署拓扑 | `infra/`、环境变量、进程管理和网络策略 | 组件、端口、存储、反向代理或备份策略 |

## 6. 发布、备份与回滚

### 6.1 发布顺序

1. 确认 `dev` 分支已通过测试并完成代码审查。
2. 备份数据库和 RustFS 对象数据，记录备份时间、版本和恢复位置。
3. 拉取目标提交，安装已锁定依赖，执行 `migrate`。
4. 构建前端，重启 API、静态资源服务和必要的 Worker。
5. 验证健康接口、登录、项目列表、文档预览和 ELN 保存。

### 6.2 回滚原则

- 先停止新版本写流量，再回滚应用版本。
- 存在数据库 Migration 时，必须先评估可逆性；不可逆 Migration 采用向前修复或恢复备份，不允许直接删除生产数据。
- 对象键、原件与业务记录一起备份；预览缓存可重新生成，原件丢失时不得用模拟内容替代。
- 每次恢复演练记录耗时、失败原因和改进措施。

### 6.3 最小备份清单

备份需要形成可追溯资产，不以“复制过目录”作为完成标志。每份备份至少记录应用提交号、Migration 状态、数据库文件/转储位置、RustFS 数据快照位置、桶清单、校验结果和操作者。

| 资产 | 备份方式 | 恢复验证 |
|---|---|---|
| PostgreSQL | 使用与部署版本兼容的逻辑备份或物理备份 | 恢复到隔离环境并执行就绪检查、项目和实验抽查 |
| PostgreSQL（开发） | 逻辑备份与受控恢复 | 启动 Spring Boot 并执行只读查询 |
| RustFS 对象原件 | 备份 `${MATERIALS_LAB_DATA_ROOT}/rustfs` 或复制私有桶到独立存储 | 比对对象清单并按文档、过程图片、结果附件抽样读取 |
| `.env` 安全备份 | 受控密钥库或服务器权限目录 | 仅确认可恢复，禁止写入 Git 或普通日志 |
| Compose 数据目录 | 按组件和应用版本记录 | 在隔离环境挂载后检查 PostgreSQL、Redis、RustFS 可启动 |

## 7. 安全与运维检查

- 生产环境使用 HTTPS、精确 `ALLOWED_HOSTS` 和 `CSRF_TRUSTED_ORIGINS`，禁止 `DEBUG=true`。
- 数据库、Redis 和 RustFS API/控制台仅对必要进程开放；Docker 端口绑定服务器回环地址。
- 每次请求必须经过 Session、权限码、组织范围、对象范围和状态校验。
- 日志应保留请求编号、资源和错误码，不记录密码、Cookie、完整 ELN 正文或文件内容。
- 监控 API 可用性、错误率、数据库连接、RustFS 容量与错误率、对象增长、LibreOffice 转换失败和任务积压。

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

### 8.3 变更检查清单

| 变更类型 | 必须同步项 |
|---|---|
| 新增或修改表字段 | Model、Migration、详细设计、序列化/服务层和测试 |
| 新增或修改 API | URL、View、OpenAPI 草案、详细设计、前端调用和测试 |
| 变更权限或对象范围 | 权限初始化数据、服务层、前端可见性、详细设计和越权测试 |
| 新增文件类型或预览能力 | 上传校验、转换/下载行为、容量限制、详细设计和人工验收 |
| 改变部署方式 | `.env.example`、部署指南、README、健康检查与回滚方案 |
| 仅文档调整 | 链接检查、`git diff --check` 和索引更新 |
