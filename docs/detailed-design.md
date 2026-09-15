# 材料实验助手详细设计

_描述当前 Java/Vue 实现及兼容性边界；结构与行为以链接的源码和迁移为依据。_

---

## 📚 模块与事实来源

| 模块 | 实现入口 | 职责 |
| --- | --- | --- |
| 公共能力 | [common](../backend/src/main/java/com/materialslab/api/common/) | 成功/分页响应、错误转换、健康检查、Flyway |
| 身份 | [identity](../backend/src/main/java/com/materialslab/api/identity/) | 登录、会话、用户、组织、权限与开发初始化 |
| 项目 | [projects](../backend/src/main/java/com/materialslab/api/projects/) | 项目、成员读取、关注、总览、文档正文 |
| 实验 | [experiments](../backend/src/main/java/com/materialslab/api/experiments/) | 实验、参与人读取、ELN、状态、附件元数据读取 |
| 页面与请求 | [frontend/src](../frontend/src/) | Vue 页面、Pinia 会话、Axios、预览分流 |

架构源图见[概要设计](overview-design.md)。版本依赖以 Maven、前端包清单及锁文件为准。
本文按领域说明语义；每个 HTTP 操作的参数、Schema 和响应统一维护在
[OpenAPI](../contracts/openapi.yaml)，避免重复维护完整接口清单。

## 💾 数据模型

### 迁移与物理约束

[V1](../backend/src/main/resources/db/migration/V1__materials_lab_schema.sql)是首次正式发布的唯一完整空库基线，创建 20 张表，
包括平台/租户身份边界、邀请码、逻辑删除字段，以及项目文档和实验附件的二进制正文表。应用启动由
[SchemaMigrationInitializer](../backend/src/main/java/com/materialslab/api/common/config/SchemaMigrationInitializer.java)
执行 Flyway；未知既有结构不会自动基线化。首版发布后不得改写 V1，只能新增 V2 及更高版本迁移。

下表为关键字段与关系导航，不是完整 DDL 副本。

| 表 | 主要字段或关系 | 当前用途 |
| --- | --- | --- |
| `organization` | `organization_code` 唯一；`parent_id` 自引用；唯一 `is_platform=true` | 平台控制面组织及业务组织层级 |
| `user_account` | `organization_id`、全局唯一 `username`、`password`、`status`、删除元数据、身份标记 | 平台管理员只能属于平台组织；组织超级管理员不能兼任平台管理员 |
| `role` | `organization_id`、`role_code`、`is_system` | 组织内角色代码唯一 |
| `permission` | `permission_code` 唯一、`module_code` | 权限字典，无通用时间字段 |
| `user_role` | `user_id`、`role_id`、`organization_id` | 用户角色组合唯一 |
| `role_permission` | `id`、`role_id`、`permission_id` | 角色权限组合唯一，无时间字段 |
| `project` | 编号、名称、类型、状态、负责人、目标/里程碑 JSONB、计划时间、`version` | 组织内编号唯一，进度有 0–100 CHECK |
| `project_member` | `project_id`、`user_id`、`member_role` | 项目用户组合唯一 |
| `project_follow` | `project_id`、`user_id`、`created_at` | 无 `organization_id` 列，经项目限定组织 |
| `project_document` | 项目、分类、名称、版本标签、`file`、MIME、大小、上传人、预览字段 | 文档元数据；没有独立 `extension` 列 |
| `project_document_content` | `document_id` 主键/外键、`content BYTEA` | 历史正文兼容读取；新上传正文存入 RustFS |
| `experiment` | 项目、编号、类型、阶段、状态、计划/实际时间、负责人、`version` | 组织内编号唯一 |
| `experiment_record` | `experiment_id` 唯一；配方/附表/附加过程 JSONB、过程/结果文本 | 一个实验至多一条 ELN 正文 |
| `experiment_participant` | 实验、用户、参与角色 | 实验用户组合唯一，响应读取 |
| `experiment_attachment` | 实验、用途、名称、`file`、MIME、大小、上传人 | 附件元数据与私有 RustFS 对象标识 |
| `business_number_sequence` | 组织、业务类型、期间、当前值 | 已建表，当前编号生成未使用 |
| `idempotency_request` | 用户、作用域、键、请求摘要、响应、过期时间 | 已建表，当前创建流程未使用 |
| `business_operation_log` | 组织、操作者、领域、对象、动作、变更 JSONB | 查询及文档上传日志 |

