# Java 后端

该目录为材料实验助手的唯一后端实现，采用 Java 25、Spring Boot 4.1、Spring Security、MyBatis 4 和 Flyway。

## 启动与验证

```bash
mvn test
mvn spring-boot:run
```

服务默认监听 `8000`，健康检查为 `GET /api/v1/health/live`。首次连接空 PostgreSQL 时，Flyway 会执行
`src/main/resources/db/migration/` 的结构初始化，并创建 `admin`、`manager`、`researcher` 三个开发账户；
密码由 `MATERIALS_LAB_DEVELOPMENT_PASSWORD` 控制。

## 目录职责

- `common/`：统一响应、异常与健康检查。
- `identity/`：Session、CSRF、账户加载和开发数据初始化。
- `projects/`：项目、工作台和关注关系。
- `experiments/`：实验计划、默认 ELN 和状态迁移。
- `src/main/resources/db/migration/`：PostgreSQL 的唯一结构迁移来源。
