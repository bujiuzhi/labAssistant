# 材料实验助手详细设计

## 1. 文档范围与事实来源

本文件是项目唯一的详细设计，描述 `dev-java` 分支中 Java 后端的实现结构。
实现事实以 Flyway Migration、Controller、Service、Mapper、前端接口调用和自动化测试为准。
数据资产、任务管理、检测、报告、材料主数据和完整审计查询不在当前实现范围内。

| 设计对象 | 当前事实来源 | 使用说明 |
|---|---|---|
| 数据库物理结构 | `backend/src/main/resources/db/migration/` | 新增字段必须先更新 Flyway Migration，再更新本文 |
| API 路径与行为 | `backend/src/main/java/**/controller/` | 本文仅汇总公开资源和关键行为 |
| 权限与数据范围 | `identity/service/`、Mapper、Service | 前端按钮不是授权依据 |
| 页面与交互 | `frontend/src/` | 以真实 API 返回的数据和当前页面为准 |

## 2. 实现结构

```text
backend/
├── pom.xml                       # Spring Boot 与 Maven 依赖
└── src/
    ├── main/java/                # Controller、Service、Mapper、SQLProvider 与安全配置
    ├── main/resources/db/migration/ # Flyway 数据库迁移
    └── test/java/                # JUnit 核心路径测试

frontend/src/
├── api/                          # Axios 请求封装及业务 API
├── components/projects/          # 文档、实验、数据资产、任务管理页签组件
├── views/                        # 登录、总览、项目、ELN、用户管理页面
├── stores/                       # 会话和权限状态
├── router/                       # 页面路由及访问控制
└── types/                        # 前端 API 类型
```

## 3. 数据库设计

### 3.1 通用约定

- 各表统一使用 `UUID` 主键和 `created_at`、`updated_at` 审计字段。
- 表名和字段名均为 `snake_case`，Migration 中定义中文表/字段注释。
- 业务对象以 `organization_id` 作为组织隔离键；所有对象访问先通过组织范围过滤。
- `project.version` 与 `experiment.version` 是乐观锁版本。更新请求必须携带 `If-Match: "<version>"`。
- 当前数据库迁移由 Flyway 统一管理；不以设计文档中的未来实体替代已落地实体。

### 3.2 实体关系

```mermaid
---
title: 已实现实体关系
---
erDiagram
    ORGANIZATION ||--o{ ORGANIZATION : "包含下级"
    ORGANIZATION ||--o{ USER_ACCOUNT : "拥有"
    ORGANIZATION ||--o{ ROLE : "定义"
    USER_ACCOUNT ||--o{ USER_ROLE : "分配"
    ROLE ||--o{ USER_ROLE : "被授予"
    ROLE ||--o{ ROLE_PERMISSION : "拥有"
    PERMISSION ||--o{ ROLE_PERMISSION : "组成"
    ORGANIZATION ||--o{ PROJECT : "隔离"
    USER_ACCOUNT ||--o{ PROJECT : "负责"
    PROJECT ||--o{ PROJECT_MEMBER : "包含"
    PROJECT ||--o{ PROJECT_DOCUMENT : "归集"
    PROJECT ||--o{ PROJECT_FOLLOW : "被关注"
    PROJECT ||--o{ EXPERIMENT : "包含"
    EXPERIMENT ||--|| EXPERIMENT_RECORD : "形成"
    EXPERIMENT ||--o{ EXPERIMENT_ATTACHMENT : "保存"
    EXPERIMENT ||--o{ EXPERIMENT_PARTICIPANT : "参与"
    BUSINESS_NUMBER_SEQUENCE ||--o{ PROJECT : "生成编号"
    USER_ACCOUNT ||--o{ IDEMPOTENCY_REQUEST : "发起"
    USER_ACCOUNT ||--o{ BUSINESS_OPERATION_LOG : "执行"
```

### 3.2.1 领域类图（UML）

类图强调聚合根、核心属性和关联方向，不替代上一节的字段表与 Migration。UUID、创建时间、更新时间等通用继承字段在图中省略，以保持可读性。

