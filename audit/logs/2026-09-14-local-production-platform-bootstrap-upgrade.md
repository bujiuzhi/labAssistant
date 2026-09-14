# 2026-09-14 本机生产平台控制面升级

- 操作时间：2026-09-14 11:28:32 至 2026-09-14 11:28:56，Asia/Shanghai
- 环境：本机 Docker Compose 项目 `materials-lab-local-production`，公开入口 `127.0.0.1:15105`
- 发布版本：`bde323c feat: 重构平台控制面首次部署`
- 数据目录：`/Users/bujiu/work/data/labAssistant/local-production-88b0b8d`

## 执行与影响

- 使用已提交且干净的工作区构建 `materials-lab-api:bde323c` 与 `materials-lab-web:bde323c`，并生成受控发布清单 `releases/bde323c.json`。
- 构建阶段执行后端 Maven 验证与前端测试、类型检查和打包；构建成功后受控升级 API/Web。
- 升级前停止 API/Web 写入口并创建恢复组：`backups/20260914-112832-14642.dump` 与同名 RustFS 归档、校验文件和元数据文件。
- 未执行 `bootstrap`、身份升级、数据库重置或结构迁移；保留现有内部平台组织与平台管理员。

## 验证与恢复

- 已验证：运行中的 API/Web 镜像均为 `bde323c`；PostgreSQL、RustFS、API 和 Web 均为健康状态。
- 已验证：`check` 及 `GET /api/v1/health/ready` 通过；业务组织、业务账号、角色和项目计数均为 0，Flyway 成功迁移数为 2。
- 未执行：平台管理员浏览器登录、组织开通与完整隔离恢复验收。
- 恢复要点：若需回退应用，先核实 schema 兼容性，再使用 `rollback <旧标签> --schema-compatible --confirm materials-lab-local-production`；如需恢复升级前数据，仅使用本恢复组的 `restore-new` 恢复到新的隔离项目与全新数据目录。
