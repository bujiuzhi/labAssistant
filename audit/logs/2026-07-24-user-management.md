# 用户管理功能开发记录

## 变更范围

- 新增超级管理员专属用户列表、创建、编辑、状态维护、角色分配和密码重置接口。
- 新增 `/system/users` 用户管理页面和顶部导航入口。
- 新增超级管理员路由保护，普通角色访问时返回项目总览。
- 项目用户选择接口继续复用当前组织的有效用户。
- 超级管理员账号设为受保护对象，不能通过业务用户管理接口修改或重置密码。

## 新增资产

- `backend/apps/identity/permissions.py`
- `backend/apps/identity/services.py`
- `backend/tests/test_user_management_api.py`
- `frontend/src/views/system/UserManagementView.vue`

## 变更资产

- `backend/apps/identity/serializers.py`
- `backend/apps/identity/views.py`
- `backend/apps/identity/urls.py`
- `frontend/src/api/users.ts`
- `frontend/src/types/api.ts`
- `frontend/src/stores/session.ts`
- `frontend/src/router/index.ts`
- `frontend/src/layouts/AppShell.vue`
- `contracts/openapi.yaml`
- `docs/04-frontend-technical-specification.md`
- `docs/07-api-and-integration-specification.md`
- `docs/08-security-permission-and-audit.md`

## 数据库影响

未新增数据表或字段，复用 `user_account`、`role`、`user_role` 和既有组织隔离字段。
