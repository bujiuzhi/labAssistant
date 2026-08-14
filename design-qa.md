# 实验助手总览截图还原 QA

- source visual truth path: `/var/folders/sf/1tkfh1yd5s3g8yrxth55rwd40000gn/T/codex-clipboard-125fc6e5-efa1-4b3d-8ae1-6a212d637bdf.png`
- implementation screenshot path: `/tmp/lab-assistant-image-target-final.png`
- source pixels: `4744 × 2442`
- implementation pixels: `2372 × 1221`
- CSS viewport: `2372 × 1221`
- density normalization: 源图按 2 倍像素密度理解，与实现的 CSS 尺寸等比例比较
- state: 已登录超级管理员，总览真实数据加载完成

## 全视图比较

实现已对齐参考图的无品牌横向导航、浅灰工作区、两组指标条、左侧双环形图、右侧项目实验数横条，以及六列高密度项目卡片。业务数字、类型数量和项目名称来自当前数据库，因此与参考图示例值存在预期差异。

## 聚焦区域比较

- 顶部与指标区：高度、分栏、蓝色主指标、红色风险和绿色在线状态一致。
- 图表区：双环形图结构和项目实验数横向进度条一致；图例数量随真实项目类型变化。
- 项目卡片区：六列布局、字段顺序、延期徽标、星标和里程碑结构一致。
- 字体：沿用项目现有中文系统字体，字号、字重和行高已压缩到参考图密度。
- 色彩：使用参考图的 `#087cf0` 主蓝、浅灰页面、白色面板与风险红色。
- 图片与图标：参考图无照片或插画；界面图标复用项目现有 Iconify 与 SVG 资源，ECharts 负责图形渲染。
- 文案：导航和区块标题对齐；“已归档”保留当前后端指标语义，未改成参考图的“已完成”。

## 比较历史

1. P1：原实现为折线趋势图且缺少项目实验数横条。已改用项目接口真实 `experiment_count`，复测通过。
2. P2：所有逾期卡片均显示红色外框。已恢复中性卡片，仅保留延期徽标，复测通过。
3. P2：总览接口仅返回六张卡片，无法形成参考图第二行。已合并项目列表真实数据并限制展示十项，复测通过。
4. P2：宽屏自适应生成九列。已按参考图固定为六列，并为中小屏设置三列、两列和单列断点。

## 交互与运行检查

- 页面身份：`http://127.0.0.1:43128/dashboard`，标题“项目总览 · 实验助手”。
- 项目卡片点击：已进入对应 `/projects/{id}` 详情页并成功返回。
- 窄屏：`760 × 900` 下指标、图表和双列卡片正常，无持久控件遮挡。
- 控制台：无 error 或 warn。
- 构建：`vue-tsc -b && vite build` 通过。

## 剩余 P3

- 参考图数据类型较少，环图只有两种颜色；当前真实数据为五类，属于数据驱动差异。
- Vite 仍提示既有大分包警告，不影响本次界面运行。

final result: passed

---

# 全局顶部栏右侧清理 QA

- source visual truth path: `/var/folders/sf/1tkfh1yd5s3g8yrxth55rwd40000gn/T/codex-clipboard-558df443-2dba-4c1a-b61f-6ccb397e1399.png`
- implementation screenshot: 浏览器验收截图（当前任务会话）
- state: 已登录超级管理员，打开 156 环境项目总览

## 标注项比较

- 顶部栏右侧“问题反馈”入口已删除。
- 顶部栏右侧“在线”状态标签已删除。
- 全局导航、当前路由高亮及登录状态逻辑保持不变。

## 验证

- 远程构建：`vue-tsc -b && vite build` 通过。
- 浏览器：顶部栏“问题反馈”和“在线”节点数量均为 0，四项导航正常显示。

final result: passed

---

# 项目数据详情头部标注 QA

- source visual truth path: `/var/folders/sf/1tkfh1yd5s3g8yrxth55rwd40000gn/T/codex-clipboard-12cfc0bf-7d05-4a50-8e90-d4b516b97963.png`
- implementation screenshot path: `/tmp/project-detail-header-final.png`
- CSS viewport: `1280 × 720`
- state: 已登录超级管理员，打开“聚酰亚胺气凝胶隔热材料开发”真实项目

## 标注项比较

- 顶部项目面包屑已删除。
- 标题下方仅保留项目编号与状态标签。
- 待开始、进行中、有风险、已归档分别使用黄色、蓝色、红色和灰色背景语义。
- 右上“编辑项目”已删除，基础信息卡片内的编辑入口保留。

## 验证

- 远程构建：`vue-tsc -b && vite build` 通过。
- 浏览器：面包屑数量为 0，“编辑项目”按钮数量为 0，头部仅显示项目编号和状态。
- 控制台：无 error 或 warn。

final result: passed

---

# 电子实验记录本操作入口标注 QA

- source visual truth paths:
  - `/var/folders/sf/1tkfh1yd5s3g8yrxth55rwd40000gn/T/codex-clipboard-961a91ad-a255-4d17-ae08-b92551262c76.png`
  - `/var/folders/sf/1tkfh1yd5s3g8yrxth55rwd40000gn/T/codex-clipboard-c7546cf0-ea3a-49a3-8856-a35f07b88202.png`
