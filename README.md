# 材料实验助手

## 项目目标

材料实验助手是面向材料研发团队的独立 Web 系统，围绕“项目管理、实验计划、电子实验记录、检测结果、报告归档”形成可追溯业务闭环。

当前已完成组织内登录、超级管理员用户管理、工作台、项目管理、项目文档与电子实验记录本纵向切片。
项目详情已接通文档分类、检索、筛选、上传、预览、下载，以及项目内实验筛选、详情、计划编辑和
电子实验记录本定位。数据资产页签按当前原型保持“研发中，敬请期待”。正式开发以 `docs/` 中批准的规范为依据。

## 目录结构

```text
materials-lab-assistant/
├── README.md                     # 项目入口
├── docs/                         # 正式项目规范
├── contracts/                    # API 与系统间接口协议
├── audit/                        # 原型来源、证据和审计记录
├── backend/                      # Django API、领域模型与测试
├── frontend/                     # Vue 3 工作台
├── infra/                        # PostgreSQL、Redis 开发环境
└── environment.yml              # Conda 开发环境
```

未实现的目录不提前创建，避免形成空模板。

## 文档入口

项目文档索引见 [docs/README.md](docs/README.md)。

关键规范：

- [产品范围与需求基线](docs/01-product-scope-and-requirements.md)
- [系统总体设计](docs/02-system-architecture.md)
- [UI/UX 设计规范](docs/03-ui-ux-design-specification.md)
- [数据模型与字段字典](docs/06-data-model-and-dictionary.md)
- [API 与集成规范](docs/07-api-and-integration-specification.md)
- [安全、权限与审计规范](docs/08-security-permission-and-audit.md)
- [开发与协作规范](docs/11-development-and-collaboration-specification.md)

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
conda run -n materials-lab-assistant python3 backend/manage.py migrate
conda run -n materials-lab-assistant python3 backend/manage.py bootstrap_development
conda run -n materials-lab-assistant python3 backend/manage.py seed_development_projects
conda run -n materials-lab-assistant python3 backend/manage.py seed_development_experiments
conda run -n materials-lab-assistant python3 backend/manage.py seed_development_documents

conda run -n materials-lab-assistant python3 backend/manage.py runserver 0.0.0.0:8000
conda run -n materials-lab-assistant pnpm --dir frontend install
conda run -n materials-lab-assistant pnpm --dir frontend dev
```

开发账号如下，密码统一为 `00000000`：

| 用户名 | 姓名 | 角色 |
|---|---|---|
| `admin` | 刘李园 | 超级管理员 |
| `manager` | 张伟 | 项目负责人 |
| `researcher` | 李娜 | 研究人员 |
| `inspector` | 王强 | 检测人员 |

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
文档版本、状态和变更记录见 [docs/00-document-control.md](docs/00-document-control.md)。