V1 的三个显式普通索引为 `idx_project_org_status_updated`、
`idx_experiment_org_status`、`idx_operation_log_object`。
其他唯一约束及外键以迁移为准；不要声称已有邮箱唯一、所有日期顺序 CHECK、
跨组织复合外键或幂等过期索引。

V1 为所有业务表和字段提供中文 `COMMENT`。后续结构变更须新增迁移并补充相应注释；
共享环境已执行的迁移不得改写。

<a id="time-semantics"></a>

### 时间语义

| 字段 | 语义 | 当前表示 |
| --- | --- | --- |
| `created_at/updated_at` | 记录创建/最近更新时刻 | 相关表为 TIMESTAMPTZ；不是所有表都有 |
| `planned_start_date/planned_end_date` | 项目计划起止时刻 | 名称含 date，但物理类型为 TIMESTAMPTZ |
| `estimated_start/estimated_end` | 实验预计起止时刻 | TIMESTAMPTZ，创建和更新均保存；无偏移量输入按 Asia/Shanghai 解释 |
| `started_at/completed_at` | 实验开始/完成动作时刻 | 状态迁移时由数据库写入 |
| `archived_at` | 项目归档时刻 | 归档操作写入 |
| `actual_end_at` | 项目实际结束时刻 | 表与响应保留，不代表已实现自动结项写入 |
| `milestones[].date` | 里程碑展示日期 | JSON 字符串，不与数据库时刻字段混淆 |

业务约定时区为 `Asia/Shanghai`；人工记录使用 `YYYY-MM-DD HH:mm:ss`。
API DTO 使用 `OffsetDateTime`，按实际序列化返回带偏移的日期时间，不把接口格式强改为人工记录格式。

Jackson 配置上海时区；项目无偏移时间输入按上海解释，但编号生成部分使用 JVM 默认时区。
实验时间更新在 SQL 中转为 `timestamptz`，无偏移输入依赖数据库连接会话。
生产 Compose 将容器/JVM、PostgreSQL 服务及 prod JDBC 会话配置为上海时区；既有开发运行参数保持不变。
前端 `Intl.DateTimeFormat` 未显式指定时区，输入也可能不带偏移，因此不能声称全链路已统一。

部署需分别核对 JVM、数据库服务/会话及客户端显示。若调整既有时区行为，先评估存量值、
跨日日期、无偏移输入和外部调用兼容性；不直接重写历史时间。

## 🌐 HTTP、会话与错误

### 通用协议

接口前缀为 `/api/v1`，JSON 字段为 `snake_case`。当前成功信封为
`{data,request_id}`；普通分页为
`{data,meta:{page,page_size,total,total_pages},request_id}`。
单页大小归一到 1–100，空结果的 `total_pages` 仍为 1。

`data=null` 受 NON_NULL 配置影响会被省略；创建/更新组织用户的成功体通常仅有
`request_id`。文件为二进制，注销/重置密码等 204 响应无正文。
项目文档列表使用 `total/filtered_total/category_counts` 元数据，不是普通分页。

项目路径参数支持项目 UUID 或编号；实验路径参数当前只按实验编号查询，不能传实验 UUID 替代。

普通业务异常返回 Problem Details 兼容 JSON，业务码为小写；
安全过滤器的错误只有 `status/code/detail`，不能要求所有错误都有
`request_id`、`instance` 或字段级错误列表。当前没有统一的 `X-Request-ID` 请求传播机制。
协议依据：[响应模型](../backend/src/main/java/com/materialslab/api/common/model/)、
[异常处理](../backend/src/main/java/com/materialslab/api/common/exception/GlobalExceptionHandler.java)。

