# 2026-09-14 本机平台管理员凭据轮换

- 操作时间：2026-09-14 13:38:33 至 2026-09-14 13:40:37，Asia/Shanghai
- 环境：本机 Docker Compose 项目 `materials-lab-local-production`，公开入口 `127.0.0.1:15105`
- 操作授权：用户明确指定本项目的平台管理员用户名和密码。

## 执行与影响

- 变更前通过受控 `backup` 停止 API/Web 写入口，并生成恢复组：`backups/20260914-133833-28434.dump` 与同名 RustFS 归档、校验文件和元数据文件。
- 将唯一平台管理员的用户名更新为用户指定值，并递增 Session 版本使历史会话失效。
- 通过应用自身的登录和自助改密接口更新密码；随后原子替换该本机部署专属的 `bootstrap_platform_admin_password` Secret。
- 明文密码只存在于运行时请求和受控 Secret，未写入 Git、配置模板、文档、日志或审计记录。

## 验证与恢复

- 已验证：更新后的平台管理员登录返回 HTTP 200，组织管理接口返回 HTTP 200，租户用户管理接口返回 HTTP 403。
- 已验证：`bash scripts/production.sh --env .env.production check` 通过，服务健康。
- 恢复要点：如需恢复变更前身份与凭据状态，停止写入口后仅通过本恢复组使用 `restore-new` 恢复到新的隔离项目和全新数据目录；不得覆盖当前环境。