```mermaid
---
title: 核心领域类图
---
classDiagram
    class Organization {
        +UUID id
        +String organization_code
        +String name
        +UUID parent_id
        +String status
    }
    class User {
        +UUID id
        +String username
        +String display_name
        +Boolean is_super_admin
        +permission_codes()
    }
    class Role {
        +String role_code
        +String name
        +Boolean is_system
    }
    class Permission {
        +String permission_code
        +String module_code
    }
    class Project {
        +UUID id
        +String project_no
        +String name
        +String status
        +Integer version
        +JSON objectives
        +JSON milestones
    }
    class ProjectMember {
        +String member_role
        +DateTime joined_at
    }
    class ProjectDocument {
        +String name
        +String category
        +String file
        +Integer file_size
    }
    class Experiment {
        +UUID id
        +String experiment_no
        +String status
        +Integer version
        +String phase
    }
    class ExperimentRecord {
        +JSON formula_columns
        +JSON formula_rows
        +String process_text
        +String result_text
    }
    class ExperimentAttachment {
        +String kind
        +String name
        +String file
        +Integer file_size
    }
    class BusinessOperationLog {
        +UUID organization_id
        +String domain
        +UUID object_id
        +String action_type
        +JSON changes
    }

    Organization "1" --> "*" User : 组织用户
    Organization "1" --> "*" Role : 定义角色
    Role "*" --> "*" Permission : 授予权限
    User "*" --> "*" Role : 分配角色
    Organization "1" --> "*" Project : 数据隔离
    User "1" --> "*" Project : 负责
    Project "1" --> "*" ProjectMember : 成员关系
    User "1" --> "*" ProjectMember : 加入
    Project "1" --> "*" ProjectDocument : 归档文档
    Project "1" --> "*" Experiment : 包含实验
    User "1" --> "*" Experiment : 负责
    Experiment "1" --> "1" ExperimentRecord : ELN 正文
    Experiment "1" --> "*" ExperimentAttachment : 真实附件
    User "1" --> "*" BusinessOperationLog : 执行操作
```

### 3.3 表与关键字段

下表列出已实现表的完整业务字段集合；所有带 `*` 的表还继承或自定义了 `id` 与时间字段，外键实际列以 `_id` 结尾。

| 表 | 关键字段 | 约束/索引 | 说明 |
|---|---|---|---|
| `organization`* | `organization_code`, `name`, `parent_id`, `status` | `organization_code` 唯一；上级组织保护删除 | 组织层级与数据范围根 |
| `user_account` | `id`, `organization_id`, `username`, `display_name`, `email`, `status`, `is_super_admin`, 密码与认证字段 | 组织内用户名唯一；非空邮箱组织内唯一；`idx_user_org_status` | 登录用户 |
| `role`* | `organization_id`, `role_code`, `name`, `description`, `is_system`, `status` | `(organization_id, role_code)` 唯一 | 组织内角色 |
| `permission` | `id`, `permission_code`, `name`, `module_code`, `description` | `permission_code` 唯一 | 稳定权限码 |
| `user_role` | `id`, `organization_id`, `user_id`, `role_id`, `created_at`, `created_by_id` | `(user_id, role_id)` 唯一 | 用户角色关联 |
| `role_permission` | `id`, `role_id`, `permission_id`, `created_at`, `created_by_id` | `(role_id, permission_id)` 唯一 | 角色权限关联 |
| `business_number_sequence` | `id`, `organization_id`, `business_type`, `period_key`, `current_value`, `updated_at` | `(organization_id, business_type, period_key)` 唯一 | 项目编号序列 |
| `project`* | `organization_id`, `project_no`, `name`, `project_type_code`, `description`, `current_stage`, `progress_percent`, `document_count`, `experiment_count`, `data_resource_count`, `objectives`, `milestones`, `status`, `owner_id`, `planned_start_date`, `planned_end_date`, `actual_end_at`, `archived_at`, `version`, `created_by_id`, `updated_by_id` | 组织内项目编号唯一；日期顺序与进度范围检查；项目状态/负责人索引 | 项目聚合根 |
| `project_member` | `id`, `organization_id`, `project_id`, `user_id`, `member_role`, `joined_at`, `created_by_id` | `(project_id, user_id)` 唯一；成员查询索引 | 项目成员和负责人关系 |
| `project_follow` | `id`, `organization_id`, `project_id`, `user_id`, `created_at` | `(project_id, user_id)` 唯一；用户关注索引 | 个人工作台关注项 |
| `project_document`* | `organization_id`, `project_id`, `name`, `file`, `extension`, `mime_type`, `file_size`, `category`, `related_content`, `version_label`, `uploaded_by_id`, `updated_by_id` | 项目分类更新时间、组织扩展名更新时间索引 | 项目文档元数据和文件路径 |
| `experiment`* | `organization_id`, `project_id`, `experiment_no`, `name`, `experiment_type`, `phase`, `status`, `purpose`, `estimated_start`, `estimated_end`, `started_at`, `completed_at`, `owner_id`, `version`, `created_by_id`, `updated_by_id` | 组织内实验编号唯一；计划时间检查；组织/项目/负责人状态索引 | 实验聚合根 |
| `experiment_record`* | `experiment_id`, `formula_columns`, `formula_rows`, `extra_tables`, `process_text`, `extra_processes`, `result_text` | `experiment_id` 一对一 | 结构化 ELN 正文，不保存附件元数据副本 |
| `experiment_attachment`* | `organization_id`, `experiment_id`, `kind`, `name`, `file`, `mime_type`, `file_size`, `uploaded_by_id` | 组织、实验、用途、创建时间索引 | 真实过程图片或结果附件 |
| `experiment_participant` | `id`, `organization_id`, `experiment_id`, `user_id`, `participant_role`, `joined_at`, `created_by_id` | `(experiment_id, user_id)` 唯一；参与人查询索引 | 实验参与人 |
| `idempotency_request` | `id`, `organization_id`, `user_id`, `idempotency_key`, `route_key`, `request_hash`, `status`, `response_status`, `response_body`, `created_at`, `expires_at` | 请求作用域和幂等键唯一；过期时间索引 | 当前用于项目创建请求去重，并保留为通用幂等模型 |
| `business_operation_log` | `id`, `organization_id`, `actor_id`, `domain`, `object_id`, `object_no`, `action_type`, `description`, `changes`, `created_at` | 对象时间、操作者时间索引 | 项目、文档、实验和附件关键操作追踪 |

