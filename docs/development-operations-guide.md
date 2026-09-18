# 开发、部署与运维指南

[English](development-operations-guide.en.md) | [简体中文](development-operations-guide.md)

_适用于当前仓库；开发复现与生产准备分别说明，目标服务器参数以实际确认结果为准。_

---

## 📋 运行范围与目录

当前后端必须访问 PostgreSQL 和 RustFS：PostgreSQL 保存业务元数据与 ELN，RustFS 保存新上传项目文档和实验附件正文。
当前项目不启动也不依赖 Redis；不要为开发或生产额外添加 Redis 容器、端口或持久目录。
LibreOffice 不在当前调用链中，安装软件本身不会使 `/preview` 获得 PDF 转换能力。

| 资产 | 新服务器位置 | 要求 |
| --- | --- | --- |
| 代码与服务器私有配置 | `~/work/server/labAssistant` | 项目专属；`.env` 不提交 Git |
| 数据、备份、运行日志 | `~/work/data/labAssistant` | 项目隔离、权限受控 |
| PostgreSQL 数据 | `${MATERIALS_LAB_DATA_ROOT}/postgres` | Compose 映射至 `/var/lib/postgresql` |
| RustFS 数据与日志 | `${MATERIALS_LAB_DATA_ROOT}/rustfs` | 新上传文件正文，必须与数据库一并备份和恢复 |
| Maven 缓存 | `${MATERIALS_LAB_DATA_ROOT}/maven` | 可重新获取，不等同业务备份 |

`MATERIALS_LAB_DATA_ROOT` 必须填写服务器上的实际绝对路径。
不要照抄模板中其他用户的目录，也不要使用依赖 shell 展开的字面量 `~`。
变更已有数据挂载前先核对原路径与备份，避免新目录为空被误认为原数据丢失。

<a id="development-deployment"></a>

## 🔧 新服务器开发部署

### 1. 准备和版本确认

先确认服务器系统、SSH 用户、代码来源及目标提交、网络入口、可用资源、现有服务和数据。
以下命令假定代码已取得并位于约定目录；本指南不指定旧服务器 IP、私人仓库凭据或未经确认的生产环境。

```bash
cd ~/work/server/labAssistant
git status --short --branch
git rev-parse HEAD
docker version
docker compose version
node --version
pnpm --version
```

版本以 [Maven 配置](../backend/pom.xml)、[前端包清单](../frontend/package.json)、
[锁文件](../frontend/pnpm-lock.yaml)和 Compose 为准。当前后端要求 Java 25，
Compose 已使用对应 Maven 镜像；纯容器启动不要求宿主机安装 Java。
宿主机执行 Maven 检查时则必须让 `mvn -version` 显示匹配的 JDK。

前端容器使用 Node 24 系列；宿主机安装依赖、执行测试时沿用项目兼容的 Node，
通过已安装的 fnm/项目环境选择。pnpm 必须匹配 `packageManager`，保留锁文件，
不为解决安装问题擅自换包管理器或升级依赖。

### 2. 创建与核对配置

仅在新环境且文件尚不存在时从模板复制；已经存在的配置应先核对，不覆盖：

```bash
cd ~/work/server/labAssistant
if [ ! -e .env ]; then
  (umask 077; cp .env.example .env)
fi
mkdir -p ~/work/data/labAssistant
chmod 600 .env
```

在服务器编辑 `.env` 后再启动，至少确认下表：

| 配置 | 新环境设置原则 | 当前 Compose 行为 |
| --- | --- | --- |
| `MATERIALS_LAB_DATA_ROOT` | 实际绝对路径，项目专属 | 所有服务持久目录的根 |
| `FRONTEND_BIND_ADDRESS` | 默认设为 `127.0.0.1` | 控制前端宿主机绑定 |
| `API_BIND_ADDRESS` | 默认设为 `127.0.0.1` | 控制 API 宿主机绑定 |
| `POSTGRES_BIND_ADDRESS` | `127.0.0.1` | 限制数据库对外入口 |
| `OBJECT_STORAGE_BIND_ADDRESS` | `127.0.0.1` | 同时控制 RustFS API/控制台 |
| `SERVER_PORT` | 保持 `8000` | 容器目标端口写死为 8000，单改此值会造成不一致 |
| `POSTGRES_DEV_DB/USER/PASSWORD` | 新建隔离开发库及凭据 | API 与 PostgreSQL 容器共同使用 |
| `MATERIALS_LAB_DEVELOPMENT_PASSWORD` | 为开发新账户设置密码 | 只影响新插入账户 |
| `OBJECT_STORAGE_ACCESS_KEY/SECRET_KEY` | 新环境设置独立凭据 | RustFS 与 Java S3 客户端共同使用 |