### 认证流程

1. 获取 `GET /auth/csrf`，使用 `csrftoken` Cookie 与 `X-CSRFToken` 请求头。
2. 提交用户名/密码至 `POST /auth/login`；登录同样受 CSRF 保护。
3. 浏览器携带 Session Cookie，`GET /auth/session` 获取用户、角色、权限和 `is_platform_admin` 标记。
4. 退出调用 `POST /auth/logout`，随后恢复未登录状态。
5. 未登录成员只能通过 `POST /auth/register` 使用管理员签发的一次性邀请码注册；邀请码绑定组织、角色和过期时刻，数据库仅保存 SHA-256 哈希，注册不自动登录。
6. 已登录用户调用 `POST /auth/password` 提交旧密码和新密码；服务端校验旧密码及统一密码策略后失效当前 Session，客户端须重新登录。

前端仅使用同源 Cookie 的 Session/CSRF 认证；HTTP Basic 已关闭。
连续 5 次认证失败会按用户名临时限制 15 分钟，返回 429 `login_temporarily_locked`；
该保护是单 API 实例内的有界内存状态，成功认证会清除记录，横向扩容前应改用共享限流存储。
Session 当前是 Servlet 进程会话，没有 Redis 持久化实现。
身份实现见 [AuthController](../backend/src/main/java/com/materialslab/api/identity/controller/AuthController.java)
与 [SecurityConfig](../backend/src/main/java/com/materialslab/api/identity/security/SecurityConfig.java)。

登录按用户名查找活动账户，没有组织选择参数，因此首版 V1 将登录名约束为全局唯一。
首版只接受空库；若后续接入历史库且存在跨组织同名账号，必须先完成经确认的重命名，不能静默选择某个组织。无外部身份交换或单点登录 API。

空库生产 bootstrap 只建立内部平台组织中的平台管理员，业务组织数量为 0；平台管理员不属于业务租户。平台管理员通过 `GET/POST /organizations` 查看组织目录、开通组织。开通事务会创建根组织、`super_admin`、`project_manager`、`researcher` 三个内置角色及权限，并创建首个仅属于新组织的超级管理员；不会创建项目、实验、文件、邀请码或测试数据。数据库约束禁止一个账号同时取得平台与租户超级管理员特权，`is_platform_admin` 不绕过租户业务查询中的 `organization_id` 条件，也不提供组织切换能力。

### 并发与错误边界

项目和实验的详情、创建及相关更新响应返回 `ETag`。
PATCH、项目归档和实验迁移需传入 `If-Match: "版本号"`；SQL 用版本条件更新并递增版本。
冲突返回 412 `version_conflict`，无效版本文本返回 428 `if_match_required`。

项目更新/归档和实验更新/迁移将缺失或非法 `If-Match` 统一转换为 428 `if_match_required`。
其他常见业务错误为 400 校验、401 未登录/凭据错误、403 权限不足、404 不可见对象、
409 非法实验迁移和 429 登录临时限制。前端应保留待提交内容并提示重新读取版本，不直接盲重试覆盖。

项目创建虽发送 `Idempotency-Key`，后端 Controller 不读取它，也不使用幂等表。
重复提交可能创建多条记录；编号采用日期加 UUID 片段，不使用编号序列表。

<a id="access-model"></a>

## 🔐 用户、授权与数据范围模型

### 正式术语与边界

当前采用“**多租户 RBAC + 项目成员数据范围**”（tenant-scoped RBAC with project-membership data scope）：

