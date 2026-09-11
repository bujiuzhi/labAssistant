# 2026-09-11 本机生产身份边界升级

- 操作时间：2026-09-11 16:10:33，Asia/Shanghai
- 环境：本机独立 Compose 项目 `materials-lab-local-production`
- 发布提交与标签：`e7bdff6d9fcf47a08ba5a07fe721421fe37132ed`
- 数据目录：`/Users/bujiu/work/data/labAssistant/local-production-88b0b8d`

## 执行与影响

- 已停止旧 API/Web，在停写状态创建首版 V1 PostgreSQL 与 RustFS 完整恢复组：`backups/20260911-161033-16675.dump` 及同名 RustFS 归档、校验与元数据文件。
- 已运行受控 `identity-upgrade`：Flyway 从 V1 升至 V2；旧 `admin` 收敛为首个业务组织超级管理员；创建内部平台组织与独立 `platform_admin`。平台账号初始密码仅保存在受控 Secret 文件，不记录明文。
- 已用新发布镜像重新创建 API/Web；PostgreSQL、RustFS 数据目录未删除或覆盖。

## 验证与恢复

- `up` 与 `check` 成功：API、Web、PostgreSQL、RustFS 健康；数据库与对象存储就绪。
- 本机 HTTP 会话验证：`admin` 返回 `is_super_admin=true`、`is_platform_admin=false`；`platform_admin` 返回相反标记；平台账号读取 `/api/v1/auth/users` 返回 403。
- 尚未执行浏览器视觉验收或隔离恢复演练。若需回退，保持 API/Web 停止并使用此 V1 恢复组恢复到全新隔离目标；不得覆盖当前数据目录。
