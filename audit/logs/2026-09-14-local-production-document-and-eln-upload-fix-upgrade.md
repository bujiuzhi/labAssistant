# 本机生产文档与实验附件修复升级

- 操作时间：2026-09-14 14:43:33（Asia/Shanghai）
- 环境：本机生产 Compose，项目 `materials-lab-local-production`
- 运行代码：`6e726fb`（`fix: 修复文档与实验附件上传`）
- 发布对象：API、Web；PostgreSQL 与 RustFS 保持既有持久化数据

## 执行与结果

使用现有 `scripts/production.sh` 发布流程构建已提交版本，再执行 `upgrade --confirm materials-lab-local-production`。升级先停止 API/Web 的写入入口，创建 PostgreSQL 与 RustFS 恢复组，随后重建 API/Web 容器。脚本没有执行数据初始化、清库、删除卷或 Flyway 结构迁移。

恢复组：`20260914-144256-46605`（含数据库 dump、RustFS 数据归档及校验文件；恢复前仍须在隔离目标验证）。

## 验证

- 受控构建：后端 Maven `verify` 74 项通过；前端测试 22 项通过且生产构建完成。
- `scripts/production.sh ... check`：数据库和对象存储均为 `ready`，基础身份及开发数据哨兵检查通过。
- `scripts/production.sh ... status`：API、Web、PostgreSQL、RustFS 均为 `healthy`；Web 仅监听 `127.0.0.1:15105`。
- `GET http://127.0.0.1:15105/api/v1/health/ready`：返回 `ok`。

本次未使用真实用户会话重新上传业务文件，故“保存实验计划后上传图片/附件”和已存在项目文档列表的界面级验收仍需由有权限的业务用户确认。若需回退，只能在确认数据库结构兼容后按发布脚本回退应用镜像；不自动回退数据库。