| 术语 | 当前含义 |
| --- | --- |
| 租户（tenant）/组织 | 一个 `organization_id` 即一个数据隔离域。登录账号只能属于一个组织，用户名在全平台唯一；不支持组织切换或跨组织成员身份。 |
| 主体（subject） | 当前登录账号及其会话身份。会话中的身份在每个请求进入授权逻辑前刷新，停用、角色变更或逻辑删除都会影响后续请求。 |
| 权限（permission） | 以 `project.read`、`document.upload`、`experiment.transition` 等权限码表达的业务动作。 |
| 角色（role）与绑定 | 组织内角色通过 `user_role` 绑定给账号，再经 `role_permission` 获得权限码；当前为内置角色目录，不提供自定义角色管理。 |
| 数据范围（data scope） | 项目负责人或 `project_member` 的 `owner/manager` 关系，以及实验负责人、参与人和所属项目关系；它在动作权限之外约束具体资源。 |
| 平台控制面 | `is_platform_admin` 是账号特权标记而非租户角色，仅可管理组织目录和开通新组织，不能据此读取或管理其他组织业务数据。 |

因此，平台管理员、组织超级管理员和项目管理员不是同一层级的“全局角色”。平台管理员属于控制面；组织超级管理员是组织内业务通配特权；项目管理员、实验员属于租户 RBAC 角色。`is_super_admin` 同样是组织内账号特权标记：它跳过项目管理成员资格检查，但不绕过组织边界，也不绕过已完成实验只读规则。

### 当前授权要件

当前授权由 [AccessControlService](../backend/src/main/java/com/materialslab/api/identity/security/AccessControlService.java)、相关 Service 与 Mapper 共同实施；前端路由和按钮不构成授权依据。对受保护资源，服务端至少组合判断以下要件：

1. 会话主体有效，且账号未被停用；
2. 主体与目标资源处于同一组织；
3. 主体经角色绑定拥有所需动作权限，或具有当前组织的超级管理员特权；
4. 对项目、实验及其文档/附件等资源，主体还须满足负责人、项目成员、实验参与人或项目管理关系；
5. 资源状态规则仍会生效，例如已完成实验即使对超级管理员也不可编辑。

这是一种“RBAC 决定能做什么，数据范围决定能对哪些对象做”的组合模型。当前数据范围规则分布在项目、实验等业务服务和查询中，尚不存在统一的通用策略决策器。

### 内置角色与管理边界

[AccessControlService](../backend/src/main/java/com/materialslab/api/identity/security/AccessControlService.java)
负责权限码，Mapper/Service 负责组织、项目成员和实验关系。前端按钮不替代后端授权。

| 权限 | 项目管理员 | 实验员 |
| --- | --- | --- |
| `organization.read`、`project.read` | 有 | 有 |
| `project.create/update/archive` | 有 | 无 |
| `document.view` | 有 | 有 |
| `document.upload` | 有 | 无 |
| `experiment.read` | 有 | 有 |
| `experiment.create` | 有 | 无 |
| `experiment.update/transition` | 有 | 有 |

上表由开发和生产初始化共同复用的
[SystemIdentityCatalog](../backend/src/main/java/com/materialslab/api/identity/service/SystemIdentityCatalog.java)
定义；超级管理员具备权限通配。生产真实角色通过显式 bootstrap 任务建立，空库迁移本身不会产生身份。

普通用户读取其负责或加入的项目，并读取这些项目下以及其本人负责或参与的实验。
项目修改、归档、文档上传需相应权限以及负责人或 `owner/manager` 成员关系。
实验写入还检查负责人、参与人或具备实验创建权限的项目管理关系；
实验员不能修改归属项目或负责人。已完成实验即使超级管理员也不可编辑。

创建实验或变更 `project_id` 时先验证目标项目属于当前组织；超级管理员仅跳过项目管理成员资格检查。
这条服务端校验阻止当前 API 形成跨组织实验项目关系，数据库现有外键本身仍不是组织复合外键。

当前 `/auth/users/options` 需要 `project.create`，且仅列出当前组织的有效用户；项目/实验负责人写入也要求当前组织有效账户。
用户管理和邀请码签发/撤销均限超级管理员。邀请码不可绑定 `super_admin` 角色，并通过条件更新原子标记为已使用，避免并发重复注册。现有管理接口保护超级管理员账号，不能修改、重置密码或删除；超级管理员应使用自助改密入口。

