# 原型来源清单

## 1. 归档信息

- 归档日期：2026-07-24
- 原型根目录：`/Users/bujiuzhi/Downloads/实验助手`
- 归档方式：复制来源文件；来源内容未在本项目中修改
- 摘要算法：SHA-256

## 2. 来源文件

| 归档文件 | 原始路径 | SHA-256 |
|---|---|---|
| `sources/requirements/materials-lab-function-list-v1.2.xlsx` | `doc/2-功能清单v1.2.xlsx` | `8134e0d7149264bc52c377fe67afb7ea51f295ae27dd6e8b023c93f8ccc2446a` |
| `sources/design/materials-lab-ui-spec-v1.2.md` | `设计规范/实验助手-玄鉴桌面端设计规范-v1.2.md` | `730cfb786f75724bb8bbb92a6bdc71cbc1fa6e31424e5f05c44d6880b7d62ddc` |

## 3. 原型截图

截图由本地原型页面在 2026-07-24 采集，用于记录评估时看到的界面状态。

| 归档文件 | 来源页面 | SHA-256 |
|---|---|---|
| `evidence/prototype/materials-lab-project-overview.png` | `项目总览/index.html` | `fc8c38bd8afda7ee6a5d88bbc5d8c589dcca0056aa3738cbab9d2fbe3c90a801` |
| `evidence/prototype/materials-lab-project-list.png` | `项目数据/index.html` | `7ddbcffaf14337d1afabf4ca1c6c629177d3e2d6e80690cd21db615457d654c6` |
| `evidence/prototype/materials-lab-project-detail.png` | `项目数据/概览.html` | `1a00380b132775eaa1fb547a4abb6ba2cd359ef2a96d06c8f4df73aa6e22e2b7` |
| `evidence/prototype/materials-lab-eln-record.png` | `电子实验记录本/index.html` | `7a82cab0cefe2977c78d944596d70eba327a49a74892da4ec8e3aa442d74dcc2` |
| `evidence/prototype/materials-lab-create-experiment.png` | 新建实验交互状态 | `2c6bdeab94cfecd603f4a61d2e9619ad1d2dbf51585dc73d103e3da1407a9b00` |

## 4. 校验方式

```bash
shasum -a 256 \
  audit/sources/requirements/materials-lab-function-list-v1.2.xlsx \
  audit/sources/design/materials-lab-ui-spec-v1.2.md \
  audit/evidence/prototype/*.png
```
