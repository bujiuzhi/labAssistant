# 材料实验助手概要设计

## 1. 文档目的与基线

本文件是项目唯一的概要设计，面向评审、实施和交接。它描述已实现系统的范围、架构、关键约束、路线图与部署方式；字段级定义、接口参数和测试用例以《详细设计》和代码实现为准。

| 属性 | 值 |
|---|---|
| 文档状态 | 已实现基线，待业务评审 |
| 代码基线 | `dev` 分支，提交 `72d62d1` |
| 更新日期 | 2026-07-27 |
| 适用范围 | 项目管理、项目文档、实验管理、电子实验记录本、用户管理 |
| 正式细化来源 | [详细设计](detailed-design.md)、`backend/apps/`、Django Migration、自动化测试 |

## 2. 建设目标与范围

材料实验助手是面向材料研发团队的独立 Web 系统，以组织为数据隔离边界，以项目为协作边界，以实验与电子实验记录本（ELN）为过程证据载体。

首期已实现能力如下：

- 项目创建、查询、编辑、成员管理、里程碑、关注和总览统计。
- 项目文档上传、分类检索、原文件下载和实际文件预览。
- 实验创建、筛选、编辑、复制、开始/完成状态迁移和项目内实验视图。
- ELN 的结构化配方、过程、结果、附件与版本并发控制。
- 超级管理员用户管理、角色选项和密码重置；组织内 RBAC 与对象范围控制。

首期不包含检测委托、报告生成、材料主数据、对象存储直传和完整审计查询。相关设计意图保留在 `audit/` 的历史材料中，不应被误认为已经上线的功能。

### 2.1 业务角色与使用边界

系统同时使用“组织级角色”和“项目/实验内角色”。组织级角色决定能否进入某项能力；项目、实验内关系决定可见对象和协作身份。前端根据权限隐藏不适用操作，后端仍会逐次校验，不以页面按钮作为授权依据。

| 使用者 | 组织级能力 | 项目或实验内职责 | 典型操作 |
|---|---|---|---|
| 超级管理员 | 管理组织用户、重置密码、查看和维护全部业务数据 | 可被加入项目，但不因超级管理员身份自动改变项目成员展示 | 创建用户、分配系统角色、处理账号停用 |
| 项目负责人 | 项目创建、编辑、成员维护、文档上传和实验管理 | 维护项目目标、成员、里程碑与负责人 | 创建项目、调整计划、分配成员、查看项目内实验 |
| 研究人员 | 有权限且具备对象范围时可读写项目与实验 | 记录配方、过程、结果和附件 | 创建/编辑实验、维护 ELN、上传过程资料 |
| 检测人员 | 按系统角色和项目范围访问 | 在项目内作为成员，或在实验内作为参与人/复核人 | 查看实验、参与记录和结果复核 |
| 只读成员 | 仅项目或实验查看能力 | 不能写入业务数据 | 查看项目资料、实验记录和附件 |

### 2.2 范围判定原则

1. 页面显示“数据资产”页签，但当前只保留研发中提示，不产生数据资产记录。
2. 文档资料和 ELN 附件均保存真实上传文件；演示数据仅用于开发初始化，不能作为生产事实来源。
3. 已完成或归档的项目、已完成的实验不再允许正常编辑，避免实验过程证据被事后无痕改写。
4. 外部系统没有直接写入数据库的接口；未来对接必须经过 API 契约、组织隔离、权限和审计评审。

## 3. 技术选型

| 层级 | 已采用技术 | 选型原因 |
|---|---|---|
| 前端 | Vue 3、TypeScript、Vite、Pinia、Vue Router、Element Plus、ECharts | 适合单页业务系统、类型化接口和组件化交互 |
| 后端 | Python 3、Django、Django REST Framework | 提供 ORM、迁移、会话认证、权限校验和管理能力 |
| 数据库 | PostgreSQL（部署配置）；开发可切换 SQLite | 关系约束、事务和 JSON 字段满足业务模型需求 |
| 缓存/任务 | Redis、Celery（基础配置已预留） | 为后续异步报告、文件处理和通知提供边界 |
| 文件预览 | Django `FileField`、LibreOffice 无头转换 | PDF/图片读取原件，办公文档转换为真实 PDF 后内嵌预览 |
| 部署 | Conda 运行环境、Docker Compose（PostgreSQL/Redis） | 便于局域网部署和数据卷隔离 |

## 4. 总体架构

