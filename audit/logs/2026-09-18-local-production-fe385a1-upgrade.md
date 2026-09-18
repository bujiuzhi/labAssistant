# 2026-09-18 本机生产 fe385a1 升级

记录时间：2026-09-18 09:59:07（Asia/Shanghai）
仓库与分支：`labAssistant` / `dev`
应用代码提交：`fe385a1`
环境：本机 Docker Compose 项目 `materials-lab-local-production`；不是目标公网服务器

## 范围与操作

- 原运行镜像为 `materials-lab-api:488eb60` 与 `materials-lab-web:488eb60`。
- 已将本机私有 `.env.production` 的发布标签切换为 `fe385a1`；未提交该配置，未输出密钥内容。
- 使用受控 `build` 构建 API/Web 镜像并生成同标签发布清单。前端容器内 26 项测试、TypeScript 检查和生产构建通过；API 镜像复用了已验证的 Maven 构建缓存。
- 使用 `upgrade --confirm materials-lab-local-production` 停止 Web/API 写入口与 RustFS，创建恢复组后启动新镜像。恢复组标识为 `20260918-095907-73898`，包含 PostgreSQL dump、RustFS 数据归档及其校验/元数据文件。

未清空、覆盖或删除 PostgreSQL、RustFS、密钥、备份、发布清单或映射数据目录；未操作远程服务器。

## 验证

- Compose 状态：PostgreSQL、RustFS、API、Web 均为 `healthy`；一次性 `rustfs-permissions` 正常退出。
- 运行镜像：API 与 Web 均为 `fe385a1`。
- `production.sh check` 返回数据库和对象存储 `ready`。
- `http://127.0.0.1:15105/` 返回 HTTP 200。

以上仅证明本机服务、依赖连接和入口可用；未执行公网访问、登录、下拉交互或完整业务验收。

## 恢复边界

如需恢复，先停止写入口，使用同一恢复组在不同 Compose 项目名、全新数据目录和空目标数据库执行 `restore-new`。不使用覆盖现有数据目录或直接回退数据库的方式。
