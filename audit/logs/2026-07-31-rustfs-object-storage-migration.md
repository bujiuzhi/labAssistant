# RustFS 对象存储迁移记录

## 变更目标

将项目文档、电子实验记录附件和办公文档预览缓存从 Django 本地文件目录迁移到
RustFS 私有对象存储，同时保持现有数据库主键、对象键、授权下载地址和前端调用方式不变。

## 资产边界

- 部署组件：`infra/docker-compose.yml` 新增 RustFS 和数据目录权限初始化服务。
- 持久化目录：`${MATERIALS_LAB_DATA_ROOT}/rustfs/data` 与
  `${MATERIALS_LAB_DATA_ROOT}/rustfs/logs`。
- 运行配置：`.env` 中的 `OBJECT_STORAGE_*`，密钥不进入 Git。
- 应用适配：Django `STORAGES` 使用 S3 SigV4 和 path-style 访问私有桶。
- 初始化命令：`bootstrap_object_storage`。
- 原件迁移命令：`migrate_media_to_object_storage`，默认复制并执行 SHA-256 校验。
- 就绪检查：对象存储启用时，`GET /api/v1/health/ready` 同时检查数据库和桶访问。

## 数据迁移原则

1. 仅迁移数据库中 `ProjectDocument.file` 与 `ExperimentAttachment.file` 引用的原件。
2. 保持原对象键，不修改数据库业务记录。
3. 迁移后逐对象比对大小和 SHA-256。
4. 本地 `backend/media` 在完成备份与恢复演练前保留为只读回滚副本，不自动删除。
5. 旧预览缓存属于可派生数据，不作为原件迁移；首次访问时重新转换并写入 RustFS。

## 回滚边界

若 RustFS 验证失败，停止新文件写入，将 `OBJECT_STORAGE_ENABLED` 设为 `false` 并重启 API，
Django 即恢复使用原 `MEDIA_ROOT`。回滚前必须确认本地回滚副本覆盖数据库当前引用，避免将
迁移后新增对象切回不存在的本地文件。

## 验证清单

- RustFS 容器与端口仅绑定服务器回环地址。
- 私有桶可以执行创建、HEAD、上传、下载和删除。
- 历史项目文档与实验附件迁移数量等于数据库唯一对象键数量。
- `--verify-only` 无缺失或摘要不一致。
- PDF/图片原件预览、办公文档转 PDF、附件下载和删除通过。
- API 就绪检查、后端测试、静态检查和前端构建通过。

## 执行结果

- 执行日期：2026-07-31。
- 执行环境：`192.168.0.156:/home/bujiu/work/code/materials-lab-assistant`。
- RustFS 版本：`rustfs/rustfs:1.0.0-beta.11`，S3 API 与管理控制台仅绑定服务器回环地址。
- 创建私有桶 `materials-lab-assistant`，未授权访问返回 HTTP 403。
- 数据库引用的 188 个唯一原件全部上传，二次 `--verify-only` 校验 188 个对象全部通过。
- Django 默认存储完成上传、读取和删除闭环验证。
- 随机抽取历史 DOCX 原件，授权读取返回 37,008 字节并与数据库记录一致；在线预览成功生成
  31,094 字节 PDF，且预览对象已写入 RustFS。
- 随机抽取历史实验附件，授权读取返回 2,518 字节并与数据库记录一致。
- `GET /api/v1/health/ready` 返回数据库与对象存储均为 `ok`。
- 后端全量测试 39 项通过，Ruff 静态检查通过。

本次迁移未删除 `backend/media`，其作为迁移时点的回滚副本保留。后续新增文件只写入
RustFS，若长期保留本地副本，应纳入独立备份策略，不得将其视为持续同步副本。
