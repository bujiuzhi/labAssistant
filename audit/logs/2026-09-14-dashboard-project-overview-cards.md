# 主页未结束项目概览卡片

- 操作时间：2026-09-14 16:45:20（Asia/Shanghai）
- 仓库与分支：`labAssistant` / `dev`
- 代码基线：`fefe03b`（变更提交前）
- 对象：`/api/projects/dashboard` 的项目卡片数据与主页展示。

## 决策与影响

项目主页的卡片区由“进行中项目”调整为“项目概览”。服务端在当前组织及当前用户数据范围内，查询所有未结束项目（排除 `completed`、`archived`），并按关注、风险、进行中、待开始、暂停、最近更新排序，最多返回 10 条。

项目的计划开始日期仅用于展示，不会自动修改项目状态。因此 `not_started` 项目会直接显示“待开始”状态卡片；不会因到达计划日期而被隐式改为“进行中”。接口字段由 `active_projects` 调整为 `overview_projects`，随同前端原子发布；该私有前后端契约的外部调用方如存在，需同步字段名。

## 验证与恢复

- `pnpm --dir frontend test`：24 项通过。
- `pnpm --dir frontend build`：通过。
- `docker build --progress=plain --target build --file infra/Dockerfile.api .`：通过；镜像内 Java 25 执行 76 项后端测试均通过。
- 本机 Maven 因运行时 JDK 不支持项目所需 release 25 未执行成功，正式 Docker 构建验证已覆盖后端编译与测试。

该变更不涉及数据库迁移或业务数据写入。回退时部署前一 Git 提交对应版本即可恢复旧接口与页面行为；本机升级会由生产脚本自动创建恢复组。
