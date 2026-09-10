# 公网 HTTP 15105 入口变更记录

## 范围与决策

- 时间：2026-09-10 00:00:00，Asia/Shanghai。
- 范围：按明确要求将首版生产访问方式从域名/TLS 叠加模式收敛为少量固定用户使用的公网 IPv4 HTTP `:15105`；未连接服务器、未开放防火墙、未迁移数据、未部署、未提交或推送。
- 决策：删除生产 TLS Compose 叠加文件与 Nginx 的 direct/upstream 模板；生产 Compose 仅公开 Nginx Web `15105/TCP`，API、PostgreSQL、RustFS 保持无宿主端口和内部网络。
- 生产 Compose 将 `SESSION_COOKIE_SECURE=false`，Nginx 固定转发 `X-Forwarded-Proto: http`，移除 HSTS。生产脚本仅接受公网 IPv4 与 HTTP 绑定地址，默认公开和绑定端口均为 `15105`。

## 风险与恢复

- HTTP 不加密登录口令、会话 Cookie 和上传内容；仅适用于已知少量用户且风险已由需求方接受，不能描述为 TLS 安全的公网服务。
- 防火墙/云安全组仅应开放 `15105/TCP`，并按可用条件限制可信来源；禁止暴露 PostgreSQL、API、RustFS 或 RustFS 控制台。
- 若后续恢复 HTTPS，需重新引入证书管理、TLS 入口与 Secure Cookie，并重新执行浏览器和生产验收；不能只修改端口或 Header。

## 验证

- `bash -n scripts/production.sh`、`node --check scripts/tests/production-acceptance.mjs`、`git diff --check`：通过。
- `node --test scripts/tests/production.test.mjs`：36 项通过，覆盖 HTTP IPv4/端口参数、密钥、发布清单、停写备份、恢复与 Compose 的单一 Web 端口模型。
- 使用非敏感占位参数执行 `docker compose -f infra/compose.production.yml config --quiet`：通过；未启动容器或写入数据。
- 目标服务器、防火墙、真实公网 IP、浏览器登录和完整隔离 Docker 验收不在本次本地变更范围，结果不得视为生产验收。