```mermaid
---
title: 材料实验助手总体架构
---
flowchart TB
    Browser["浏览器<br/>Vue 单页应用"]:::process
    Web["Vite 静态资源服务<br/>开发环境"]:::process
    Api["Django REST API<br/>Session / CSRF / RBAC"]:::process
    Project["项目域<br/>项目、成员、文档、关注"]:::process
    Experiment["实验域<br/>实验、ELN、附件、状态迁移"]:::process
    Identity["身份域<br/>组织、用户、角色、权限"]:::process
    Db[("PostgreSQL / SQLite<br/>业务数据与版本")]:::storage
    Media[("本地 MEDIA_ROOT<br/>文档与实验附件")]:::storage
    Preview["LibreOffice<br/>办公文档转 PDF"]:::process
    Redis[("Redis / Celery<br/>后续异步边界")]:::storage

    Browser -->|"HTTPS / HTTP"| Web
    Browser -->|"/api/v1/*"| Api
    Api --> Project
    Api --> Experiment
    Api --> Identity
    Project --> Db
    Experiment --> Db
    Identity --> Db
    Project --> Media
    Experiment --> Media
    Project --> Preview
    Preview --> Media
    Api -. "后续任务" .-> Redis

    classDef process fill:#EAF3FF,stroke:#7AA7D9,stroke-width:1px,color:#1F2A44,rx:10,ry:10;
    classDef storage fill:#FDECF2,stroke:#C989A1,stroke-width:1px,color:#5D273B,rx:10,ry:10;
```

## 5. 领域边界与职责

| 领域 | 主要实体 | 对外职责 | 不承担的职责 |
|---|---|---|---|
| 身份与权限 | 组织、用户、角色、权限 | 登录、会话、超级管理员用户管理、权限码判定 | 不替代项目或实验对象范围判断 |
| 项目管理 | 项目、成员、里程碑、关注 | 项目生命周期、成员协作、概览统计、业务编号 | 不保存 ELN 正文 |
| 项目文档 | 项目文档、预览缓存 | 上传、分类、下载和真实在线预览 | 不伪造或解析虚构内容 |
| 实验与 ELN | 实验、参与人、ELN、附件 | 实验计划、状态迁移、过程和结果留存 | 不实现检测或报告业务 |
| 公共能力 | 幂等请求、分页、错误响应、请求编号 | 创建去重、并发冲突、统一错误模型 | 不包含业务规则本身 |

## 6. 关键流程

### 6.1 项目到实验的业务闭环

```mermaid
---
title: 项目、实验与 ELN 主流程
---
flowchart LR
    CreateProject["创建项目<br/>生成 PRJ 编号"]:::process
    Members["设置负责人和成员"]:::process
    Milestones["维护里程碑与目标"]:::process
    CreateExperiment["创建实验<br/>生成 EXP 编号"]:::process
    Record["填写 ELN 与上传附件"]:::process
    Transition{"状态迁移<br/>版本是否一致"}:::decision
    Completed["完成实验<br/>只读记录"]:::success
    Conflict["返回 412 / 提示刷新"]:::failure

    CreateProject --> Members --> Milestones --> CreateExperiment --> Record --> Transition
    Transition -->|"一致"| Completed
    Transition -->|"版本冲突"| Conflict

    classDef process fill:#EAF3FF,stroke:#7AA7D9,stroke-width:1px,color:#1F2A44,rx:10,ry:10;
    classDef decision fill:#FFFFFF,stroke:#D4A63A,stroke-width:1px,color:#1F2A44,rx:10,ry:10;
    classDef success fill:#EEF8EC,stroke:#88B47E,stroke-width:1px,color:#1F2A44,rx:10,ry:10;
    classDef failure fill:#FDECEC,stroke:#D98989,stroke-width:1px,color:#6A2525,rx:10,ry:10;
```

### 6.2 文档预览流程

1. 当前用户先通过 `document.view` 权限和项目可见范围校验。
2. PDF、PNG、JPG、JPEG、WebP 直接以内联方式读取原始文件。
3. DOC/DOCX、XLS/XLSX/CSV、PPT/PPTX、TXT 通过 LibreOffice 转换为缓存 PDF。
4. 前端在弹窗中嵌入预览地址；不能转换或未支持的格式明确提示下载原文件。
5. 预览缓存存于 `MEDIA_ROOT/project-document-previews/<document_id>/<更新时间>/`，原文件更新后自动使用新缓存目录。

## 7. 安全与一致性原则

