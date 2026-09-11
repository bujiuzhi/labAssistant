# 生产 Compose 部署

_更新时间：2026-09-10，Asia/Shanghai。_

## 1. 范围与访问边界

本项目首版生产运行使用 Docker Compose，服务代码位于 `~/work/server/labAssistant`，持久数据位于 `~/work/data/labAssistant`。

当前经明确授权使用**无 TLS 的公网 HTTP 入口**：

```text
http://<公网 IPv4>:15105
```

仅 Nginx Web 服务映射宿主 `15105/TCP`；API、PostgreSQL 与 RustFS 均不映射宿主端口，数据库和对象存储仍位于内部网络。该方案不使用域名、证书、HTTPS、Secure Cookie 或 HSTS。

> 警告：HTTP 不加密传输登录口令、会话 Cookie 与上传内容。它只适用于已知、少量用户且已接受该风险的场景；不能被表述为 TLS 安全的公网生产服务。若接入范围扩大、数据敏感度提高或需合规审计，应先恢复 HTTPS。

生产不使用开发 Compose、开发数据、`DEV_TEST` 组织或开发账户。Redis 不在当前生产链路中；文档与附件正文存入项目专属 RustFS，PostgreSQL 保存元数据与关系。

## 2. 生产资产

| 资产 | 用途 |
| --- | --- |
| [生产 Compose](../infra/compose.production.yml) | PostgreSQL、RustFS、API、Web、一次性 bootstrap/身份边界升级与内部网络 |
| [生产配置模板](../infra/.env.production.example) | 非敏感的 IP、端口、目录与身份参数 |
| [生产脚本](../scripts/production.sh) | 预检、构建、初始化、启动、升级、备份、恢复 |
| [API Dockerfile](../infra/Dockerfile.api) | Java 构建与运行镜像 |
| [Web Dockerfile](../infra/Dockerfile.web) | 前端构建与 HTTP Nginx 镜像 |
| [隔离验收](../scripts/tests/production-acceptance.mjs) | HTTP、身份、文件、备份与空库恢复验收 |

运行镜像由发布标签和发布清单约束：清单记录 Git SHA、API 镜像 ID 与 Web 镜像 ID；`up`、`upgrade`、`rollback` 均拒绝清单不匹配的镜像。运行期不从工作区构建镜像。

## 3. 首次部署

### 3.1 准备配置与目录

```bash
cd ~/work/server/labAssistant
if [ ! -e .env.production ]; then
  (umask 077; cp infra/.env.production.example .env.production)
fi
chmod 600 .env.production
```

填写全部 `CHANGE_ME`。真实 `.env.production` 不提交 Git，不使用引号、变量展开、行内注释或首尾空格。

```ini
MATERIALS_LAB_PUBLIC_HOST=<服务器公网 IPv4>
MATERIALS_LAB_PUBLIC_PORT=15105
MATERIALS_LAB_HTTP_BIND_ADDRESS=0.0.0.0
MATERIALS_LAB_HTTP_BIND_PORT=15105
```

`MATERIALS_LAB_PUBLIC_HOST` 为纯 IPv4，不带 `http://`、端口或路径。`HTTP_BIND_PORT` 必须与公开端口一致；仅隔离验收可使用 `0` 让 Docker 动态分配端口。

目录固定如下：

```text
~/work/server/labAssistant/
├── .env.production
├── infra/ … scripts/ …
└── data/production-secrets/

~/work/data/labAssistant/
├── postgres/
├── rustfs/data/
├── rustfs/logs/
├── backups/
└── releases/
```

生成独立凭据和持久目录：

```bash
LABASSISTANT_PROD_ENV="$PWD/.env.production"
bash scripts/production.sh --env "$LABASSISTANT_PROD_ENV" secrets
bash scripts/production.sh --env "$LABASSISTANT_PROD_ENV" prepare
```

密钥目录权限为 `0700`，文件为 `0444`，不会打印或覆盖密码。Compose 文件型 secret 不是加密存储；Docker 管理权限和服务器管理员均可读取，必须保护宿主机与备份。

### 3.2 网络与镜像

在服务器防火墙/云安全组中仅允许可信来源访问 `15105/TCP`，不要暴露 `5432`、`8000`、`9000` 或 RustFS 控制台。若无法限制来源，任何互联网用户均可请求 HTTP 入口。

预先在构建机或目标服务器获得以下锁定镜像，离线交付需校验完整 tag@digest：

```bash
docker image inspect 'postgres:18.4-alpine@sha256:9a8afca54e7861fd90fab5fdf4c42477a6b1cb7d293595148e674e0a3181de15'
docker image inspect 'rustfs/rustfs:v1.0.0-rc.5@sha256:b7014e0ce2bc703c1316b3ef760e29dfae61fe4a50d1a66fa89638e0f8ea211f'
```

构建仅允许干净且已提交的 Git 工作树：

```bash
bash scripts/production.sh --env "$LABASSISTANT_PROD_ENV" preflight
bash scripts/production.sh --env "$LABASSISTANT_PROD_ENV" build
```