组织超级管理员可管理当前组织的普通用户、普通角色分配和邀请码；用户管理角色选项与服务端写入均排除 `super_admin`，不能借此管理其他组织或提升普通用户为超级管理员。平台管理员在开通新组织时原子建立该组织及其首个组织超级管理员，但不管理既有租户的普通用户或业务数据。这是首版的受限管理能力，不是完整的 Administrative RBAC（行政角色授权）模型。

### 普通用户删除与历史保留

组织超级管理员可对当前组织的普通用户执行逻辑删除；服务端不物理删除 `user_account`，以保留项目、实验、文件和审计日志的外键关系。删除事务会：

1. 拒绝删除组织超级管理员、平台管理员、跨组织账号或已删除账号；
2. 若目标仍负责未归档项目或未完成实验，返回冲突并要求先在相应业务中交接负责人；
3. 撤销目标账号签发但尚未使用的邀请码，清除其 `user_role` 角色绑定；
4. 将状态设为 `deleted`、使会话版本递增、写入删除时间和操作者，并将登录名、密码哈希、显示名称和邮箱匿名化；原登录名不会重新分配；
5. 保留项目成员、实验参与人和历史审计的关联行，但成员与参与人列表不会再展示已删除账号。

请求进入时会重新加载账号；因此删除成功后，该账号已有 Session 在下一次请求立即失效。逻辑删除不可从管理界面恢复；如确需重新启用人员，应按正常流程创建一个新的普通账号。当前不提供组织管理员交接或组织归档删除；这是与账号删除不同的后续组织生命周期能力。

## 🔄 项目、实验与 ELN

### 项目

创建持久化项目并将创建者加入 `manager` 成员；负责人字段与成员角色不是自动同步关系。
PATCH 支持已处理字段的增量更新，包含基础信息、`current_stage`、目标与里程碑，
但没有保存 `member_ids` 的流程。JSON 数组提交时整体替换对应字段。

项目 UI 使用若干状态和中文类型选项，但后端并未实施对应完整白名单。
项目更新 SQL 拒绝已归档记录；`completed` 在后端仍可编辑，文档上传不检查项目终态。
前端更严格的按钮限制不能写成服务端已保证“所有终态只读”。

### 实验和正文

状态只能依次 `not_started → in_progress → completed`，迁移不自动修改 `phase`。
开始/完成时写入实际时间并增加版本。完成后 `can_edit=false`，再更新或迁移被拒绝；
没有完成后补充修订入口。

创建保存基础字段、计划时间、参与人和 ELN；参与人必须为当前组织有效用户。
ELN 初始缺失字段使用空数组/空字符串，不能宣称服务端固定初始化四列两行配方。

实验 PATCH 的基础字段、计划时间、参与人和 ELN 均支持字段级增量更新：省略字段保留现值；
计划时间传 null/空串、参与人传空数组、ELN 数组传空数组或文本传空串可显式清空。
记录数组内部仍按已提交字段整体替换，不提供数组元素级补丁。

“暂存”会提交到服务器；开始/完成也先保存再迁移，是两个 HTTP 请求。新建计划在用户首次上传过程图片或结果附件时，也会自动以当前已填写的计划字段创建 `not_started` 草稿，再继续上传；项目、实验名称、类型和目的仍为创建草稿所需的最小归属信息。
前端完成要求结果非空并确认，后端迁移没有相同非空校验。正文保存成功不等于状态迁移也成功。

页面“复制”可把已有记录填入新增编辑器后走创建 API；专用复制接口复制基础信息、计划时间、
参与人、配方及过程，状态重置为未开始，不复制结果正文和附件。
这属于前端复制草稿；请求层另有未被该页面使用的 `/copy` 封装，后端不存在该专用接口。

实现依据：[ProjectService](../backend/src/main/java/com/materialslab/api/projects/service/ProjectService.java)、
[ExperimentService](../backend/src/main/java/com/materialslab/api/experiments/service/ExperimentService.java)、
[ElnView](../frontend/src/views/ElnView.vue)。