### 3.4 状态和值域

| 对象 | 枚举值 | 设计规则 |
|---|---|---|
| 项目 | `draft`、`not_started`、`active`、`at_risk`、`suspended`、`completed`、`archived` | 前五种可编辑；完成和归档只读 |
| 项目成员 | `owner`、`researcher`、`inspector`、`viewer` | 项目负责人须同步存在 `owner` 成员记录 |
| 项目类型 | `聚酰亚胺`、`环氧树脂` | 创建和更新均只接受需求规定值 |
| 实验 | `not_started`、`in_progress`、`completed` | 只能依次开始和完成；完成前必须填写实验结果；完成后仍可留痕修订 |
| 实验类型 | `单体`、`聚合`、`其他` | 创建和更新均只接受需求规定值 |
| 实验参与人 | `owner`、`participant`、`reviewer` | 参与关系以项目可见范围为前提 |
| 实验附件 | `process_image`、`result_file` | 附件数量和总大小由服务层校验 |
| 幂等请求 | `processing`、`completed`、`failed` | 同一键不同请求体返回校验错误 |

### 3.4.1 实验状态机（UML）

实验状态迁移只有一个正式写入口：`POST /experiments/{key}/transition`。服务端在数据库事务中校验 `experiment.execute` 权限、对象范围、`If-Match` 版本和目标状态；不允许跳过“进行中”直接完成，也不支持完成后回退。

```mermaid
---
title: 实验状态机
---
flowchart LR
    Create["创建实验"]:::process
    NotStarted["未开始<br/>phase=方案设计"]:::state
    InProgress["进行中<br/>phase=实验执行"]:::state
    Completed["已完成<br/>phase=检测分析"]:::success
    Revision["留痕修订 ELN<br/>权限 + If-Match"]:::process

    Create --> NotStarted
    NotStarted -->|"transition: in_progress<br/>记录 started_at"| InProgress
    InProgress -->|"transition: completed<br/>记录 completed_at"| Completed
    Completed -->|"保存补充记录<br/>version+1"| Revision
    Revision --> Completed

    classDef process fill:#EAF3FF,stroke:#7AA7D9,stroke-width:1px,color:#1F2A44,rx:10,ry:10;
    classDef state fill:#FFFFFF,stroke:#D4A63A,stroke-width:1px,color:#1F2A44,rx:10,ry:10;
    classDef success fill:#EEF8EC,stroke:#88B47E,stroke-width:1px,color:#1F2A44,rx:10,ry:10;
```

