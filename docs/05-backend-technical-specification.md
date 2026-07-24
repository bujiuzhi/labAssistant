# 后端技术规范

## 1. 技术基线

后端采用 Python 3、Django、Django REST Framework、PostgreSQL、Celery 和 Redis，文件存储使用兼容 S3 的对象存储。系统以模块化单体部署，业务边界通过 Django App、服务层和数据模型保持清晰。

项目实现时创建同名 conda 环境 `materials-lab-assistant`，并提交 `environment.yml`。依赖版本必须锁定；升级框架或数据库大版本需单独编写决策记录和迁移验证。

## 2. 目录与职责

```text
backend/
├── config/                  # 环境配置、URL、ASGI、Celery
├── apps/
│   ├── identity/            # 用户、角色、权限、组织
│   ├── projects/            # 项目、成员、里程碑
│   ├── materials/           # 材料、批次、单位
│   ├── processes/           # 工艺块、模板、模板步骤
│   ├── experiments/         # 实验计划、步骤、电子实验记录
│   ├── testing/             # 检测标准、委托、结果
│   ├── reports/             # 报告任务和产物
│   ├── files/               # 文件元数据、上传确认、下载授权
│   ├── notifications/       # 站内通知和事件流
│   └── audit/               # 审计事件
├── common/                  # 共享异常、分页、类型和中间件
├── tests/
└── manage.py
```

每个业务 App 内按 `models.py`、`services.py`、`selectors.py`、`serializers.py`、`views.py`、`permissions.py`、`tasks.py` 和 `tests/` 组织：

- `services`：写操作、事务边界、状态迁移和领域校验。
- `selectors`：只读查询、权限范围过滤和预加载策略。
- `serializers`：接口结构与基础字段校验。
- `permissions`：动作权限和对象级范围判断。
- `tasks`：异步任务入口，不直接承载核心领域规则。

不增加通用 Repository 层包装 Django ORM；只有外部系统或对象存储适配器使用接口抽象。

## 3. 编码规范

- 所有公共函数和方法必须提供类型注解与中文 docstring，说明功能、参数、返回值和可能异常。
- 领域标识符使用完整英文单词，不使用含义不清的缩写。
- 关键写路径记录结构化日志，至少包含请求编号、用户 ID、组织 ID、对象类型、对象 ID 和结果。
- 禁止捕获所有异常后静默返回；预期业务异常转换为标准问题详情，未知异常由全局异常处理器记录。
- 时间统一保存为 UTC，接口采用 ISO 8601 含时区格式。
- 精确数量使用 `Decimal`，不得使用二进制浮点数保存配比和检测数值。
- 面向用户的业务编号由服务端生成，数据库 UUID 不暴露业务含义。

## 4. 请求处理与事务

写操作流程固定为：

1. 校验会话、CSRF、动作权限和对象数据范围。
2. 解析输入并锁定需要并发保护的记录。
3. 在 `transaction.atomic()` 中执行领域校验、数据变更和审计事件写入。
4. 使用事务提交回调投递异步任务。
5. 返回最新资源、版本和请求编号。

禁止在数据库事务内执行对象存储上传、外部 HTTP 请求或长时间报告生成。跨资源修改应建立明确的服务方法，不在 View 中散落保存逻辑。

## 5. 状态机

状态迁移必须由领域服务执行，不能允许客户端直接任意修改 `status`。

### 5.1 项目

```text
draft -> active -> suspended -> active
active -> completed -> archived
draft/active/suspended -> archived
```

### 5.2 实验

```text
draft -> planned -> running -> waiting_test -> reviewing -> completed -> archived
running -> reviewing
draft/planned/running/waiting_test/reviewing -> cancelled
```

是否经过 `waiting_test` 由实验计划的检测需求决定。完成前必须存在已提交的电子实验记录；需要检测时，还必须存在已审核结果。

### 5.3 电子实验记录

```text
draft -> recording -> submitted -> archived
submitted -> rejected -> recording
```

提交和归档生成不可变内容快照。退回不会覆盖历史修订。

### 5.4 检测委托

```text
draft -> submitted -> awaiting_result -> reviewing -> completed
draft/submitted/awaiting_result/reviewing -> cancelled
```

每个迁移方法必须验证来源状态、必填数据、权限和版本，并写入审计日志。

## 6. 并发与幂等

- 可编辑聚合根使用整数 `version` 字段实现乐观锁。
- 更新接口要求 `If-Match` 或请求体版本；不一致返回 412。
- 业务编号由独立序列或带行锁的编号表生成，不使用“查询最大值加一”。
- 创建、提交、报告生成等请求支持 `Idempotency-Key`，结果至少保留 24 小时。
- 异步任务使用业务唯一键防重，重试前检查当前状态。
- 审计事件与业务变更在同一事务写入，异步通知采用事务后投递。

## 7. 查询与分页

- 默认分页 20 条，最大 100 条。
- 列表查询必须先应用组织和对象范围，再应用用户筛选。
- 关联对象使用 `select_related` 或 `prefetch_related`，关键列表通过查询数量测试防止 N+1。
- 模糊搜索限定在业务编号、名称和可索引字段；大规模全文检索在容量达到阈值后再引入专用搜索服务。
- 导出不复用普通列表接口，采用异步任务生成受权限保护的文件。

## 8. 文件与报告

文件服务只保存元数据和对象键，不将二进制写入 PostgreSQL。上传完成后校验实际对象大小、MIME 类型和 SHA-256；扫描完成前文件不能被业务对象引用。

报告生成由 Celery 任务执行：

1. 固化报告输入快照和模板版本。
2. 生成 PDF 或业务要求的格式。
3. 将产物登记为 `file_object`。
4. 更新报告状态并发送站内通知。

任务失败需保存稳定错误码和脱敏摘要，允许有权限用户重试；不得把堆栈直接展示给终端用户。

## 9. 安全与审计

认证、对象权限、敏感字段、文件下载和审计事件要求见[安全、权限与审计规范](08-security-permission-and-audit.md)。后端是权限和状态机的唯一可信执行端。

## 10. 测试与运行

核心服务使用 pytest 和 pytest-django；时间、对象存储和异步任务通过可替换适配器测试。测试数据库必须应用真实迁移，不以 `--no-migrations` 作为持续集成默认配置。

实现阶段最小运行方式：

```bash
conda env create -f environment.yml
conda activate materials-lab-assistant
python3 backend/manage.py migrate
python3 backend/manage.py runserver
```

完整本地依赖和生产部署方式以[部署、运维与路线图](10-deployment-operations-and-roadmap.md)为准。
