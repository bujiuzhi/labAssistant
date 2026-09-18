# 审计资产与证据说明

_本目录保存来源、历史观察和变更记录；现行规范见 docs。_

---

## 📋 资产职责

| 入口 | 内容 | 适用边界 |
| --- | --- | --- |
| [来源清单](source-manifest.md) | 原始材料路径与 SHA-256 | 保留原始来源及摘要，不以重写文档改变事实 |
| [需求来源](sources/requirements/) | 功能清单 | 用于追溯，不等于全部已实现 |
| [设计来源](sources/design/) | 原始视觉规范 | 参考材料不构成实施授权 |
| [变更记录](logs/) | 决策、影响、验证、恢复要点 | 历史内容按原意保留 |

正式文档入口为 [docs](../docs/README.md)。
历史记录中出现过的技术栈、端口、服务器地址或“通过”，均不能自动作为当前部署依据。

## 🔍 证据使用

源码说明当前实现路径；测试结果说明对应命令在对应版本和环境的结果；
截图说明被捕获界面当时的状态。这三类证据不可相互替代。

查阅证据时核对提交号、时间、环境、账户角色、动作、结果和原始文件是否仍可访问。
无法确认原运行版本或只有摘要时，应标为“历史描述，未重新验证”，不能补写成一次新的成功验收。

## ✍️ 新增审计记录

影响接口、数据、安全、兼容或部署的重要变更在 `logs/` 新增单份记录，至少包含：

- 操作时间及语义，格式 `YYYY-MM-DD HH:mm:ss`，注明 `Asia/Shanghai`
- 仓库、分支、代码基线与本次对象
- 决策、实际影响和不兼容风险
- 验证命令、结果、未执行项及范围外问题
- 备份/恢复引用、回滚或补偿要点

日志记录验证事实，不把未来计划写成完成。凭据、Cookie、令牌、个人信息和业务正文不进入 Git。
版本通过 Git 保留，正式资产不以 `final-v2` 等副本并行维护。

## 🔄 本次文档对齐

[2026-09-07 文档重写记录](logs/2026-09-07-documentation-rewrite.md)
记录本次源码与文档差异、接口契约调整和验证边界。
原始需求与设计来源保留，未将历史验收转换为当前结论。

[2026-09-08 生产 Compose 资产记录](logs/2026-09-08-production-compose-assets.md)
记录独立生产部署资产、初始化防护、就绪门禁、审查修复及隔离验证；不代表已操作正式服务器。

[2026-09-08 OCR 高风险问题修复记录](logs/2026-09-08-ocr-remediation.md)
记录会话、组织隔离、实验增量写入、附件持久化、迁移和验证结果；不代表已执行生产发布。

[2026-09-11 用户与授权模型术语对齐](logs/2026-09-11-access-control-model-documentation.md)
记录“多租户 RBAC + 项目成员数据范围”的正式术语、特权边界和未实施的 ACL/ABAC/ReBAC 演进边界；本次仅变更文档。

[2026-09-11 租户身份边界修复](logs/2026-09-11-tenant-identity-boundary-remediation.md)
记录用户选项跨组织身份信息边界和普通用户角色提升边界的修复与验证；不代表已完成目标服务器验收。

[2026-09-11 标准多租户 RBAC 平台与租户身份分离](logs/2026-09-11-standard-multitenant-rbac-separation.md)
记录平台管理员与组织超级管理员拆分、Flyway V2 边界、部署参数变更和验证结论；不代表已部署到目标服务器。

[2026-09-11 零业务组织首次初始化](logs/2026-09-11-zero-tenant-platform-bootstrap.md)
记录首次生产初始化改为仅平台控制面、由平台开通首个业务组织的边界调整和验证范围；不代表已部署到目标服务器。

[2026-09-11 本机生产身份数据重置](logs/2026-09-11-local-production-identity-reset.md)
记录经确认后保留平台控制面、清除本机生产业务组织与账号数据的备份、范围和验证；仅适用于该本机 Compose 环境。

[2026-09-14 本机生产平台控制面升级](logs/2026-09-14-local-production-platform-bootstrap-upgrade.md)
记录本机从旧发布镜像升级到零业务组织初始化版本的备份、运行版本和健康验证；仅适用于该本机 Compose 环境。

[2026-09-14 本机平台管理员凭据轮换](logs/2026-09-14-local-production-platform-admin-credential-rotation.md)
记录经确认后修改本机平台管理员登录名与部署专属 Secret 的备份、验证和恢复边界；不记录凭据明文。

[2026-09-14 普通用户逻辑删除](logs/2026-09-14-logical-user-deletion.md)
记录普通用户逻辑删除、负责人交接保护、会话失效和匿名化边界；本记录不代表已部署至任何服务器。

[2026-09-14 本机生产普通用户删除升级](logs/2026-09-14-local-production-logical-user-deletion-upgrade.md)
记录本机生产 Compose 升级、恢复组、Flyway V3 与健康验收；仅适用于该本机环境。