项目状态当前是项目属性和编辑限制条件，而不是独立状态迁移接口：`draft`、`not_started`、`active`、`at_risk`、`suspended` 允许在权限和负责人范围内编辑；`completed`、`archived` 拒绝项目编辑和文档上传。若后续增加项目状态迁移，必须先补充状态机、迁移接口、权限码和回归测试。

## 4. API 设计

全部接口前缀为 `/api/v1`，认证依赖同域 Session。响应中的正常数据使用 `data`，列表使用分页信封，错误由统一 Problem Details 处理；重要写操作返回 `ETag`，后续更新使用 `If-Match`。

| 领域 | 方法与路径 | 主要用途 |
|---|---|---|
| 健康检查 | `GET /health/live`、`GET /health/ready` | 进程存活，以及数据库、RustFS 私有桶就绪状态 |
| 认证 | `GET /auth/csrf`、`POST /auth/login`、`POST /auth/logout`、`GET /auth/session` | CSRF、登录、注销和会话恢复 |
| 用户管理 | `GET/POST /auth/users`、`GET/PATCH /auth/users/{id}`、`POST /auth/users/{id}/reset-password`、`GET /auth/users/options`、`GET /auth/roles/options` | 超级管理员用户管理与人员选项；当前通过停用而非删除账号 |
| 工作台 | `GET /dashboard` | 当前可见范围内项目/实验统计和趋势 |
| 项目 | `GET/POST /projects`、`GET/PATCH /projects/{key}` | 项目列表、创建、详情和编辑 |
| 项目关注 | `POST/DELETE /projects/{key}/follow` | 新增或取消关注 |
| 项目归档与追踪 | `POST /projects/{key}/archive`、`GET /projects/{key}/operation-logs` | 乐观锁归档和最近 100 条关键操作记录 |
| 项目文档 | `GET/POST /projects/{key}/documents`、`GET /.../content`、`GET /.../preview` | 文档列表、上传、下载与预览 |
| 实验 | `GET/POST /experiments`、`GET/PATCH /experiments/{key}`、`POST /.../copy`、`POST /.../transition` | 实验计划、ELN 更新、复制、状态迁移 |
| 实验附件 | `POST /experiments/{key}/attachments`、`GET /.../attachments/{id}/content`、`DELETE /.../attachments/{id}` | 上传、读取和删除真实附件 |

### 4.1 请求、响应与错误约定

除文件流接口外，成功响应使用 JSON。列表响应使用 `data` 数组和 `meta` 分页信息；单对象响应以 `data` 包装。分页默认每页 20 条，客户端可用 `page`、`page_size` 控制，单页最大 100 条。所有响应均由中间件写入 `X-Request-ID`；标准业务 JSON 通常同时在响应体提供 `request_id`，少量简单操作仅提供 `data`，客户端应以响应头作为统一追踪入口。

| 场景 | HTTP 状态 | 响应特征 | 客户端处理 |
|---|---:|---|---|
| 未登录或会话失效 | `401` | Problem Details，含 `request_id` | 清理会话状态并跳转登录 |
| 无操作权限 | `403` | Problem Details | 提示无权限，不应重试 |
| 对象不可见 | `404` | Problem Details | 按资源不存在处理，避免泄露跨范围对象 |
| 字段校验失败 | `400` | `code=VALIDATION_ERROR`，可含 `field_errors` | 映射到表单字段 |
| 幂等键冲突、状态不允许 | `409` | `BUSINESS_RULE_CONFLICT` 或业务冲突信息 | 刷新业务状态后由用户决定 |
| 乐观锁冲突 | `412` | `RESOURCE_VERSION_CONFLICT` | 重新读取对象，提示合并或重填 |
| 文件预览格式不支持/转换失败 | `406` 或明确业务错误 | 不返回伪造预览内容 | 提供下载原文件入口 |

