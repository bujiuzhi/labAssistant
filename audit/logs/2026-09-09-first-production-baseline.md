# 首次生产数据库基线整理记录

## 决策

- 时间：2026-09-09 12:00:00，Asia/Shanghai。
- 用户确认当前尚未进行正式生产发布，采用单一 `V1__materials_lab_schema.sql` 作为首版空库基线。
- 原开发阶段 `V2__project_document_content.sql` 与 `V3__experiment_attachments_and_global_username.sql` 的结构被并入 V1；本次不连接服务器、不迁移任何数据库、不提交或推送代码。
- 关闭 Flyway 的 `baselineOnMigrate`。未知或已有结构不得自动标记为已基线化；首版只接受空库，已有开发数据库须在受控场景重建，不能篡改 Flyway 历史。

## 结构与升级边界

- V1 创建完整的 19 张业务表，包含项目文档与实验附件正文、全局唯一登录名、所需索引及中文表/字段注释。
- 首版产品交付使用独立发布标签（例如 `v1.0.0`）和不可变镜像 ID；迁移版本不等同于产品版本。
- 首版之后只新增 V2 及更高版本迁移。兼容性变更遵循“扩展、切换、收缩”，不兼容结构不通过应用回退处理数据库降级。

## 验证与恢复

- 空 PostgreSQL 临时容器执行 V1：通过；创建 19 张表、全局用户名唯一约束、19 个表注释及 174 个字段注释。容器使用 `--rm` 和无网络模式，退出后不保留数据。
- `mvn clean test`：53 项通过，其中 `FirstProductionSchemaTest` 断言打包的首版结构完整且源目录无 V2/V3；Maven 容器和匿名缓存卷均已自动移除。
- `node --test scripts/tests/production.test.mjs`：35 项通过，其中旧 V1/V2/V3 历史、旧 V1-only 结构均会在升级或回退停写前被拒绝；`bash -n scripts/production.sh` 与 `git diff --check` 通过。
- 未参与实现的独立复核已完成：旧 V1/V2/V3 历史和旧 V1-only 结构均在升级、回退停写前被拒绝；未发现 P0/P1。
- 本次不删除或改写任何已部署数据库；若未来发现已有共享环境已执行旧 V1/V2/V3，必须停止该整理方案，保留原迁移链并另行制定兼容迁移计划。
