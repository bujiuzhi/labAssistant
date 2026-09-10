# RustFS 生产恢复组整改记录

时间：2026-09-09 16:10:00（Asia/Shanghai）

## 决策与影响

- 首次正式发布的 V1 已包含 RustFS 正文存储语义；未上线的 `V2__rustfs_object_storage.sql` 不再随发布包交付。
- 开发与生产 Compose 均不启动 Redis。PostgreSQL 保存元数据、权限关系、ELN 与历史 BYTEA 兼容正文；RustFS 保存新上传的项目文档与实验附件正文。
- 生产 Compose 固定官方 RustFS `v1.0.0-rc.5` 的多架构摘要，RustFS 只在内部 storage 网络监听；API、一次性 bootstrap 均等待 RustFS `/health/ready`。
- `bootstrap` 创建并验证私有业务桶；API `/api/v1/health/ready` 同时验证 PostgreSQL 和已启用的 RustFS 桶。

## 备份、恢复与验证

- `backup --confirm <项目名>` 在 API/Web 停写后生成 PostgreSQL `.dump`、RustFS `.rustfs-data.tar.gz`、两份 SHA-256 与元数据文件；五个文件共同构成恢复组。
- `restore-new` 仅接受校验值、元数据和首版架构标识均匹配的恢复组，并拒绝非空 RustFS 数据目录；对象数据先解压至隔离暂存目录，数据库恢复校验后才通过同数据根目录移动发布到目标 RustFS 目录。发布失败会保留暂存目录并输出人工恢复路径。
- 已执行脚本语法检查和隔离脚本测试；未连接真实 RustFS、未访问目标服务器、未执行生产部署或真实恢复演练。
