# 材料实验助手后端

_当前唯一后端实现；Java/Spring Boot，接口前缀为 `/api/v1`。_

---

## 📋 模块职责

采用 Java 25、Spring Boot、Spring Security、MyBatis 和 Flyway，依赖与版本以
[pom.xml](pom.xml)为准。

| 目录 | 用途 |
| --- | --- |
| [common](src/main/java/com/materialslab/api/common/) | 响应、异常、健康检查及显式数据库迁移 |
| [identity](src/main/java/com/materialslab/api/identity/) | 身份、用户、组织、权限、开发种子初始化 |
| [bootstrap](src/main/java/com/materialslab/bootstrap/) | 独立无 Web 生产真实身份初始化，仅显式一次性运行 |
| [projects](src/main/java/com/materialslab/api/projects/) | 项目、文档及总览 |
| [experiments](src/main/java/com/materialslab/api/experiments/) | 实验计划、ELN、状态与元数据读取 |
| [迁移](src/main/resources/db/migration/) | PostgreSQL 结构的唯一来源 |
| [测试](src/test/java/) | 当前单元测试 |

## 🔧 运行与构建

建议从项目根目录按[运维指南](../docs/development-operations-guide.md)启动开发 Compose。
直接运行时须使用匹配的 JDK，并显式提供隔离数据库环境；项目不自动加载根目录 `.env`。

在项目根目录执行：

```bash
java -version
mvn -version
mvn -f backend/pom.xml verify
```

如仅需单元测试，使用 `mvn -f backend/pom.xml test`。
直接开发运行命令为 `SERVER_ADDRESS=127.0.0.1 mvn -f backend/pom.xml spring-boot:run -Dspring-boot.run.profiles=dev`；
它会连接环境指定的数据库、执行 Flyway 并补齐开发数据，不能用于生产数据库。

## ⚙️ 配置与边界

配置入口为 [application.yml](src/main/resources/application.yml)、
[dev](src/main/resources/application-dev.yml) 和 [prod](src/main/resources/application-prod.yml)。

项目文档与实验附件的新二进制正文保存在 RustFS；PostgreSQL 保存元数据、对象标识和历史 BYTEA 兼容正文。Redis 与 LibreOffice 未接入。
`/health/live` 验证进程；`/health/ready` 需全部启动任务完成、应用接受流量且数据库连接有效，
不代替身份、权限、正文完整性和业务验收。
正常 `prod` API 不自动创建身份；首次生产初始化仅创建内部平台组织、平台管理员和全局权限字典。业务组织、内置角色及其首个组织超级管理员由平台管理员开通组织时原子创建。初始化通过
[生产 Compose](../docs/production-deployment.md)的 `bootstrap` 任务显式执行，拒绝非空库和重复建账。
`dev` 不是只在空库运行初始化；`prod/dev` 混合 Profile 会在数据库连接前拒绝。
全部 Profile 使用 25 MiB 文件/27 MiB 请求限额；prod 默认启用 Secure Cookie 及 Asia/Shanghai 数据库连接会话时区。当前生产 Compose 因明确授权的 HTTP IP:15105 入口将 Cookie 覆盖为非 Secure，风险见生产部署指南。

接口字段、权限、PATCH 增量语义和剩余限制见[详细设计](../docs/detailed-design.md)及
[OpenAPI](../contracts/openapi.yaml)。数据库 COMMENT 等历史差距以详细设计登记为准，
不据已有迁移声明已满足全部新规范。