原模板端口默认使用 `0.0.0.0`；只有已确认可信局域网/VPN 且防火墙限源时才可保留。
一般开发访问采用回环绑定加 SSH 隧道。已有数据库和存储目录的密码变更不能仅修改 `.env`，
需要对应服务的受控凭据轮换。

Compose 的 `rustfs-permissions` 会以 root 身份创建 RustFS 数据/日志目录，并递归将所有者
改为 `10001:10001`。首次使用新建隔离目录时按此准备；如果恢复或复用旧目录，先记录原 UID/GID、
确认原服务权限和恢复办法，再在获准的迁移范围内启动，不能指向其他服务的数据目录。

### 3. 安装前端依赖并启动

```bash
cd ~/work/server/labAssistant
pnpm --dir frontend install --frozen-lockfile
docker compose --env-file .env -f infra/docker-compose.yml config --quiet
docker compose --env-file .env -f infra/docker-compose.yml up -d
docker compose --env-file .env -f infra/docker-compose.yml ps
docker compose --env-file .env -f infra/docker-compose.yml logs --tail=100 api frontend
```

前端容器直接执行绑定目录内的 `node_modules/.bin/vite`，必须先在目标 Linux 环境安装依赖。
不要复制 macOS 的 `node_modules` 到 Linux。API 容器通过 Maven 开发启动，
首次执行可能需要获取依赖；容器 `running` 不代表应用已完成初始化。

Flyway 由
[SchemaMigrationInitializer](../backend/src/main/java/com/materialslab/api/common/config/SchemaMigrationInitializer.java)
显式执行首版完整 V1，随后开发初始化器补齐测试数据。每次 `dev` 启动都可能补齐示例记录并刷新统计；
禁止连接生产数据库启动该 Profile。初始化器不会覆盖已有测试用户密码，
也没有自动强制首次改密机制。

### 4. 检查并访问

默认回环配置下，在服务器执行：

```bash
curl --fail --silent --show-error http://127.0.0.1:5173/
curl --fail --silent --show-error http://127.0.0.1:8000/api/v1/health/live
curl --fail --silent --show-error http://127.0.0.1:8000/api/v1/health/ready
```

`live` 返回 `data.status=ok`；`ready` 成功返回
`data.status=ok`、`data.database=ready`，对象存储启用时还返回 `data.objectStorage=ready`。就绪检查要求应用全部启动任务（包括 Flyway）已完成并接受流量，
同时验证 JDBC 连接和 RustFS 桶可访问性；启动未就绪返回 503。它不验证登录或每个文档正文的完整性。

本机需要访问远程开发服务时，先将 `LABASSISTANT_SSH_TARGET` 设置为已确认的
`用户@主机`，再执行；本地端口需事先确认未占用：

```bash
ssh -N -o ExitOnForwardFailure=yes \
  -o ServerAliveInterval=15 -o ServerAliveCountMax=6 \
  -L 15173:127.0.0.1:5173 \
  -L 18000:127.0.0.1:8000 \
  "${LABASSISTANT_SSH_TARGET:?请先设置已确认的 SSH 用户和主机}"
```

浏览器打开 `http://127.0.0.1:15173`，后端检查可经本机 `18000` 端口完成。
若使用域名访问 Vite，还应核对 `allowedHosts`；不要通过关闭认证、CSRF 或全部 Host 校验处理连接问题。

## ⚙️ 配置如何生效

### Compose 与 Java 的区别

Compose CLI 的 `--env-file` 为配置插值提供值；容器实际收到的变量由服务的
`environment` 或 `env_file` 决定，不能假定整个 `.env` 自动传入容器。[^1]

| 参数 | Java 直接运行 | 现有 Compose API |
| --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | 可选择 Profile，默认 dev | 命令固定 `-Dspring-boot.run.profiles=dev` |
| `JDBC_DATABASE_URL` | 可覆盖数据库 URL | 固定使用 `postgres:5432/POSTGRES_DEV_DB` |
| `POSTGRES_PROD_*` | prod 读取 | 未传入，也不用于建库 |
| `SESSION_COOKIE_SECURE` | 控制 Session Cookie Secure | 未传入，应用默认 false |
| `DATABASE_MAX_POOL_SIZE` | dev 默认 10，prod 默认 20 | 未传入，使用应用默认值 |
| `SERVER_ADDRESS` | 应用绑定地址 | API 容器内固定 0.0.0.0，宿主暴露由 API_BIND_ADDRESS 控制 |
| `OBJECT_STORAGE_*` | RustFS S3 客户端连接参数 | 必填；上传、读取和就绪检查均使用 RustFS |