Problem Details 的核心字段为 `type`、`title`、`status`、`code`、`detail`、`instance`、`request_id`，字段级错误附于 `field_errors`。前端 Axios 层统一识别这一结构，业务页面不自行拼接后端错误。

### 4.2 写入接口的并发和幂等规则

| 操作 | 必填请求头 | 服务端行为 | 返回关键头 |
|---|---|---|---|
| 创建项目 | `Idempotency-Key`，长度 16–128 | 相同用户、组织、路由与请求体的重复提交返回首次结果；同键不同请求体拒绝 | `ETag` |
| 更新项目 | `If-Match: "<version>"` | 只在版本一致、状态可编辑、对象范围允许时写入；成功后版本加一 | 新 `ETag` |
| 更新实验或 ELN | `If-Match: "<version>"` | 同时校验实验计划与 ELN 内容；包含完成后的留痕修订 | 新 `ETag` |
| 实验状态迁移 | `If-Match: "<version>"` | 仅允许未开始→进行中→已完成；完成前校验非空结果，服务端记录实际时间 | 新 `ETag` |
| 归档项目 | `If-Match: "<version>"` | 校验项目可见范围和当前状态，写入归档时间与操作记录 | 新 `ETag` |

### 4.3 关键请求字段

| 资源 | 创建/更新字段 | 关键校验 |
|---|---|---|
| 项目 | `name`、`project_type_code`、`owner_id`、`member_ids`、`objectives`、`milestones`、计划起止时间 | 创建时类型、计划时间、目标、至少一个里程碑必填；成员不重复；最多一个里程碑为 `current` |
| 项目文档 | multipart：`file`、`category`、`related_content`、`version_label` | 不超过 100 MB；允许 DOC/DOCX/PDF/XLS/XLSX/CSV/TXT/PPT/PPTX/PNG/JPG/JPEG/WebP；扩展名须与内容签名匹配 |
| 实验 | `project_id`、`name`、`experiment_type`、`purpose`、`owner_id`、`participant_ids`、计划时间、ELN 字段 | 类型限单体/聚合/其他；目的必填；参与人不重复；结束时间不得早于开始 |
| ELN 配方 | `formula_columns`、`formula_rows`、`extra_tables` | 列 ID 不重复；行数据不得包含未定义列 |
| ELN 过程与结果 | `process_text`、`extra_processes`、`result_text`；附件使用 multipart 独立接口 | 过程文字最长 50,000；单个附加过程 1,000 字；过程图片最多 20 张且单张 10 MB；结果附件最多 30 个且单个 25 MB |

### 4.4 项目更新时序

```mermaid
---
title: 项目编辑与乐观锁时序
---
sequenceDiagram
    participant UI as 前端
    participant API as Spring Boot API
    participant DB as 数据库

    UI->>API: GET /projects/{key}
    API->>DB: 按组织和可见范围查询
    DB-->>API: 项目和 version
    API-->>UI: data + ETag "version"
    UI->>API: PATCH /projects/{key} + If-Match
    API->>DB: select_for_update + 权限/状态校验
    alt version 一致
        API->>DB: 写入字段、成员、version+1
        DB-->>API: 更新结果
        API-->>UI: data + 新 ETag
    else version 不一致
        DB-->>API: 当前版本不同
        API-->>UI: 412 Precondition Failed
    end
```

## 5. 关键实现细节

### 5.1 项目创建与成员同步

项目创建要求 `Idempotency-Key` 长度为 16–128。服务层在事务内锁定
`business_number_sequence` 生成 `PRJ-年份-六位序号`，创建项目后同步负责人和普通成员关系；
创建人不是负责人且未被选择时自动加入项目。负责人和成员必须属于本人组织或下级组织的有效用户。

### 5.2 实验与 ELN

实验编号、负责人、项目、状态和计划时间采用规范化字段；新记录默认创建 4 列、2 行配方表。
动态配方列、配方行、附加表和过程模块以 JSON 保存到 `experiment_record`；过程图片和结果附件只存于
`experiment_attachment`，序列化输出实时派生，不保留第二份 JSON 元数据。已完成实验仍可补充 ELN，
每次保存均校验 `If-Match` 并写入业务操作日志。

### 5.3 文件与预览

项目文档和实验附件的存储路径由服务端生成，路径按组织与业务对象隔离。项目文档预览使用
真实文件并按能力分流：