- implementation screenshot path: `/tmp/eln-annotation-final.png`
- CSS viewport: `1280 × 720`
- state: 已登录超级管理员，加载 156 环境真实实验数据

## 标注项比较

- 原料配方表入口已改为无背景的“新增”两字，并紧邻标题。
- 实验过程“新增”已移至标题左侧区域，不再靠右。
- “上传图片”和“上传附件”均保持左对齐并移除按钮背景及边框。
- 右下操作区已移除白色悬浮底板、边框、圆角和阴影，保留原按钮及业务行为。

## 验证

- 远程构建：`vue-tsc -b && vite build` 通过。
- 浏览器：标注区域截图复核通过，无控制台 error 或 warn。
- 数据与上传、保存、完成实验逻辑未改动。

final result: passed

---

# 电子实验记录本截图还原 QA

- source visual truth path: `/var/folders/sf/1tkfh1yd5s3g8yrxth55rwd40000gn/T/codex-clipboard-1201ce2b-8c46-4ad6-bb80-69493833ab53.png`
- implementation screenshot path: `/tmp/eln-final.png`
- CSS viewport: `1280 × 720`
- state: 已登录超级管理员，加载 156 环境真实实验数据

## 全视图比较

实现已对齐参考图的固定左侧计划列表、紧凑实验基础信息、配方表、实验过程和右下角固定操作区。左右内容仍可通过滚轮或触控滚动，但所有可见滚动条均已隐藏。

## 聚焦区域比较

- 左栏：双操作按钮、项目筛选、三状态标签和高密度实验条目结构一致。
- 右栏：白色分区、浅灰工作区、紧凑表格输入框和底部操作按钮一致。
- 数据：项目、实验、配方和记录均使用当前接口真实返回值，未写入模拟数据。
- 响应式：桌面视口无横向溢出；内部滚动容器 `scrollbar-width` 均为 `none`。

## 验证

- 远程构建：`vue-tsc -b && vite build` 通过。
- 页面：`http://192.168.0.156:5173/eln`。
- 既有 Vite 大分包警告仍存在，不影响本次页面运行。

final result: passed

---

# 项目里程碑日期醒目度 QA

- source visual truth path: `/var/folders/sf/1tkfh1yd5s3g8yrxth55rwd40000gn/T/codex-clipboard-3c315e9c-6dbc-4030-9f82-9fefb7ccbaad.png`
- state: 已登录超级管理员，打开 156 环境项目详情

## 标注项比较

- 日期由 11px 提升为 14px。
- 日期字重提升为 600，并使用更深的正文色。
- 日期仍保持在对应里程碑时间轴下方居中，未改变卡片和时间轴结构。

## 验证

- 远程构建：`vue-tsc -b && vite build` 通过。
- 浏览器计算样式：`font-size: 14px`、`font-weight: 600`、`line-height: 24px`。
- 页面截图复核通过，无布局溢出。

final result: passed

---

# 项目研发总体目标醒目度 QA

- source visual truth path: `/var/folders/sf/1tkfh1yd5s3g8yrxth55rwd40000gn/T/codex-clipboard-9880152b-23ca-4700-ba71-5a96427d9d26.png`
- state: 已登录超级管理员，打开 156 环境项目详情

## 标注项比较

- “研发总体目标”标题提升为 13px、600 字重和更深颜色。
- 目标正文提升为 15px、550 字重，并使用主正文色。
- 保留原有列表结构、间距和基础信息卡片布局。

## 验证

- 远程构建：`vue-tsc -b && vite build` 通过。
- 浏览器计算样式与目标值一致，页面无溢出。

final result: passed

---

# 项目总览图表与卡片边框清理 QA

- source visual truth path: `/var/folders/sf/1tkfh1yd5s3g8yrxth55rwd40000gn/T/codex-clipboard-43f510cc-a308-47d1-a9b1-d1a030e0433b.png`
- state: 已登录超级管理员，打开 156 环境项目总览

## 标注项比较

- 关键指标下方“项目类型分布”和“项目实验数统计”整行已删除。
- 项目卡片悬停蓝色边框、风险状态彩色边框及底部强调线已删除。
- 卡片保留统一浅灰边框，点击详情、键盘访问和关注操作保持不变。

## 验证

- 远程构建：`vue-tsc -b && vite build` 通过。
- 浏览器：图表标题与组件数量均为 0，项目卡片正常显示 10 条。
- 卡片默认边框为统一浅灰色，伪元素强调线不存在。

final result: passed

---

# 项目卡片里程碑边框与悬停动效 QA

- source visual truth path: `/var/folders/sf/1tkfh1yd5s3g8yrxth55rwd40000gn/T/codex-clipboard-e026646b-e235-4da2-967e-aac50c2c55fc.png`
- state: 已登录超级管理员，打开 156 环境项目总览

## 标注项比较

- 逾期与临期里程碑框不再使用红色或黄色边框，统一为中性边框。
- 风险提示胶囊的红色、黄色语义仍保留，风险信息可继续快速识别。
- 整张项目卡片新增 180ms 上浮与阴影过渡，悬停时上浮 3px，不改变边框颜色。

## 验证

- 远程构建：`vue-tsc -b && vite build` 通过。
- 浏览器：里程碑框边框为统一中性色，卡片过渡属性包含 `transform` 与 `box-shadow`。
- 页面无布局溢出，原有点击和收藏交互保持不变。

final result: passed