[2026-09-14 项目文档列表与电子实验记录本附件修复](logs/2026-09-14-document-list-and-eln-attachment-upload-fix.md)
记录文档列表 RustFS 对象引用映射修复、未保存实验草稿的附件上传边界与构建验证；不代表已完成生产环境验收。

[2026-09-14 本机生产文档与实验附件修复升级](logs/2026-09-14-local-production-document-and-eln-upload-fix-upgrade.md)
记录 `6e726fb` 的本机 Compose 备份、重启与健康验收；业务界面级上传验收仍待有权限用户确认。

[2026-09-14 ELN 缩略图与大结果附件修复](logs/2026-09-14-eln-attachment-preview-and-large-file-fix.md)
记录私有 RustFS 对象标识泄露导致的缩略图失败修复、任意格式 300 MiB 结果附件与流式传输边界；不代表已完成生产环境验收。

[2026-09-14 本机生产 ELN 附件预览与大文件升级](logs/2026-09-14-local-production-eln-attachment-large-file-upgrade.md)
记录 `f8a2161` 的本机 Compose 恢复组、服务健康与 Nginx 上传限制验收；真实业务界面验收仍待用户确认。

[2026-09-14 ELN 首次附件上传自动暂存草稿](logs/2026-09-14-eln-auto-draft-attachment-upload.md)
记录首次上传自动创建 `not_started` 草稿的用户体验调整、最小归属字段与构建验证；不代表已完成生产环境验收。

[2026-09-14 本机生产 ELN 自动草稿附件升级](logs/2026-09-14-local-production-eln-auto-draft-upgrade.md)
记录 `7cfd461` 的本机 Compose 恢复组、重启和健康验证；首次上传的业务界面验收仍待用户确认。

[2026-09-14 主页未结束项目概览卡片](logs/2026-09-14-dashboard-project-overview-cards.md)
记录主页卡片从仅进行中项目改为未结束项目概览、接口字段调整、排序与构建验证；不代表已完成本机业务界面验收。

[2026-09-14 本机生产主页项目概览升级](logs/2026-09-14-local-production-dashboard-project-overview-upgrade.md)
记录 `f61f7da` 的恢复组、Compose 重启和健康检查；登录后的项目卡片视觉验收仍待用户确认。

[2026-09-14 OCR 发布阻断问题修复](logs/2026-09-14-ocr-release-blocker-remediation.md)
记录 V1 迁移兼容性、平台账号边界、预览回退与敏感信息生命周期等修复及构建验证；未执行数据库迁移、生产重启或远程推送。

[2026-09-15 本机首版 V1 清库重建与启动](logs/2026-09-15-local-first-release-bootstrap.md)
记录受控恢复组、已确认的本机数据重置、单 V1 初始化和健康核验；未推送或操作远程服务器。

[2026-09-16 生产运维命令收敛](logs/2026-09-16-production-operations-command-simplification.md)
记录首次部署、升级、重启和卸载的单命令入口及其数据保留边界。

[2026-09-17 OCR 生产运行与对象存储修复](logs/2026-09-17-ocr-production-runtime-and-object-storage-remediation.md)
记录运维互斥、恢复组一致性、安全解包、对象桶引用与上传事务清理的修复及本地构建验证；未操作生产环境。

[2026-09-17 项目文档与本地构建产物整理](logs/2026-09-17-project-documentation-and-artifact-cleanup.md)
记录正式文档入口按开源项目惯例补齐、过时视觉 QA 与重复素材移除，以及本地可再生成产物清理；未删除部署数据或操作生产环境。

[2026-09-17 本机生产 f0c5da4 升级](logs/2026-09-17-local-production-f0c5da4-upgrade.md)
记录本机从 `b6e9f1a` 升级至 `f0c5da4` 的恢复组、镜像切换与健康核验；不代表目标公网服务器验收。

[2026-09-17 本机生产 488eb60 升级](logs/2026-09-17-local-production-488eb60-upgrade.md)
记录本机将项目人员选项展示修复升级至 `488eb60` 的恢复组、镜像切换与健康核验；不代表目标公网服务器验收。

[2026-09-18 Element Plus 下拉组件统一](logs/2026-09-18-element-plus-select-unification.md)
记录业务下拉从浏览器原生控件统一为项目既有 Element Plus 组件的边界、兼容性语义及验证结果；未构建或部署。

[2026-09-18 本机生产 fe385a1 升级](logs/2026-09-18-local-production-fe385a1-upgrade.md)
记录 Element Plus 下拉组件统一版本的本机构建、恢复组、镜像切换和健康核验；不代表目标公网服务器验收。

[2026-09-18 项目成员数据范围修复](logs/2026-09-18-project-member-data-scope-remediation.md)
记录项目成员关系未持久化导致实验员数据范围拒绝的修复、验证和本机数据补正边界。

[2026-09-18 本机生产 0fc1c8c 项目成员修复升级](logs/2026-09-18-local-production-0fc1c8c-member-remediation.md)
记录成员关系修复版本的本机构建、恢复组、最小数据补正和健康核验；不代表目标公网服务器验收。
