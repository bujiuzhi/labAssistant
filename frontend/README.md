# 材料实验助手前端

_Vue 单页应用；产品与接口边界见正式设计文档。_

---

## 📋 结构与依赖

Vue、TypeScript、Vite、Pinia、Vue Router、Element Plus 和文档预览组件由
[package.json](package.json)与 [pnpm-lock.yaml](pnpm-lock.yaml)管理。
使用清单指定的 pnpm 版本，不复制其他操作系统的依赖目录。

页面见 [src/views](src/views/)，路由见 [src/router](src/router/)，
请求见 [src/api](src/api/)，会话见 [src/stores/session.ts](src/stores/session.ts)。
本目录没有 Electron 主进程。

## 🔧 常用命令

从项目根目录执行：

```bash
pnpm --dir frontend install --frozen-lockfile
pnpm --dir frontend test
pnpm --dir frontend run build
```

`build` 同时执行 TypeScript 检查与静态构建，输出到 `frontend/dist/`。
`test` 使用 Node 原生测试执行器运行 TypeScript 测试，当前包含函数测试和源码静态断言。
构建及这些测试不证明浏览器操作、后端接口或上传已验收。

## 🌐 运行与代理

开发环境优先按[运维指南](../docs/development-operations-guide.md)通过 Compose 启动。
独立启动时使用 `pnpm --dir frontend dev --host 127.0.0.1`，并确保不会与已有前端容器争用端口。
独立 Vite 的监听地址由 CLI/Vite 配置决定，不读取 Compose 的 `FRONTEND_BIND_ADDRESS`。
[Vite 配置](vite.config.ts)中的 `VITE_API_PROXY_TARGET` 控制开发代理目标。

API 基址为同源 `/api/v1`，认证采用 Cookie/CSRF。
生产静态托管需提供 SPA 路由回退和 API 原路径代理；仅构建产物不会包含 Vite 开发代理。
生产镜像由 [Dockerfile.web](../infra/Dockerfile.web)执行测试及构建，再以非 root Nginx 提供服务，
部署步骤见[生产 Compose 指南](../docs/production-deployment.md)。

## 🔍 当前功能边界

路由、预览格式、ELN 覆盖保存语义和缺少的后端接口统一见
[详细设计](../docs/detailed-design.md)。
页面存在按钮、组件或请求封装，不等于当前后端已经实现该能力。
历史视觉报告见[审计索引](../audit/README.md)，不作为当前构建的验收结论。