| 格式 | 首选预览 | 自动兜底 |
|---|---|---|
| DOCX | `@vue-office/docx` 组件读取授权原件 | LibreOffice 转 PDF，再由 PDF 组件显示 |
| XLS、XLSX | `@vue-office/excel` 组件读取授权原件 | LibreOffice 转 PDF，再由 PDF 组件显示 |
| PPTX | `@vue-office/pptx` 组件读取授权原件 | LibreOffice 转 PDF，再由 PDF 组件显示 |
| PDF | `@vue-office/pdf` 组件 | 浏览器原生 PDF 查看器 |
| DOC、PPT、RTF、ODT、ODS、ODP | LibreOffice 转 PDF | 下载原件 |
| TXT、CSV | 文本/CSV 表格组件，限制预览体积和行列数 | 大文件转换 PDF 或下载原件 |
| PNG、JPG/JPEG、WebP、GIF、BMP | 浏览器图片组件 | 下载原件 |

前端组件预览上限为 25 MB，文本直接解析上限为 5 MB；超过边界的办公文档使用服务端转换。
上传端校验扩展名、文件签名、压缩包条目数、解压后总体积、加密标记和目录穿越路径。所有组件
只接收通过当前 Session 权限读取的二进制数据，不使用第三方公网预览服务。转换失败时只允许
下载原件，禁止显示模拟内容。每次 LibreOffice 转换使用独立临时目录和用户配置目录，避免并发
请求共享进程配置或锁文件；转换结果按文档版本写入 RustFS 预览缓存。

### 5.4 权限判定顺序

1. Session 用户有效且具备对应权限码。
2. 目标对象属于本人组织或下级组织，或者当前用户是跨组织项目成员。
3. 实验可见范围从可见项目、实验负责人和参与人关系合并得出。
4. 对象状态允许该动作。
5. 不可见对象以 404 返回，明确动作禁止以 403 返回。

当前开发初始化的角色权限如下；生产环境可通过用户管理分配已有系统角色，但新增权限码必须同时修改初始化命令、后端服务和本文件。

| 权限码 | 超级管理员 | 项目管理员 | 实验员 | 含义 |
|---|---|---|---|---|
| `project.view` | 有 | 有 | 有 | 查看组织层级范围及成员关联项目 |
| `project.view_all` | 有 | 无 | 无 | 超级管理员兼容权限码；业务选择器仍执行统一范围合并 |
| `project.create`、`project.update`、`project.manage_members` | 有 | 有 | 有 | 创建、编辑、归档项目与维护成员 |
| `document.view`、`document.upload` | 有 | 有 | 有 | 查看、预览、下载和上传授权项目文档 |
| `experiment.view` | 有 | 有 | 有 | 查看可见项目和参与关系下的实验 |
| `experiment.view_all` | 有 | 无 | 无 | 超级管理员兼容权限码；业务选择器仍执行统一范围合并 |
| `experiment.create`、`experiment.update`、`experiment.execute` | 有 | 有 | 有 | 创建、编辑、复制、迁移实验与维护 ELN |

### 5.5 文件约束与存储语义

文件二进制数据不写入 JSON 正文：项目文档存于 `project-documents/<organization>/<project>/`，实验附件存于 `experiment-attachments/<organization>/<experiment>/`。路径采用服务端 UUID 文件名，原文件名只是展示和下载元数据，因此不能由客户端路径决定授权。

项目文档分类固定为项目方案、文献资料、实验方案、阶段报告、会议纪要和其他。实验附件用途固定为过程图片或结果附件。ELN 的 `process_images` 和 `result_files` 输出由附件实际记录生成，客户端不能仅通过提交 JSON 声明一个不存在的文件。

## 6. 前端设计

- 路由覆盖登录、工作台、项目列表、项目详情、ELN 和用户管理。
- 项目详情由概览、文档资料、实验管理、数据资产、任务管理五个页签组成；后两项仅展示研发中状态。
- 概览使用真实基础信息、里程碑、最近实验、成员角色和项目操作记录，项目头部提供编辑、新建实验和归档入口。
- 文档预览使用服务端 `/preview` 地址，下载始终读取 `/content?download=1` 原文件。
- `sessionStore` 保存当前会话和权限码；页面按钮仅作体验提示，后端是最终权限裁决点。
- Axios 请求层统一处理 CSRF、Problem Details、会话失效和乐观锁冲突。

