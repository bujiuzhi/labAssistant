# 2026-09-18 Element Plus 下拉组件统一

记录时间：2026-09-18（Asia/Shanghai）
仓库与分支：`labAssistant` / `dev`
范围：电子实验记录本、项目列表、项目详情里程碑、项目文档页的业务下拉控件

## 决策与影响

- 将上述页面遗留的原生 `select/option` 直接替换为项目既有的 Element Plus `el-select/el-option`，未引入第三方依赖或新的封装组件。
- 保留原有的 `v-model` 字段、选项值、筛选/保存事件和空值语义；负责人选项继续展示“显示名称（用户名）”。
- 下拉面板沿用 Element Plus 默认的 Teleport 行为，避免浏览器/操作系统原生下拉面板覆盖触发框且无法由 CSS 层级稳定控制的问题。
- 移除全局对原生 `select` 的通用样式，局部仅调整 Element Plus 选择框尺寸，不覆盖其内部输入结构。

## 验证

- 搜索确认 `frontend/src` 下 Vue 业务页面不再包含原生 `select/option`。
- 新增静态回归测试，约束涉及页面使用 `el-select` 且不得重新引入原生下拉元素。
- `pnpm --dir frontend test`：26 项通过。
- `pnpm --dir frontend run build`：TypeScript 检查与生产构建通过；保留既有的大体积构建产物提示，未将其视为本次阻断项。
- `git diff --check`：通过。

## 边界与恢复

本次未构建镜像、未重启服务、未改动数据或部署配置。回退仅需在发布前恢复相应前端源码提交；不涉及数据库迁移或持久化数据恢复。
