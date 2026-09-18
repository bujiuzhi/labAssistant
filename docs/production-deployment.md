# 生产 Compose 部署

[English](production-deployment.en.md) | [简体中文](production-deployment.md)

## 首次部署

服务器需安装 Git、Docker Engine 和 Docker Compose 插件，并能拉取基础镜像。使用同一个系统用户执行后续操作；首次安装需要构建镜像，会运行项目构建检查。

```bash
mkdir -p ~/work/server
cd ~/work/server
git clone --branch main https://github.com/bujiuzhi/labAssistant.git
cd labAssistant
if [ ! -e .env.production ]; then
  (umask 077; cp infra/.env.production.example .env.production)
fi
bash scripts/deploy.sh install
```

默认配置可以直接使用，只在需要时修改：

```ini
APP_PORT=13501
ADMIN_USERNAME=admin
ADMIN_DISPLAY_NAME=平台管理员
```

访问 `http://服务器IPv4:13501`。无需手填 Git 提交号、用户名目录、数据库参数或公网 IP。可选 `PUBLIC_HOST=服务器IPv4` 将入口限制到指定 Host；默认 `auto` 接受有效 IPv4 Host，不支持域名或 IPv6。

配置是数据文件而不是 Shell：不使用引号、变量展开、行内注释或首尾空格；不要提交真实 `.env.production`。首次安装只创建平台管理员、内部平台组织及权限字典，业务组织、业务用户、项目与实验均为空。由平台管理员创建业务组织及首个组织管理员。

初始密码在服务器上查看：

```bash
cat ~/work/server/labAssistant/data/production-secrets/bootstrap_platform_admin_password
```

密码随机生成，已有密钥不覆盖。修改配置、重启和升级不会修改已有账号的密码；账号改密后该文件也不会同步为新密码。

> 仅 Web 映射宿主端口。API、PostgreSQL 和 RustFS 不对外开放，不使用 Redis。HTTP 不加密口令、Cookie 和上传内容，请在防火墙/安全组中限制入口到可信来源。该简易方案不代表加密的公网传输；敏感数据或扩大使用范围前应启用 HTTPS。

## 升级与日常维护

在项目目录执行。先评审目标版本和数据库迁移，并安排短暂停写窗口：

```bash
git pull --ff-only origin main
bash scripts/deploy.sh upgrade
```

升级自动拉取固定版本的基础服务镜像、构建当前干净提交、生成独立发布标签，再停写并备份 PostgreSQL 与 RustFS，最后启动并检查健康状态。构建失败不会停止旧服务；进入停写阶段后失败会保持写入口停止，不自动回退数据库或启动旧代码。无需日常手工填写发布标签，也无需在升级前额外执行一次 `backup`。

| 操作 | 命令 |
| --- | --- |
| 查看状态 | `bash scripts/deploy.sh status` |
| 健康检查 | `bash scripts/deploy.sh check` |
| 重启当前版本的 API/Web | `bash scripts/deploy.sh restart` |
| 单独停写备份 | `bash scripts/deploy.sh backup` |
| 备份后恢复服务 | `bash scripts/deploy.sh up` |
| 继续启动失败部署的目标版本 | `bash scripts/deploy.sh resume` |
| 卸载容器及网络，保留数据、密钥和镜像 | `bash scripts/deploy.sh uninstall` |

`install` 仅用于首次空环境，拒绝已有部署记录、数据或项目容器。卸载后重新启用用 `up`，不要再次 `install`。脚本不提供完整删除数据的快捷命令。`backup` 完成后保持服务停止，适用于独立恢复点，不是升级的额外必做步骤。

修改端口后用 `upgrade` 应用。`restart` 和 `up` 使用上次成功部署的版本与配置，不因拉取新代码而偷偷升级。固定 Compose 项目名为 `materials-lab-production`，同一 Docker daemon 只支持一套简洁部署，并始终由同一系统用户运维；多实例、自定义目录及离线镜像交付使用高级配置。

## 数据、日志与状态

所有路径自动按执行用户的 HOME 展开：

