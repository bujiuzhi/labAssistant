# 生产 Compose 部署

_更新时间：2026-09-17，Asia/Shanghai。_

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
| [生产 Compose](../infra/compose.production.yml) | PostgreSQL、RustFS、API、Web、一次性 bootstrap 与内部网络 |
| [生产配置模板](../infra/.env.production.example) | 非敏感的 IP、端口、目录与身份参数 |
| [生产脚本](../scripts/production.sh) | 首次部署、升级、重启、停用、预检、备份与恢复 |
| [API Dockerfile](../infra/Dockerfile.api) | Java 构建与运行镜像 |
| [Web Dockerfile](../infra/Dockerfile.web) | 前端构建与 HTTP Nginx 镜像 |
| [隔离验收](../scripts/tests/production-acceptance.mjs) | HTTP、身份、文件、备份与空库恢复验收 |

运行镜像由发布标签和发布清单约束：清单记录 Git SHA、API 镜像 ID 与 Web 镜像 ID；`up`、`upgrade`、`rollback` 均拒绝清单不匹配的镜像。运行期不从工作区构建镜像。

## 3. 常用运维命令

完成一次 `.env.production` 配置后，日常运维只使用下面的命令。脚本内部仍调用 Docker Compose、保留发布清单校验和升级备份；运维人员不必手工拆分这些步骤。

```bash
cd ~/work/server/labAssistant
LABASSISTANT_PROD_ENV="$PWD/.env.production"

# 新服务器首次部署：生成缺失密钥、准备目录、构建镜像、初始化空库并启动
bash scripts/production.sh --env "$LABASSISTANT_PROD_ENV" install --confirm materials-lab-production

# 后续发布：先停写并创建 PostgreSQL + RustFS 恢复组，再启动新版本
bash scripts/production.sh --env "$LABASSISTANT_PROD_ENV" upgrade --confirm materials-lab-production

# 日常安全重启：仅重启 API/Web，等待健康检查
bash scripts/production.sh --env "$LABASSISTANT_PROD_ENV" restart --confirm materials-lab-production

# 停用并卸载服务：仅移除本项目容器和网络，数据、备份、密钥、镜像均保留
bash scripts/production.sh --env "$LABASSISTANT_PROD_ENV" uninstall --confirm materials-lab-production
```

其中 `materials-lab-production` 必须替换为 `.env.production` 中的 `MATERIALS_LAB_COMPOSE_PROJECT` 实际值。`install` 只适用于空库，不能用于已有部署或升级；`upgrade` 需要先把新的已提交代码和新的发布标签同步到服务器。完整删除数据不由脚本提供，必须先确认恢复组后再执行受控人工操作。

## 4. 首次部署前仅需一次的配置

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

首次 `install` 会自动创建不存在的密钥目录与持久目录；若密钥目录已经存在，脚本只校验并保留，绝不覆盖。密钥目录权限为 `0700`，文件为 `0444`，不会打印密码。Compose 文件型 secret 不是加密存储；Docker 管理权限和服务器管理员均可读取，必须保护宿主机与备份。

在服务器防火墙/云安全组中仅允许可信来源访问 `15105/TCP`，不要暴露 `5432`、`8000`、`9000` 或 RustFS 控制台。若无法限制来源，任何互联网用户均可请求 HTTP 入口。

预先在构建机或目标服务器获得以下锁定镜像，离线交付需校验完整 tag@digest：

```bash
docker image inspect 'postgres:18.4-alpine@sha256:9a8afca54e7861fd90fab5fdf4c42477a6b1cb7d293595148e674e0a3181de15'
docker image inspect 'rustfs/rustfs:v1.0.0-rc.5@sha256:b7014e0ce2bc703c1316b3ef760e29dfae61fe4a50d1a66fa89638e0f8ea211f'
```

`install` 内部执行预检和 API/Web 构建测试，生成同标签镜像与 `releases/<标签>.json`，再在空库上执行 Flyway 和身份初始化。初始化只创建内部平台组织、平台管理员和全局权限字典；业务组织、项目、实验、文档和开发用户均为 0。若构建机与服务器不同，仍需受控分发两份镜像与对应清单，目标服务器使用 `up` 启动。

通过实际地址 `http://<公网 IPv4>:15105` 完成以下验收：

| 检查 | 预期 |
| --- | --- |
| 网络 | 仅 `15105/TCP` 可访问；API、PostgreSQL、RustFS 无宿主端口 |
| 登录与退出 | CSRF 生效；Session 为 HttpOnly，但不带 Secure 属性 |
| 空库 | 项目、实验、文档为空；不存在 `DEV_TEST` 与开发账户 |
| 业务 | 创建项目、上传下载文档、ELN 和权限操作按交付范围正常 |
| 时间 | JVM、数据库服务与 JDBC 会话使用 Asia/Shanghai |
| 恢复 | 完整恢复组可恢复到新隔离环境，身份与文件正文完整 |

验收后将初始化平台管理员密码转存到受控密码库。脚本不自动删除临时密钥文件、容器、镜像或数据目录。

## 5. 升级、回退与恢复

每次升级使用新的发布标签，先评审变更、迁移与停写窗口，再执行上方的单命令 `upgrade`。脚本会只读检查身份、结构和发布清单，停止 API/Web 与 RustFS，对 PostgreSQL 与 RustFS 创建同一恢复组，然后启动新版本。停止 RustFS 是为了避免直接归档其仍在写入的底层数据目录。失败时保持写入口停止；不会自动回退数据库。

首版仅有 `V1__materials_lab_schema.sql`。生产发布后不得修改 V1，所有后续结构变化只能新增 V2、V3 等迁移。若新旧 schema 已经人工确认兼容，可只回退应用镜像：

```bash
bash scripts/production.sh --env "$LABASSISTANT_PROD_ENV" rollback <旧标签> \
  --schema-compatible --confirm materials-lab-production
```

这不会降级数据库；不兼容迁移应恢复到新隔离环境或向前修复。

手工备份会停止 API/Web 与 RustFS，并在 `backups/` 生成 PostgreSQL `.dump`、RustFS `.rustfs-data.tar.gz`、两个校验文件和元数据文件；五个文件共同组成恢复组。备份完成后服务保持停止，使用 `up --confirm` 才恢复写入：

```bash
bash scripts/production.sh --env "$LABASSISTANT_PROD_ENV" backup --confirm materials-lab-production
```

备份目录无自动保留策略。完成备份后应建立异机加密副本、容量监控和经授权的清理策略。`restore-new` 仅恢复到不同 Compose 项目名、全新数据目录和空目标数据库；不使用 `--clean`、DROP 或覆盖已有目录。

## 6. 本地与隔离验证

```bash
bash -n scripts/production.sh
node --test scripts/tests/production.test.mjs
node scripts/tests/production-acceptance.mjs \
  --release <已构建标签> --manifest <发布清单绝对路径>
```

隔离验收使用 Docker 动态 HTTP 端口与独立数据目录，不读取真实生产 `.env` 或数据库。自动化结果不替代目标服务器的防火墙、真实公网 IP、浏览器交互、备份介质和风险接受验收。

每次生产操作在 `audit/logs/` 记录 Asia/Shanghai 时间、环境、Git/镜像版本、迁移、验证、备份与恢复策略；不得记录密码、令牌、Cookie 或业务正文。