- 使用 Django Session 与 CSRF，浏览器不保存认证令牌。
- 所有业务查询以组织范围开头，再叠加项目成员、负责人、实验参与人等对象范围。
- 项目和实验更新使用 `ETag` / `If-Match` 对应的 `version` 乐观锁；版本冲突返回 `412`。
- 项目创建使用 `Idempotency-Key`，相同键只能重放相同请求。
- 项目与实验附件采用服务端生成路径，原始文件名仅作为元数据保留。
- 已完成、已归档项目及已完成实验的可写能力受状态机限制。

## 8. 部署架构与运行边界

开发服务器当前采用前后端分离进程：前端 Vite 监听 `5173`，Django API 监听 `8000`。`infra/docker-compose.yml` 为 PostgreSQL 和 Redis 提供独立数据卷映射；环境变量由未提交的 `.env` 文件提供。

| 组件 | 运行方式 | 数据位置/依赖 | 运维要点 |
|---|---|---|---|
| 前端 | `pnpm --dir frontend dev -- --host 0.0.0.0` | API 同源代理或 `/api/v1` 路径 | 生产环境应构建为静态文件并由反向代理托管 |
| API | `python3 backend/manage.py runserver 0.0.0.0:8000` | 数据库、`MEDIA_ROOT` | 生产环境应替换为 WSGI/ASGI 进程管理器 |
| PostgreSQL | Docker Compose | 独立 Docker 数据卷 | 必须执行备份和恢复演练 |
| Redis | Docker Compose | 独立 Docker 数据卷 | 后续 Celery 启用前配置监控和重试策略 |
| LibreOffice | 服务器系统依赖 | 文档预览缓存 | 转换失败不得降级为模拟预览 |

## 9. 质量与风险

当前后端核心测试覆盖认证、项目、文档、实验和附件链路；`pytest backend -q` 的代码基线结果为 25 项通过。前端以 TypeScript 构建校验作为基础门禁。

| 风险 | 当前处理 | 后续措施 |
|---|---|---|
| 办公文档转换失败 | 返回明确错误并保留下载原件 | 接入转换队列、病毒扫描和缓存清理任务 |
| ELN 动态结构查询能力有限 | 正文采用受约束 JSON 字段 | 高频统计字段规范化并建立索引 |
| 开发服务器直接暴露 | 适用于局域网开发 | 生产部署统一接入 HTTPS、反向代理和进程守护 |
| 文档、测试、报告等扩展域未实现 | 在专题规范中明确为规划 | 按领域迁移、契约和验收用例逐步实现 |

## 10. 非功能性要求

| 维度 | 当前设计要求 | 验证方式 |
|---|---|---|
| 数据隔离 | 任一业务查询必须从当前组织范围开始，跨组织对象不得通过编号、UUID 或文件地址访问 | 越权接口测试、代码评审与生产抽查 |
| 一致性 | 项目和实验的关键写操作在数据库事务内完成；更新使用版本号检测并发冲突 | 并发更新测试、`412` 回归验证 |
| 可追溯性 | 业务对象保留创建/更新时间、创建/更新用户，文件保留上传人、原文件名、大小和 MIME 类型 | 数据库字段检查、下载和预览链路验证 |
| 可恢复性 | 数据库和 `MEDIA_ROOT` 必须作为同一恢复单元备份；预览缓存可重新生成 | 定期恢复演练和文件一致性抽检 |
| 可维护性 | 数据库结构只通过 Migration 演进；接口、权限、测试和规范同步变更 | 提交检查与发布清单 |
| 性能边界 | 默认分页为 20 条，最大 100 条；单个文档或附件不超过 25 MB | API 参数校验、上传回归测试 |

## 11. 交付与验收口径

一项功能可交付的最低条件是：接口已完成权限和对象范围校验，前端不使用模拟业务数据兜底，数据库变化已生成 Migration，核心异常路径有自动化测试，且三份正式规范中相应章节已更新。验收以真实浏览器操作和真实数据库/媒体文件结果为准，不能仅以页面静态展示判断完成。

针对本期系统，验收应至少覆盖：登录并恢复会话、超级管理员创建用户、创建并编辑项目、设置唯一当前里程碑、上传和预览常见文档、创建实验、写入 ELN、上传/读取实验附件、完成实验后拒绝继续编辑，以及不同项目成员之间的可见范围差异。

## 12. 规范组成

- [详细设计](detailed-design.md)：已实现的结构、字段、接口、权限和测试映射。
- [开发部署与运维指南](development-operations-guide.md)：环境、测试、发布、备份、协作和文档维护。
- [文档中心](README.md)：三份规范的阅读路径和当前代码基线。
