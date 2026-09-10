# 发布门禁修复记录

## 范围与决策

- 时间：2026-09-09 12:00:00，Asia/Shanghai。
- 范围：修复隔离验收、V3 升级前置检查、发布可追溯性及实验附件响应边界；不连接服务器、不迁移或修改正式数据、不提交或推送代码。
- 隔离验收改为先断言 HTML 文档被拒绝，再上传经服务端允许的 UTF-8 文本，恢复后校验原始字节。
- `upgrade` 在停写前和停写后、备份和迁移前，以与 V3 相同的分组条件只读检查重复用户名。前一次命中时不停止 API/Web、不生成备份；两次检查之间新增的重复用户名会在停写后被拒绝，且不会进入备份、迁移或新版本启动。
- `build` 拒绝含已修改、暂存或未跟踪文件的 Git 工作树；成功后生成包含 Git SHA 与 API/Web 不可变镜像 ID 的发布清单。启动、升级和回退必须校验同标签清单与本机镜像 ID 一致。
- 实验附件不再信任 multipart MIME，复用文档白名单和文件头校验；过程图片只允许 JPEG/PNG 内联，其他历史或异常类型降级为附件下载并添加安全响应头。

## 验证与恢复

- `bash -n scripts/production.sh`、`node --check scripts/tests/production-acceptance.mjs`、`git diff --check`：通过。
- `node --test scripts/tests/production.test.mjs`：33 项通过，包含脏工作树拒绝、发布清单缺失或镜像不一致拒绝、重复用户名停写前及停写后二次拒绝。
- Java 25 容器 `mvn test`：52 项通过。API/Web 隔离验收镜像构建中再次执行后端测试、前端 18 项测试及生产构建，均通过。
- `node scripts/tests/production-acceptance.mjs --release acceptance-20260909-5 --manifest "$PWD/data/acceptance-manifests/acceptance-20260909-5.json"`：此前已通过 HTTPS 初始化、重复 bootstrap 拒绝、匿名/CSRF、非白名单文档拒绝、上传下载、逻辑备份、空库恢复及恢复后登录读取。该清单是隔离验收夹具，只验证清单消费链路，不是可发布的正式交付物。
- 在“原样复制外部清单”调整后重跑同一隔离验收时，运行在 `source up` 前因 Docker 报告 `all predefined address pools have been fully subnetted` 失败；`data/acceptance-cbcaec4c8a37/summary.json` 已保留。原因是历史隔离验收资源按约定未清理，非应用启动、迁移或发布清单校验失败。本次未删除任何容器、网络、卷或测试数据。
- 未参与实现的独立复核已确认停写后二次用户名检查和外部清单消费均无新的可触发 P0/P1；复核后仅补齐了验收脚本用法注释。
- 真实服务器、实际证书、跨版本迁移和外部备份介质不在本次本地变更范围。
- 发布清单或升级预检失败时不自动删除镜像、备份或数据库数据；改用新发布标签或完成受控账号重命名后重新执行。
