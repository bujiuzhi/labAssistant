# API 与集成规范

## 1. 契约原则

接口以 REST/JSON 为主，统一前缀 `/api/v1`。[OpenAPI 文件](../contracts/openapi.yaml)是核心闭环的初始机器可读契约，本文件说明跨接口规则、完整资源清单和业务语义。端点表中尚未在 OpenAPI 展开的接口必须在实现前先补齐契约；两者冲突时停止实现并修正文档。

前后端通过 OpenAPI 生成类型和基础客户端。未经评审不得为页面临时新增语义重复的接口。

## 2. 协议约定

- 请求与响应编码：UTF-8。
- JSON 字段：`snake_case`，与后端和数据库语义保持一致。
- 时间：ISO 8601 含时区，例如 `2026-07-24T09:30:00Z`。
- 日期：`YYYY-MM-DD`。
- UUID：小写标准格式。
- 精确数值：接口使用十进制字符串，例如 `"12.500000"`，避免 JavaScript 浮点误差。
- 布尔值：JSON 原生 `true/false`。
- 空值：未知或不适用时使用 `null`；不以空字符串替代。

## 3. 认证与安全请求头

浏览器通过 HttpOnly Session Cookie 认证。写请求必须带 CSRF 请求头：

```http
X-CSRFToken: <csrf-token>
```

建议请求头：

| 请求头 | 适用场景 | 说明 |
|---|---|---|
| `X-Request-ID` | 所有请求 | 客户端生成 UUID；缺失时由网关生成 |
| `Idempotency-Key` | 创建、提交、生成报告 | 同一用户和路由内唯一 |
| `If-Match` | 更新聚合根 | 资源版本，例如 `"7"` |
| `Accept-Language` | 所有请求 | `zh-CN` 或后续支持语言 |

响应应返回 `X-Request-ID`；可并发编辑资源返回 `ETag`。

## 4. 响应结构

### 4.1 成功

单资源：

```json
{
  "data": {
    "id": "6f4640ca-9b70-4f6e-a0fe-c53b969ee89f",
    "project_no": "PRJ-2026-000001"
  },
  "request_id": "3ebeb497-ce08-448c-9be8-78e75a5584e9"
}
```

分页列表：

```json
{
  "data": [],
  "meta": {
    "page": 1,
    "page_size": 20,
    "total": 0,
    "total_pages": 0
  },
  "request_id": "3ebeb497-ce08-448c-9be8-78e75a5584e9"
}
```

删除或无响应体操作返回 `204 No Content`，不再包装 JSON。

### 4.2 错误

错误采用 `application/problem+json`：

```json
{
  "type": "https://errors.materials-lab.example/conflict/version",
  "title": "记录已被其他用户修改",
  "status": 412,
  "code": "RESOURCE_VERSION_CONFLICT",
  "detail": "请重新加载最新版本后再提交",
  "instance": "/api/v1/eln-records/6f4640ca-9b70-4f6e-a0fe-c53b969ee89f",
  "request_id": "3ebeb497-ce08-448c-9be8-78e75a5584e9",
  "field_errors": {
    "version": ["当前版本为 8"]
  }
}
```

状态码约定：

| 状态码 | 语义 |
|---:|---|
| 400 | 请求格式错误 |
| 401 | 未登录或会话失效 |
| 403 | 已登录但无权限 |
| 404 | 对象不存在或不在可见范围 |
| 409 | 状态冲突、重复业务约束 |
| 412 | `If-Match` 版本不一致 |
| 422 | 字段或业务规则校验失败 |
| 429 | 请求频率超限 |
| 500 | 未预期服务端错误 |
| 503 | 依赖暂时不可用 |

## 5. 查询、分页和排序

- 分页参数：`page`，默认 1；`page_size`，默认 20，最大 100。
- 排序参数：`ordering`，字段前加 `-` 表示降序。
- 常用过滤：`status`、`owner_id`、`project_id`、`updated_after`。
- 模糊查询：`search`，作用字段由接口明确限定。
- 多值过滤使用重复参数，例如 `status=running&status=reviewing`。
- 未知筛选或排序字段返回 400，不静默忽略。

## 6. 核心资源接口

### 6.1 会话与基础资料

| 方法 | 路径 | 权限/说明 |
|---|---|---|
| `GET` | `/auth/csrf` | 获取登录所需 CSRF 令牌并设置 CSRF Cookie |
| `GET` | `/auth/session` | 获取当前会话、用户和权限摘要 |
| `POST` | `/auth/login` | 登录并轮换会话 |
| `POST` | `/auth/logout` | 注销当前会话 |
| `GET` | `/auth/users/options` | 查询当前组织有效用户，供负责人和成员选择 |
| `GET/POST` | `/materials` | 查询/创建材料 |
| `GET/PATCH` | `/materials/{id}` | 查看/更新材料 |
| `GET/POST` | `/materials/{id}/batches` | 查询/创建材料批次 |
| `GET/POST` | `/process-templates` | 查询/创建工艺模板 |
| `POST` | `/process-templates/{id}/publish` | 发布不可变模板版本 |
| `GET/POST` | `/test-standards` | 查询/创建检测标准 |

### 6.2 项目

