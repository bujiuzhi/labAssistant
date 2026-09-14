# 本机生产 ELN 附件预览与大文件升级

- 操作时间：2026-09-14 14:57:34（Asia/Shanghai）
- 环境：本机生产 Compose，项目 `materials-lab-local-production`
- 运行代码：`f8a2161`（`fix: 修复实验附件预览与大文件上传`）
- 发布对象：API、Web；PostgreSQL 与 RustFS 持久化数据保持原状

## 执行与恢复

通过既有 `scripts/production.sh` 构建提交 `f8a2161` 的 API/Web 镜像后，执行受控 `upgrade`。升级先停止 API/Web 写入入口，生成恢复组 `20260914-145659-50605`，再重建 API/Web。恢复组包含 PostgreSQL dump、RustFS 数据归档及校验文件；恢复前仍须在隔离目标执行验证。

本次没有运行数据库初始化、数据清理、卷删除或 Flyway 结构迁移。若需回退，只能在确认结构兼容后使用发布脚本回退应用镜像，不能自动回退数据库。

## 验证

- 构建时：后端 Maven `verify` 76 项通过；前端 23 项测试与生产构建通过。
- `scripts/production.sh ... check`：数据库和对象存储均为 `ready`，基础数据检查通过。
- `scripts/production.sh ... status`：运行 API/Web 镜像均为 `f8a2161`，API、Web、PostgreSQL、RustFS 均为 `healthy`；Web 仅映射 `127.0.0.1:15105`。
- `GET http://127.0.0.1:15105/api/v1/health/ready`：返回 `ok`。
- 容器内 Nginx 生效配置：`client_max_body_size 310m`、上传/代理超时 600 秒、`proxy_request_buffering off`。

未在真实用户会话中上传 300 MiB 文件或截图中的已有图片，因此界面级验收仍待有实验读写权限的用户刷新页面后确认。