`build` 会执行 API/Web 构建所需测试，生成同标签 API/Web 镜像和 `releases/<标签>.json`。若构建机与服务器不同，必须一并受控分发两份镜像与该清单，目标服务器再执行预检。

### 3.3 初始化、启动与验收

```bash
bash scripts/production.sh --env "$LABASSISTANT_PROD_ENV" bootstrap --confirm materials-lab-production
bash scripts/production.sh --env "$LABASSISTANT_PROD_ENV" up --confirm materials-lab-production
bash scripts/production.sh --env "$LABASSISTANT_PROD_ENV" check
```

`bootstrap` 仅接受空库，等待 PostgreSQL 与 RustFS 健康后执行 Flyway，创建内部平台组织及平台管理员、首个真实业务组织及其超级管理员、三个组织角色和权限。平台管理员不属于业务租户，不能创建租户成员或读取业务数据；后续组织必须由其在“组织管理”中开通，开通操作原子创建新组织、三个内置角色及该组织首个管理员，不重复执行 `bootstrap`。不创建项目、实验、文档或开发用户。`up` 等待 API/Web 健康，失败时尝试停止 API/Web 写入口并保留数据与证据。

通过实际地址 `http://<公网 IPv4>:15105` 完成以下验收：

| 检查 | 预期 |
| --- | --- |
| 网络 | 仅 `15105/TCP` 可访问；API、PostgreSQL、RustFS 无宿主端口 |
| 登录与退出 | CSRF 生效；Session 为 HttpOnly，但不带 Secure 属性 |
| 空库 | 项目、实验、文档为空；不存在 `DEV_TEST` 与开发账户 |
| 业务 | 创建项目、上传下载文档、ELN 和权限操作按交付范围正常 |
| 时间 | JVM、数据库服务与 JDBC 会话使用 Asia/Shanghai |
| 恢复 | 完整恢复组可恢复到新隔离环境，身份与文件正文完整 |

验收后将初始化管理员密码转存到受控密码库。脚本不自动删除临时密钥文件、容器、镜像或数据目录。

## 4. 升级、回退与恢复

每次升级使用新的发布标签，先评审变更、迁移与停写窗口，再在已导入新镜像及清单的目标环境执行：

```bash
bash scripts/production.sh --env "$LABASSISTANT_PROD_ENV" upgrade --confirm materials-lab-production
```

升级先只读检查身份、结构和发布清单，停止 API/Web 写入，对 PostgreSQL 与 RustFS 创建同一恢复组，然后启动新版本。失败时保持写入口停止；不会自动回退数据库。

当前身份模型的 V2 迁移会拒绝“同一账号同时是平台管理员和租户超级管理员”的旧部署继续上线。首版已运行且符合“单一活动双身份管理员、无平台组织、无开发数据”的受控形态，可使用一次性拆分任务：它先在停写状态创建标记为 V1 的完整恢复组，再执行 Flyway、创建独立平台组织和平台管理员，并把旧账号收敛为其原组织的超级管理员：

```bash
bash scripts/production.sh --env "$LABASSISTANT_PROD_ENV" identity-upgrade --confirm materials-lab-production
bash scripts/production.sh --env "$LABASSISTANT_PROD_ENV" up --confirm materials-lab-production
```

该任务拒绝多名双身份管理员、已有平台组织、开发数据或重名平台账号；不得通过手工改库或复用旧账号绕过。复杂历史数据必须单独审查并演练恢复。

首版仅有 `V1__materials_lab_schema.sql`。生产发布后不得修改 V1，所有后续结构变化只能新增 V2、V3 等迁移。若新旧 schema 已经人工确认兼容，可只回退应用镜像：

```bash
bash scripts/production.sh --env "$LABASSISTANT_PROD_ENV" rollback <旧标签> \
  --schema-compatible --confirm materials-lab-production
```

这不会降级数据库；不兼容迁移应恢复到新隔离环境或向前修复。

手工备份会停写并在 `backups/` 生成 PostgreSQL `.dump`、RustFS `.rustfs-data.tar.gz`、两个校验文件和元数据文件；五个文件共同组成恢复组：

```bash
bash scripts/production.sh --env "$LABASSISTANT_PROD_ENV" backup --confirm materials-lab-production
```

备份目录无自动保留策略。完成备份后应建立异机加密副本、容量监控和经授权的清理策略。`restore-new` 仅恢复到不同 Compose 项目名、全新数据目录和空目标数据库；不使用 `--clean`、DROP 或覆盖已有目录。

## 5. 本地与隔离验证

```bash
bash -n scripts/production.sh
node --test scripts/tests/production.test.mjs
node scripts/tests/production-acceptance.mjs \
  --release <已构建标签> --manifest <发布清单绝对路径>
```

隔离验收使用 Docker 动态 HTTP 端口与独立数据目录，不读取真实生产 `.env` 或数据库。自动化结果不替代目标服务器的防火墙、真实公网 IP、浏览器交互、备份介质和风险接受验收。

每次生产操作在 `audit/logs/` 记录 Asia/Shanghai 时间、环境、Git/镜像版本、迁移、验证、备份与恢复策略；不得记录密码、令牌、Cookie 或业务正文。