Spring Boot 使用环境变量、系统属性、命令行参数和应用配置等配置源；本项目没有根目录
`.env` 的自动加载器。直接运行 Maven 或 JAR 时必须由受控进程环境提供所需配置。[^2]
不要通过打印完整环境或 `docker compose config` 展开内容来收集证据，以免输出凭据；
可用 `config --quiet` 检查结构。

前端 API 固定为同源 `/api/v1`。`VITE_API_PROXY_TARGET` 只配置 Vite 开发代理，
不是生产静态文件的运行时 API 地址。生产入口必须正确转发该原路径。

### 时间与上传限额

业务和人工运维记录约定为 `Asia/Shanghai`，时间戳写作 `YYYY-MM-DD HH:mm:ss`。
部署时分别确认宿主机、容器/JVM、PostgreSQL 服务和连接会话时区。
开发 Compose 没有设置 `TZ`、JVM 时区或 PostgreSQL 会话时区；Jackson 的配置不能替代它们。
独立生产 Compose 已设置容器/JVM、数据库服务时区，prod Profile 设置 JDBC 连接会话时区；客户端仍需单独核验。

当前业务代码限制项目文档为 20 MiB；prod 显式设置 Spring 文件 20 MiB、请求 22 MiB，生产 Nginx 请求上限 22 MiB。
应用统一设置 25 MiB 文件/27 MiB 请求上限；外层网关也可能另有限额。项目文档服务仍实施 20 MiB 业务上限，前端“100 MB”提示不是服务保证。

<a id="production-deployment"></a>

## 📦 生产部署入口与交付门槛

生产运行统一使用[生产 Compose 部署指南](production-deployment.md)，其中维护实际构建、
公网 HTTP IP:13501、一次性身份初始化、升级、备份及空库恢复步骤。日常入口为 `scripts/deploy.sh`，底层 `production.sh` 保留高级恢复与旧配置兼容。本节只保留交付门槛，不复制命令。
现有开发 Compose 不能通过单改 `SPRING_PROFILES_ACTIVE` 切成生产。

| 待完成事项 | 原因及验收要求 |
| --- | --- |
| 生产运行资产 | 已提供独立 Dockerfile/Compose/Nginx，须在目标架构和服务器验证 |
| 首次身份初始化 | 显式 bootstrap 只创建真实身份，拒绝非空库；生产不使用开发样例 |
| 文件安全 | 核验 MIME/内容、上传大小、同源原件内联风险；当前没有内容签名和压缩包安全校验 |
| 会话与组织验证 | 核验登录 Session 更新、退出和组织重名用户；修复超级管理员实验关联项目的跨组织校验缺口 |
| 目标功能闭合 | 按交付范围处理成员、计划参与人、附件、预览转换、审计等缺口 |
| 网络和配置 | 仅 Web 公网 HTTP 13501、内部数据库、强凭据、HttpOnly Cookie、请求限额与时区；明文传输风险须接受 |
| 恢复能力 | 同版本恢复演练、文档正文校验、回滚或向前修复方案 |

选定经审查和验证的版本，按生产指南创建独立配置、密钥和数据目录，完成初始化与公网 HTTP 验收。
Nginx 配置保留 `/api/v1` 原路径；`proxy_pass` 的 URI 语义以配置及实际请求验证为准。[^3]
若迁移旧环境，仍需备份、恢复和停写窗口确认，不能把开发库当作生产初始数据源。

## 💾 备份、迁移与恢复

### 资产及一致性

当前版本的 PostgreSQL 备份必须包含全部业务表、历史 `project_document_content` 与
`flyway_schema_history`；同时必须备份 RustFS 桶与对象清单，仅备份元数据不能恢复新上传原件。
另保存可恢复的数据库角色/授权、受控配置、应用提交号、容器镜像及校验信息。

RustFS 桶、对象清单和挂载目录属于当前恢复单元；迁移前先核验数据库对象标识与桶内对象的引用关系。

逻辑备份使用兼容版本的 `pg_dump`，自定义归档由 `pg_restore` 恢复；
集群角色等全局对象需单独处理。备份必须在隔离环境验证可恢复。[^4]
不要把运行中的 PostgreSQL 数据目录直接复制当作一致备份。

### 操作顺序

1. 明确新旧环境、对象和停写窗口；只读核对版本、实际数据卷、数据库及服务用途。
2. 在切换窗口停止应用写入，保存数据库、配置和实际使用的对象数据，记录备份时间与校验值。
3. 在项目专属的隔离目标恢复，先运行同版本验证，再评估并执行目标版本迁移。
4. 核对表及关键记录数量、Flyway 历史、文档正文、账户角色、项目和 ELN，再开放新环境写入。
5. 保留旧环境及备份直到约定观察期结束；清理另行授权。

