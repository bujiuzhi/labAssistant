# 本机生产 ELN 自动草稿附件升级

- 操作时间：2026-09-14 15:06:11（Asia/Shanghai）
- 环境：本机生产 Compose，项目 `materials-lab-local-production`
- 运行代码：`7cfd461`（`feat: 附件上传自动暂存实验草稿`）
- 发布对象：API、Web；PostgreSQL 与 RustFS 使用既有持久化数据

## 执行与结果

经当前确认执行 `scripts/production.sh ... upgrade --confirm materials-lab-local-production`。流程先停止 API/Web 写入入口，创建 PostgreSQL dump 与 RustFS 数据归档恢复组，再重建 API/Web 容器。没有执行数据初始化、清库、删除卷或 Flyway 结构迁移。

恢复组：`20260914-150541-53607`。恢复组包含数据库 dump、RustFS 数据归档及校验文件；恢复操作必须在隔离目标验证。

## 验证

- 发布构建：前端测试 23 项通过并完成类型检查与生产构建；API 构建目标使用已验证的 Maven 测试缓存层。
- `scripts/production.sh ... check`：数据库、RustFS 均为 `ready`，基础身份与开发数据哨兵检查通过。
- `scripts/production.sh ... status`：API、Web、PostgreSQL、RustFS 均为 `healthy`；Web 仅监听 `127.0.0.1:15105`。
- `GET http://127.0.0.1:15105/api/v1/health/ready`：返回 `ok`。

未以真实业务会话执行“填写新计划后首次上传”端到端验收。业务用户应刷新页面后填写项目、实验名称、类型和目的，直接选择图片或结果附件，确认页面提示自动暂存且附件出现于该草稿；结果附件可使用任意格式且单文件不超过 300 MiB。若回退，须确认数据库结构兼容后仅回退应用镜像，不自动回退数据库。
