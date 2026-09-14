# ELN 缩略图与大结果附件修复

- 操作时间：2026-09-14 14:55:25（Asia/Shanghai）
- 仓库与分支：`labAssistant` / `dev`
- 代码基线：`ed013ec` 之后的工作区修复
- 变更对象：电子实验记录本过程图片预览、结果附件格式与大小边界、RustFS 流式传输

## 决策与影响

过程图片列表此前将私有 RustFS 对象标识作为前端 `img` 地址下发。该标识不是浏览器可访问 URL，因而上传成功后缩略图显示失败。本次仅向前端返回经会话授权的附件内容路由；图片仍由 API 完成组织与实验数据范围校验，RustFS 对象标识不再暴露。

结果附件不再沿用项目文档的格式白名单，允许任意扩展名和客户端声明的 MIME；单文件上限调整为 300 MiB。结果附件统一按二进制下载，不作浏览器内联预览。上传和下载均改为流式 RustFS 传输，避免单个 300 MiB 文件整体驻留在 API 内存。过程图片继续仅接受文件头确认的 JPG/PNG，单文件上限维持 10 MiB。

Spring multipart、Nginx 请求体与超时、前端上传超时同步提高；项目文档自身的格式白名单与 20 MiB 业务上限不变。

## 验证

- `pnpm --dir frontend test`：23 项通过。
- `pnpm --dir frontend build`：类型检查与生产构建通过。
- `docker build --progress=plain --target build --file infra/Dockerfile.api .`：后端 Maven `verify` 通过，76 项测试通过。
- 新增回归覆盖：任意格式结果附件的流式对象存储写入、超过 300 MiB 的服务端拒绝、响应中不泄露 RustFS 对象标识。
- `git diff --check`：通过。

未用真实用户会话上传 300 MiB 文件；后续本机生产升级会先生成恢复组。该变更不涉及数据库结构或 Flyway 迁移，可按受控发布脚本回退应用镜像。