```text
~/work/server/labAssistant/
├── .env.production
└── data/
    ├── production-secrets/       # 数据库、RustFS、初始管理员密钥
    └── deployment/
        ├── active.env            # 上次成功部署的完整配置
        ├── incomplete.env        # 仅未完成上线时存在
        └── release.env.*         # 每次构建的配置快照

~/work/data/labAssistant/
├── postgres/
├── rustfs/data/
├── rustfs/logs/
├── backups/
└── releases/                     # Git SHA 与镜像 ID 的发布清单
```

API/Web 等标准输出由 Docker 日志驱动管理，并非所有日志都映射成文件。密钥目录权限为 `0700`；文件型 Compose secret 不是加密存储，宿主管理员和 Docker 管理员可以读取。

备份包含数据库 dump、RustFS 数据归档、两份校验文件和元数据，五个文件组成完整恢复组。密钥另行安全保管；建立异机加密副本、容量监控和保留策略，脚本不会自动删除历史备份或镜像。

## 中断与恢复

上线开始前写入 `incomplete.env`，成功后才切换 `active.env`。有未完成记录时，`install/upgrade/up/restart` 会阻止隐式使用旧版本。先检查错误和依赖，修复原因后：

```bash
bash scripts/deploy.sh resume
```

此命令仅使用失败目标配置执行 `up` 和健康检查，**不重新构建、不重新执行 bootstrap、不回退数据库**。首次初始化未完成时不能仅靠 `resume` 修复：

1. 保留数据、密钥、失败配置和日志，确认 bootstrap 是否执行成功。
2. 仅确认仍是空库时，使用底层脚本对失败配置执行受空库检查保护的初始化：

   ```bash
   bash scripts/production.sh --env "$HOME/work/server/labAssistant/data/deployment/incomplete.env" bootstrap --confirm materials-lab-production
   bash scripts/deploy.sh resume
   ```

3. 若已有部分数据或迁移异常，不删除失败标记、不反复初始化，按具体错误向前修复或恢复到新的隔离环境。

首版 V1 发布后不得修改已执行迁移，后续结构变更新增 V2、V3 等。数据库不兼容时必须向前修复或恢复到新环境，不能仅回退镜像。`production.sh restore-new` 只允许不同项目名、全新数据目录和空库目标；详细参数见 `bash scripts/production.sh --help`。

高级 `rollback <旧标签> --schema-compatible` 只回退应用，要求事先核对数据库兼容性。不要直接拿 `active.env` 临时回退后继续使用简洁入口：底层回退不更新简洁入口的活动记录，应改用经核对的旧版完整配置管理后续运行，避免下一次重启恢复错误版本。

## 旧配置与高级模式

已有 `MATERIALS_LAB_*` 完整配置继续使用原文件，**不要用简洁模板覆盖**。简洁入口检测到旧格式时转交底层脚本，保留项目名、路径和显式标签；不迁移数据，也不自动管理版本。旧格式升级仍需设置新标签并先 `build`：

```bash
bash scripts/production.sh --env "$PWD/.env.production" build
bash scripts/deploy.sh upgrade
```

自定义项目名、目录、离线交付等参考[完整模板](../infra/.env.production.full.example)和[底层脚本](../scripts/production.sh)。旧流程需预先准备 Compose 中锁定的 PostgreSQL/RustFS 镜像。仅完整配置可直接用于生产 Compose；不要将三项简洁配置传给 `docker compose --env-file`。

## 验证与交付边界

```bash
bash -n scripts/deploy.sh scripts/production.sh
node --test scripts/tests/production.test.mjs
```

脚本测试使用隔离 Git/Docker 替身，不访问生产数据。真实隔离集成验收使用 `scripts/tests/production-acceptance.mjs --release <标签> --manifest <清单绝对路径>`，不能替代目标服务器验收。

每次发布核验实际访问地址、登录退出、租户/项目权限、文档上传下载、ELN、端口暴露和备份恢复。健康检查只代表依赖与服务就绪。操作记录放入 `audit/logs/`，注明 Asia/Shanghai 时间、版本、迁移、恢复组和验证结果，不记录密码或业务正文。