### 6.1 页面与真实数据映射

| 页面 | 主要 API | 不应使用的兜底行为 |
|---|---|---|
| 登录页 | `GET /auth/csrf`、`POST /auth/login`、`GET /auth/session` | 不能仅在浏览器本地写入“已登录”标记 |
| 工作台 | `GET /dashboard` | 不能静态拼接项目数、实验数或趋势数据 |
| 项目列表/详情 | `GET/POST/PATCH /projects` | 项目成员、里程碑和统计必须来自 API 返回 |
| 项目文档页签 | 文档列表、上传、`content`、`preview` | 预览失败时不能展示本地示例或模拟文本 |
| 实验管理/ELN | 实验列表、详情、复制、迁移、附件增删接口 | 不得将浏览器本地文件名或预览 URL 当作已保存附件 |
| 用户管理 | 组织用户、角色选项与密码重置接口 | 普通用户不得通过路由直达绕过超级管理员限制 |

## 7. 部署与配置

| 配置项 | 作用 | 注意事项 |
|---|---|---|
| `.env` | Java 服务、数据库、对象存储、开发初始化参数 | 不提交仓库；以 `.env.example` 为模板 |
| `DATABASE_ENGINE` | `postgresql` 或 `sqlite` | 环境切换前执行 Migration |
| `OBJECT_STORAGE_*` | RustFS endpoint、私有桶、区域、SigV4 凭据和 TLS 校验 | 密钥不提交；应用端使用 path-style S3 |
| `MEDIA_ROOT` | 切换对象存储前的历史原件迁移源或测试文件系统 | 迁移后只保留受控回滚副本，不作为运行时正式存储 |
| `ALLOWED_HOSTS`、`CSRF_TRUSTED_ORIGINS` | 访问域名与 CSRF 来源 | 生产环境必须精确配置 |
| LibreOffice | 办公文档预览转换依赖 | 运行前确认 `libreoffice` 或 `soffice` 可执行 |

建议上线流程：备份数据库与对象原件 → 启动 RustFS 并执行 `bootstrap_object_storage` →
执行 `migrate` → 必要时运行 `migrate_media_to_object_storage` 及 `--verify-only` →
运行后端测试和前端构建 → 发布静态文件/API 进程 → 检查健康接口、登录、项目文档预览和 ELN 写入。

## 8. 测试与验收映射

| 验收链路 | 已有测试位置 | 核心断言 |
|---|---|---|
| 认证与会话 | `backend/src/test/java/**` | 登录、会话、CSRF 与权限 |
| 项目管理 | `backend/src/test/java/**` | 创建、编辑、成员、乐观锁和状态限制 |
| 项目文档 | `backend/src/test/java/**`、`frontend/tests/document-preview.test.ts` | 上传、下载、可信 MIME、压缩包安全、组件分流、文本解析、PDF 原件和办公转换 |
| 实验与 ELN | `backend/src/test/java/**` | 必填与类型、默认配方、状态迁移、完成后修订、附件增删 |
| 用户与组织范围 | `backend/src/test/java/**`、项目/实验范围用例 | 超级管理员保护、三角色选项、下级组织和跨组织成员 |
| 系统初始化 | `backend/src/test/java/**` | 开发种子数据 |
| 对象存储 | `backend/src/test/java/**` | 禁用配置保护、原件迁移、重复校验和就绪状态 |

本基线下，后端完整测试命令为：

```bash
conda run -n materials-lab-assistant mvn -f backend/pom.xml test
```

前端构建命令为：

```bash
conda run -n materials-lab-assistant pnpm --dir frontend run build
```

## 9. 后续设计约束

- 新增表必须先补充 Migration 与本文件第 3 节，再更新数据字典。
- 新增接口必须补充 OpenAPI 契约、权限码、对象范围和测试用例。
- 文件对象存储迁移时，须保留现有 `ProjectDocument`、`ExperimentAttachment` 的业务主键与下载授权语义。
- 检测、报告、审计查询等未来领域不得绕过现有组织隔离、事务和版本控制约束。
