# 2026-09-18 本机生产 0fc1c8c 项目成员修复升级

记录时间：2026-09-18 10:13:57（Asia/Shanghai）
仓库与分支：`labAssistant` / `dev`
应用代码提交：`0fc1c8c`
环境：本机 Docker Compose 项目 `materials-lab-local-production`；不是目标公网服务器

## 范围与操作

- 原运行镜像为 `materials-lab-api:fe385a1` 与 `materials-lab-web:fe385a1`。
- 已将本机私有 `.env.production` 的发布标签切换为 `0fc1c8c`；未提交该配置，未输出密钥内容。
- 使用受控 `build` 构建 API/Web 镜像并生成同标签发布清单。后端固定 Java 25 构建阶段的 85 项测试、前端容器内 27 项测试、TypeScript 检查和生产构建均通过。
- 使用 `upgrade --confirm materials-lab-local-production` 停止 Web/API 写入口与 RustFS，创建恢复组后启动新镜像。恢复组标识为 `20260918-101357-76291`，包含 PostgreSQL dump、RustFS 数据归档及其校验/元数据文件。
- 升级后核对当前环境恰有一个项目、一个同组织活跃实验员，并只补写一条 `researcher` 项目成员关系。未修改组织角色、项目负责人、实验负责人、附件或其他成员关系。

## 验证

- Compose 状态：PostgreSQL、RustFS、API、Web 均为 `healthy`；一次性 `rustfs-permissions` 正常退出。
- 运行镜像：API 与 Web 均为 `0fc1c8c`。
- `production.sh check` 返回数据库和对象存储 `ready`。
- 项目成员关系共 2 条，其中实验员成员关系 1 条，符合本次指定数据修复范围。
- `http://127.0.0.1:15105/` 返回 HTTP 200。

以上仅证明本机服务、依赖连接、成员关系与入口可用；未执行目标公网访问或以实验员身份完成页面级登录验收。

## 恢复边界

如需撤销本次成员补正，可只删除该项目的对应普通 `researcher` 成员关系；如需完整恢复，先停止写入口，使用同一恢复组在不同 Compose 项目名、全新数据目录和空目标数据库执行 `restore-new`。不使用覆盖现有数据目录或直接回退数据库的方式。