## 📦 文档与附件

### 项目文档存储

上传参数为 `file`、`category`、`version_label`。分类为项目方案、文献资料、实验方案、
阶段报告、会议纪要和其他；版本标签是上传元数据，不是自动修订链。

[ProjectDocumentService](../backend/src/main/java/com/materialslab/api/projects/service/ProjectDocumentService.java)
先将已校验正文写入私有 RustFS，再在数据库事务中写入元数据与 `s3://桶名/对象键` 标识；若数据库写入异常会尽力删除刚写入对象，
更新项目文档数量并记录上传操作日志。授权读取经 `/content` 或 `/preview` 返回原始二进制；
下载使用 `download=true`。没有替换、删除文档和转换 PDF 的 API。

服务校验文件非空、20 MiB 业务大小上限、分类、版本标签及文件名；
项目文档业务上限为 20 MiB，Spring 文件上限为 25 MiB，生产 Nginx 请求上限为 27 MiB。
上传仅允许服务端白名单中的 Word、Excel、PowerPoint、OpenDocument、PDF、RTF、文本及位图图片；
MIME 由文件扩展名和基础文件头校验决定，不信任 multipart 声明。HTML、SVG 和未知扩展名被拒绝。
`/content` 与 `/preview` 对 Office、文本及历史异常 MIME 一律返回 `attachment`、`nosniff`、私有不缓存；
只有通过校验的 PDF 与位图图片可内联预览。当前未做杀毒、宏内容或压缩包深度扫描，受控生产环境仍应评估专用扫描链路。

### 前端预览策略

| 文件 | 前端策略 | 当前后端能力 |
| --- | --- | --- |
| DOCX、XLS/XLSX、PPTX，至多 25 MiB | 专用 Vue Office 组件 | 可读取真实原件 |
| PDF | 小文件组件，失败或大文件走浏览器 PDF | 可读取真实原件 |
| PNG/JPEG/JPG/GIF/BMP/WebP | 浏览器图片 | 可读取真实原件 |
| TXT/CSV，至多 5 MiB | 文本解码；CSV 最多 200 行、50 列 | 可读取真实原件 |
| DOC/ODT/RTF/ODS/PPT/ODP，以及部分大文件或组件失败 | 前端尝试请求 PDF 兜底 | 未实现转换，不能保证预览成功 |
| 其他格式 | 提示不支持并下载 | 仅原件读取 |

前端文本支持 UTF-8、带 BOM 的 UTF-16，并对严格 UTF-8 失败回退 GB18030。
策略实现见 [documentPreview](../frontend/src/utils/documentPreview.ts)、
[预览组件](../frontend/src/components/projects/DocumentPreviewViewer.vue)。
前端写着“服务端转换”不代表返回内容一定为 PDF。

### 实验附件

实验附件元数据保存在 `experiment_attachment`，新上传正文保存在私有 RustFS；
`experiment_attachment_content.content` 仅用于读取历史 BYTEA 正文。上传、删除和授权正文读取路由均已接通。
过程图片只允许经服务端文件头确认的 JPG/PNG，单文件限制 10 MiB，并经受会话保护的内容路由内联预览；前端不接收 RustFS 对象标识。结果附件不限制扩展名或客户端 MIME，单文件限制 300 MiB，流式写入 RustFS，并始终使用 `application/octet-stream` 和 `attachment` 下载，避免浏览器内联执行未知内容。首次上传会自动创建可审计的 `not_started` 草稿并关联附件；完成实验不可修改附件。所有附件响应带 `nosniff`、私有不缓存和最小 CSP。
附件新增或删除后实验版本递增，使响应 ETag 能反映附件列表变化。
当前仅有实现与单元测试证据，仍需在隔离数据库完成迁移和端到端上传/读取/删除验收。

## 📍 页面与验证映射

