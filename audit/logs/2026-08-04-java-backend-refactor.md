# 2026-08-04 Java 后端重构

## 变更范围

- 创建 `dev-java` 分支，将 `backend/` 从 Django/Python 切换为 Java 25、Spring Boot 4.1 和 MyBatis 4。
- 使用 Flyway 的 `V1__materials_lab_schema.sql` 维护 PostgreSQL 物理结构，并沿用既有表名和字段名。
- 迁移同源 Session/CSRF 登录、健康检查、项目核心链路和实验状态迁移；空库自动创建开发账户。
- 删除旧 Django 源码、Python 依赖和 pytest 测试资产；更新环境变量、启动文档与忽略规则。

## 验证证据

2026-08-04 以 JDK 25 与 Apache Maven 3.9.12 执行：

```bash
/private/tmp/apache-maven-3.9.12/bin/mvn -q -f backend/pom.xml test
```

命令退出码为 `0`。构建产物 `backend/target/` 已删除，未纳入版本控制。

## 数据迁移边界

上线前必须先备份 PostgreSQL 和 RustFS 数据。Flyway 的初始迁移采用 `CREATE TABLE IF NOT EXISTS`，用于接管现有同名结构或初始化空库；生产数据切换应先在隔离副本上执行登录、项目读取和实验读取的只读验收。