现有 Flyway 初始化器在代码中显式调用 `migrate()`，并固定
不启用自动 baseline；未知或已有结构的数据库必须人工核对来源，不能自动标记为已迁移。
不能只用 `SPRING_FLYWAY_ENABLED=false` 保证阻止它。
对非空旧库必须先核对结构与迁移历史；禁止把陌生旧库直接接入应用试启动。

### 回滚与记录

先停止新版本写入，再判断应用和数据库兼容性。有迁移时，单独退回 JAR 不一定恢复兼容。
选择向前修复或恢复备份；恢复前明确会影响哪些新写入，并准备补偿。
不改已执行迁移，不使用删库、删卷或覆盖数据目录作为常规故障恢复。

每次部署和恢复在 `audit/logs/` 记录：时间及语义、服务器环境标识、提交号、迁移状态、
操作对象、影响、验证、缺口、备份位置引用、恢复策略及操作者；不写秘密值或业务正文。

## ✅ 检查与验收

| 检查 | 命令或方法 | 能证明的范围 |
| --- | --- | --- |
| 文档 | 链接/锚点、标题/围栏、契约引用、图示语法、`git diff --check` | 文档结构与一致性 |
| 后端单测 | `mvn -f backend/pom.xml test` | 已有单元测试与编译 |
| 后端构建 | `mvn -f backend/pom.xml verify` | 编译、现有测试、打包生命周期 |
| 前端单测 | `pnpm --dir frontend test` | 工具函数与源码静态断言 |
| 前端构建 | `pnpm --dir frontend run build` | TypeScript 与 Vite 产物 |
| 运行 | Compose 状态、日志、live/ready | 进程与数据库连接 |
| 数据 | Flyway 历史、关键只读查询、备份恢复 | 目标环境结构及可恢复性 |
| 业务 | 登录、权限、项目、文档、ELN、版本冲突和完成只读 | 指定环境的实际交互 |

`verify` 已包含测试，不要求为了同一结论重复执行 `test`。
当前 pom 没有配置独立静态分析器；不能把 Maven verify 称为完整静态安全检查。
纯文档修改不必启动服务或连接业务库。

文档上传验收比较上传前后字节或摘要；选择当前前端可解析格式，
并单独记录旧格式/大文件转换缺口。ELN 验收应确认暂存后重新读取内容、冲突拒绝及完成后只读。
实验附件写入与正文读取已接通，但只有在隔离测试库完成上传、下载和删除往返后才能记录为端到端通过。
集成验证使用隔离测试库；读取历史报告不算本次重跑。

## 🔍 故障定位

| 现象 | 优先检查 |
| --- | --- |
| 前端容器退出、找不到 vite | 目标系统是否安装锁定依赖，绑定目录是否正确 |
| API 启动慢或退出 | Maven/JDK、依赖获取、数据库连接、Flyway 日志 |
| 修改 prod 参数仍有示例数据 | 当前 Compose 固定 dev，不能按变量名判断生效 |
| 页面正常但接口失败 | Vite/反向代理原路径、API 端口与 Cookie/CSRF |
| ready 200 但文件失败 | ready 不检查正文或预览；核验 BYTEA、权限、格式与请求限额 |
| prod 空库无法登录 | 核对是否已按生产指南显式执行 bootstrap，正常 API 不自动建账 |
| 旧文档无法转 PDF | 后端未实现转换；安装 LibreOffice 不会自动接通 |
| 计划时间或 ELN 丢失 | 创建字段支持范围、PATCH 全量正文覆盖、浏览器与数据库会话时区 |
| 附件或 /copy 返回错误 | 当前后端不存在这些接口，不是 RustFS 参数修复可解决 |
| 跨日时间偏移 | 客户端本地时区、JVM、数据库及 JDBC 会话分别核对 |

## 🔗 官方参考

以下链接用于配置和运维机制解释；项目版本仍以仓库清单和锁文件为准。

[^1]: Docker. “Set environment variables within your container's environment.” https://docs.docker.com/compose/how-tos/environment-variables/set-environment-variables/
[^2]: Spring. “Externalized Configuration.” https://docs.spring.io/spring-boot/reference/features/external-config.html
[^3]: Nginx. “Module ngx_http_proxy_module — proxy_pass.” https://nginx.org/en/docs/http/ngx_http_proxy_module.html#proxy_pass
[^4]: PostgreSQL. “SQL Dump.” https://www.postgresql.org/docs/18/backup-dump.html
