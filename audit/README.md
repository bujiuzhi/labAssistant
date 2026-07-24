# 审计资产说明

## 1. 目的

`audit/` 用于保存原型来源、评估证据和后续审计记录，确保正式规范能够追溯到原始材料。该目录不是需求或设计的正式来源。

## 2. 资产清单

文件来源、原始路径和 SHA-256 摘要见 [原型来源清单](source-manifest.md)。

### 原始来源

| 文件 | 来源 | 用途 |
|---|---|---|
| `sources/requirements/materials-lab-function-list-v1.2.xlsx` | 原型目录 `doc/2-功能清单v1.2.xlsx` | 需求追溯 |
| `sources/design/materials-lab-ui-spec-v1.2.md` | 原型目录玄鉴桌面端设计规范 | 视觉基线参考 |

### 原型证据

| 文件 | 页面 |
|---|---|
| `evidence/prototype/materials-lab-project-overview.png` | 项目总览 |
| `evidence/prototype/materials-lab-project-list.png` | 项目列表 |
| `evidence/prototype/materials-lab-project-detail.png` | 项目详情 |
| `evidence/prototype/materials-lab-eln-record.png` | ELN 记录 |
| `evidence/prototype/materials-lab-create-experiment.png` | 新建实验 |

### 开发证据

| 目录 | 用途 |
|---|---|
| `evidence/development/` | 远程运行页面与验收截图 |
| `logs/` | 开发批次、验证结果、数据边界与已知问题记录 |

## 3. 资产边界

- 原始来源只读保存，不直接修订。
- 由来源推导的正式结论写入 `docs/`。
- 后续自动化扫描、测试证据和报告应继续放在 `audit/evidence/`、`audit/logs/`、`audit/reports/`。
- 不在 `audit/` 存放业务代码、生产数据、密钥或用户隐私数据。