| 路由 | 当前页面 | 限制 |
| --- | --- | --- |
| `/login` | 登录 | Session/CSRF |
| `/dashboard` | 指标和未结束项目概览卡片 | 最多 10 张卡片，按关注、风险、进行中、待开始、暂停排序；当前模板无旧图表 |
| `/projects` | 查询、筛选、排序、分页与新建 | 前端选项与后端校验范围不同 |
| `/projects/:projectId` | 概览、文档资料 | 数据资产与任务页签未开放 |
| `/eln` | 计划、正文、暂存与状态 | 初次最多取前 100 条实验及项目，再本地筛选 |
| `/system/users` | 组织用户管理 | 仅超级管理员 |
| `/system/organizations` | 组织目录与开通 | 仅平台管理员 |

`/` 重定向总览，`/system` 重定向用户管理；非平台管理员不可访问组织管理路由。
UI 当前为顶部横向导航；不把新后台的默认布局规则追写成当前已实现的主题、语言或固定左栏功能。
生产静态部署需为 `createWebHistory` 配置页面回退与同源 API 代理。

后端现有测试主要覆盖 SQL 过滤、权限、项目部分写入命令/总览、完成实验只读、分页和开发文档夹具。
前端测试覆盖预览/解码/幂等键函数以及布局、样式和组件源码断言。
当前没有据此证明登录、上传、附件、存储或生产初始化全链路已通过；命令与验收入口见
[运维指南](development-operations-guide.md)。

<a id="implementation-limits"></a>

## 🔍 当前实现限制与后续变更

| 限制 | 交付影响 | 后续处理边界 |
| --- | --- | --- |
| 项目成员写入未闭合 | 页面选择不保证项目成员持久化 | 接通写入后增加角色与范围测试 |
| 实验附件尚未做数据库往返验收 | 不能仅凭单元测试宣称生产可用 | 在隔离数据库验证上传、读取、删除及大小限制 |
| 项目终态前后端限制不同 | 页面只读不能防止直接 API 写入 | 以确认的业务状态规则修复 |
| 项目文档未做杀毒、宏与压缩包深度扫描 | 已阻断 HTML/SVG 内联和客户端 MIME 欺骗，但不能宣称文件无恶意内容 | 评估隔离扫描链路并压测 |
| 正常 prod 启动不自动建立身份 | 空生产库必须先显式初始化 | 使用生产 Compose bootstrap；拒绝非空库，不重置管理员 |
| 全局用户名唯一性存在数据前置条件 | 历史跨组织重名不符合首版 V1 约束 | 上线前执行重复用户名预检并完成重命名 |
| Redis 未接入 | Session、缓存与限流均不依赖 Redis | 多实例或明确短期状态需求出现后再评估 |
| 幂等/编号表未使用，审计覆盖不全 | 不能保证创建去重或所有变更留痕 | 按实际范围实现并验证 |
| 时区、COMMENT 和关系约束有历史缺口 | 不满足全部现行规范要求 | 后续影响范围内通过评估与新增迁移处理 |
| 未实现通用 ACL、ABAC、ReBAC | 当前只能按内置角色和既有项目/实验关系授权，不能配置任意资源授权、属性策略或关系策略 | 需求出现时新增迁移与统一授权评估器；不得把名称相近的成员表直接宣称为已支持。 |
| 跨组织关系由应用层保证，现有表未使用组织复合外键 | 直接数据库写入、批处理或未来接口若绕过服务校验，可能写入组织不一致的关系 | 先检查存量数据，再以新 Flyway 迁移增加 `(id, organization_id)` 唯一约束和复合外键，并补充拒绝用例；不改写已执行的 V1。 |
| 缺少两个真实组织的端到端拒绝验证 | 现有测试不能单独证明所有跨租户路径均被拒绝 | 使用隔离数据库建立两个组织，覆盖读取、写入、成员/参与人绑定、附件和邀请等路径。 |

上述项是修复后的剩余事实与验证缺口登记，不等同于目标环境验收结论。
若属于发布目标的核心能力或安全要求，应在对应变更中解决并验证后再发布。