| 方法 | 路径 | 权限/说明 |
|---|---|---|
| `GET/POST` | `/projects` | 查询可见项目/创建项目 |
| `GET/PATCH` | `/projects/{project_key}` | 按 UUID 或业务编号查询详情；更新草稿、待开始、活动、风险或暂停项目 |
| `POST` | `/projects/{id}/activate` | 激活项目 |
| `POST` | `/projects/{id}/complete` | 完成项目 |
| `POST` | `/projects/{id}/archive` | 归档项目 |
| `GET/POST` | `/projects/{id}/members` | 查询/添加成员 |
| `PATCH/DELETE` | `/projects/{id}/members/{member_id}` | 调整角色/移除成员 |
| `GET/POST` | `/projects/{id}/milestones` | 查询/创建里程碑 |
| `POST/DELETE` | `/projects/{id}/follow` | 关注/取消关注 |

### 6.3 实验与 ELN

| 方法 | 路径 | 权限/说明 |
|---|---|---|
| `GET/POST` | `/experiments` | 查询/新建实验 |
| `GET/PATCH` | `/experiments/{id}` | 详情/更新计划 |
| `POST` | `/experiments/{id}/submit` | 提交实验计划 |
| `POST` | `/experiments/{id}/start` | 开始执行 |
| `POST` | `/experiments/{id}/complete` | 完成实验 |
| `POST` | `/experiments/{id}/cancel` | 取消实验 |
| `GET` | `/experiments/{id}/eln-record` | 获取对应电子实验记录 |
| `GET/PATCH` | `/eln-records/{id}` | 读取/保存记录草稿 |
| `PUT` | `/eln-records/{id}/steps/{step_id}` | 幂等保存步骤记录 |
| `POST` | `/eln-records/{id}/submit` | 提交记录并生成快照 |
| `POST` | `/eln-records/{id}/reject` | 退回并要求填写原因 |
| `POST` | `/eln-records/{id}/archive` | 归档并生成最终快照 |
| `GET` | `/eln-records/{id}/revisions` | 查询修订历史 |

### 6.4 检测与报告

| 方法 | 路径 | 权限/说明 |
|---|---|---|
| `GET/POST` | `/test-orders` | 查询/创建检测委托 |
| `GET/PATCH` | `/test-orders/{id}` | 查看/更新草稿 |
| `POST` | `/test-orders/{id}/submit` | 提交委托 |
| `POST` | `/test-orders/{id}/assign` | 指派检测人 |
| `PUT` | `/test-orders/{id}/items/{item_id}/result` | 录入或更新待审核结果 |
| `POST` | `/test-orders/{id}/review` | 审核检测结果 |
| `GET/POST` | `/reports` | 查询报告/发起生成 |
| `GET` | `/reports/{id}` | 查询报告状态和下载资源 |
| `POST` | `/reports/{id}/retry` | 重试失败报告 |

### 6.5 文件与通知

| 方法 | 路径 | 说明 |
|---|---|---|
| `POST` | `/files/uploads` | 初始化上传，返回文件 ID 和限时地址 |
| `POST` | `/files/{id}/complete` | 确认上传并进入校验 |
| `GET` | `/files/{id}` | 获取文件元数据 |
| `POST` | `/files/{id}/download-url` | 获取限时下载地址 |
| `POST` | `/files/{id}/links` | 关联业务对象 |
| `DELETE` | `/files/{id}/links/{link_id}` | 解除关联 |
| `GET` | `/notifications` | 分页查询通知 |
| `POST` | `/notifications/{id}/read` | 标记已读 |
| `GET` | `/notifications/stream` | 通过 SSE 接收轻量事件 |

## 7. 文件上传协议

初始化请求包含文件名、声明 MIME、大小和可选 SHA-256。服务端检查组织配额、扩展名白名单和用户权限后返回：

```json
{
  "data": {
    "file_id": "6f4640ca-9b70-4f6e-a0fe-c53b969ee89f",
    "upload_url": "https://storage.example/limited-signed-url",
    "method": "PUT",
    "headers": {
      "Content-Type": "application/pdf"
    },
    "expires_at": "2026-07-24T10:00:00Z"
  },
  "request_id": "3ebeb497-ce08-448c-9be8-78e75a5584e9"
}
```

上传地址是临时敏感信息，不记录日志。完成接口校验对象实际大小和摘要；文件状态变为 `available` 后才能建立业务关联。

## 8. 异步任务与事件

报告生成、批量导出和文件扫描返回 `202 Accepted`，响应包含任务资源或业务资源，不返回 Celery 内部任务 ID。客户端轮询资源状态，站内实时提示使用 SSE。

SSE 只发送资源 ID、事件类型和更新时间，例如：

```text
event: report.ready
data: {"report_id":"6f4640ca-9b70-4f6e-a0fe-c53b969ee89f","updated_at":"2026-07-24T09:45:00Z"}
```

事件不携带完整实验内容。客户端断线后通过常规查询恢复真实状态。

## 9. 版本与弃用

- 不兼容变更通过新的主版本路径发布。
- 新增可选字段属于兼容变更，但客户端必须忽略未知字段。
- 弃用接口至少保留一个发布周期，并返回 `Deprecation` 和 `Sunset` 响应头。
- OpenAPI 变更纳入代码审查；CI 检查契约语法和破坏性差异。
