# 材料实验助手

## 项目目标

材料实验助手是面向材料研发团队的独立 Web 系统，围绕“项目管理、实验计划、电子实验记录、检测结果、报告归档”形成可追溯业务闭环。

当前已完成组织内登录、超级管理员用户管理、工作台、项目管理、项目文档与电子实验记录本。
项目详情已接通项目编辑、人员与里程碑维护、归档、操作记录，以及文档分类检索、真实文件上传、
常用格式预览、下载和实验筛选、复制、状态流转、ELN 编辑与附件管理。数据资产和任务管理页签按
最新原型保持“研发中，敬请期待”，不生成虚构业务数据。正式开发以 `docs/` 中批准的规范为依据。

文档预览支持 DOC/DOCX/ODT/RTF、PDF、XLS/XLSX/ODS/CSV、PPT/PPTX/ODP、TXT，
以及 PNG/JPG/JPEG/WebP/GIF/BMP。DOCX、XLS/XLSX、PPTX、PDF 优先使用专用前端组件，
旧格式、大文件或组件解析失败时自动回退到 LibreOffice 转 PDF。

## 目录结构

```text
materials-lab-assistant/
├── README.md                     # 项目入口
├── docs/                         # 正式项目规范
├── contracts/                    # API 与系统间接口协议
├── audit/                        # 原型来源、证据和审计记录
├── backend/                      # Spring Boot、MyBatis API 与 Flyway 迁移
├── frontend/                     # Vue 3 工作台
├── infra/                        # PostgreSQL、Redis、RustFS 开发环境
└── environment.yml              # Conda 开发环境
```

未实现的目录不提前创建，避免形成空模板。

## 文档入口

项目文档索引见 [docs/README.md](docs/README.md)。

正式规范仅保留三份：

- [概要设计](docs/overview-design.md)
- [详细设计](docs/detailed-design.md)
- [开发部署与运维指南](docs/development-operations-guide.md)

接口协议草案见 [contracts/openapi.yaml](contracts/openapi.yaml)。

## 数据来源

需求和设计来源保存在 `audit/sources/`：

- `audit/sources/requirements/materials-lab-function-list-v1.2.xlsx`
- `audit/sources/design/materials-lab-ui-spec-v1.2.md`

原型截图证据保存在 `audit/evidence/prototype/`。来源文件只用于追溯，不作为实现规范；存在冲突时以 `docs/` 中已批准文档为准。

## 启动方式

远程开发服务器目录为 `/home/bujiu/work/code/materials-lab-assistant`。首次启动：

```bash
cd ~/work/code/materials-lab-assistant
cp .env.example .env
conda env create -f environment.yml
docker compose --env-file .env -f infra/docker-compose.yml up -d

set -a
source .env
set +a
conda run -n materials-lab-assistant mvn -f backend/pom.xml test
conda run -n materials-lab-assistant mvn -f backend/pom.xml spring-boot:run
conda run -n materials-lab-assistant pnpm --dir frontend install
conda run -n materials-lab-assistant pnpm --dir frontend dev
```

`.env` 中必须为 RustFS 配置独立随机访问密钥，不能沿用模板值。Spring Boot 启动时由
Flyway 执行 `backend/src/main/resources/db/migration/` 中的数据库迁移；切换现有历史
数据库前必须先完成备份和只读验证。

首次连接空的开发测试数据库时，应用会写入可追溯的 `DEV_TEST` 测试组织、项目和实验数据；页面仅通过接口读取这些数据库记录。开发账号如下，密码统一为 `00000000`：

| 用户名 | 姓名 | 角色 |
|---|---|---|
| `admin` | 测试管理员 | 超级管理员 |
| `manager` | 测试项目管理员 | 项目管理员 |
| `researcher` | 测试实验员 | 实验员 |

默认密码只适用于开发环境，生产部署必须通过环境变量覆盖并强制首次登录修改。

开发服务仅由远程服务器运行。若局域网端口未直接放行，在本机建立 SSH 隧道：

```bash
ssh -f -N \
  -o ExitOnForwardFailure=yes \
  -o ServerAliveInterval=15 \
  -o ServerAliveCountMax=6 \
  -L 15173:127.0.0.1:5173 \
  -L 18000:127.0.0.1:8000 \
  bujiu@192.168.0.156
```

浏览器访问 `http://127.0.0.1:15173`，后端健康检查为
`http://127.0.0.1:18000/api/v1/health/live`。若服务器已放行端口，也可直接访问
`http://192.168.0.156:5173`。

## 审计入口

审计资产说明见 [audit/README.md](audit/README.md)，来源文件摘要见
[audit/source-manifest.md](audit/source-manifest.md)，首轮开发记录见
[audit/logs/2026-07-24-development-bootstrap.md](audit/logs/2026-07-24-development-bootstrap.md)。
文档范围、状态和维护规则见 [docs/README.md](docs/README.md)。
