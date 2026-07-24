/**
 * 项目总览原型数据
 *
 * 第一阶段用于稳定复刻原型首屏；后续由同结构的统计 API 替换。
 */

export interface ProjectTypeDistribution {
  name: string;
  projectCount: number;
  experimentCount: number;
  color: string;
}

export interface TrendSeries {
  name: string;
  values: number[];
}

export interface DashboardProject {
  id: string;
  name: string;
  startDate: string;
  endDate: string;
  type: string;
  owner: string;
  objective: string;
  milestoneName: string;
  milestoneDate: string;
  urgency: "normal" | "warning" | "overdue";
  countdownText: string;
}

export const projectTypeDistribution: ProjectTypeDistribution[] = [
  { name: "聚酰亚胺", projectCount: 5, experimentCount: 20, color: "#0b7dea" },
  { name: "环氧树脂", projectCount: 5, experimentCount: 22, color: "#ed8100" },
  { name: "新能源材料", projectCount: 4, experimentCount: 17, color: "#4d9fe9" },
  { name: "绿色化工", projectCount: 3, experimentCount: 14, color: "#009345" },
  { name: "功能材料", projectCount: 3, experimentCount: 19, color: "#885ee8" },
];

export const trendDates = [
  "06/21",
  "06/22",
  "06/23",
  "06/24",
  "06/25",
  "06/26",
  "06/27",
  "06/28",
  "06/29",
  "06/30",
  "07/01",
  "07/02",
  "07/03",
  "07/04",
  "07/05",
  "07/06",
  "07/07",
  "07/08",
  "07/09",
  "07/10",
  "07/11",
  "07/12",
  "07/13",
  "07/14",
  "07/15",
  "07/16",
  "07/17",
  "07/18",
  "07/19",
  "07/20",
];

export const trendSeries: TrendSeries[] = [
  {
    name: "聚酰亚胺",
    values: [2, 3, 3, 4, 4, 5, 4, 5, 6, 5, 6, 7, 6, 5, 6, 7, 7, 6, 7, 8, 7, 8, 7, 6, 7, 8, 8, 7, 8, 9],
  },
  {
    name: "环氧树脂",
    values: [1, 2, 2, 3, 4, 4, 3, 4, 5, 4, 5, 5, 6, 5, 5, 6, 7, 6, 6, 7, 6, 7, 8, 7, 7, 8, 7, 8, 8, 8],
  },
  {
    name: "新能源材料",
    values: [1, 1, 2, 2, 3, 2, 3, 3, 2, 3, 4, 3, 4, 3, 4, 3, 4, 5, 4, 5, 4, 5, 5, 4, 5, 6, 5, 6, 5, 6],
  },
  {
    name: "绿色化工",
    values: [0, 1, 1, 2, 2, 2, 1, 2, 2, 2, 3, 3, 2, 3, 3, 4, 3, 4, 4, 3, 4, 4, 5, 4, 4, 5, 4, 5, 5, 5],
  },
  {
    name: "功能材料",
    values: [1, 1, 1, 2, 1, 2, 2, 2, 3, 2, 2, 3, 3, 2, 3, 3, 4, 3, 3, 4, 3, 4, 4, 3, 4, 4, 5, 4, 5, 4],
  },
];

export const dashboardProjects: DashboardProject[] = [
  {
    id: "PRJ-2026-PLA-001",
    name: "高性能PLA基可降解复合材料开发",
    startDate: "2026-03-01",
    endDate: "2026-12-31",
    type: "功能材料",
    owner: "张伟",
    objective: "开发兼具力学性能与可降解性的PLA基复合材料，完成配方筛选与性能验证。",
    milestoneName: "完成第三轮配方筛选",
    milestoneDate: "2026-07-25",
    urgency: "overdue",
    countdownText: "延期 3 天",
  },
  {
    id: "PRJ-2026-SE-002",
    name: "固态电解质材料离子电导率优化研究",
    startDate: "2026-04-01",
    endDate: "2026-11-30",
    type: "新能源材料",
    owner: "李娜",
    objective: "优化固态电解质配方与制备工艺，提升材料离子传导稳定性。",
    milestoneName: "完成固态电解质配方初筛",
    milestoneDate: "2026-07-30",
    urgency: "normal",
    countdownText: "倒计时 9 天",
  },
  {
    id: "PRJ-2026-CO2-003",
    name: "CO₂加氢制备高附加值化学品工艺开发",
    startDate: "2026-02-15",
    endDate: "2026-10-31",
    type: "绿色化工",
    owner: "王强",
    objective: "构建CO₂加氢催化与工艺体系，完成催化剂活性和稳定性评价。",
    milestoneName: "完成催化剂活性评价",
    milestoneDate: "2026-07-22",
    urgency: "warning",
    countdownText: "倒计时 1 天",
  },
  {
    id: "PRJ-2026-BIO-004",
    name: "生物基环氧树脂合成与性能研究",
    startDate: "2026-03-20",
    endDate: "2026-12-15",
    type: "环氧树脂",
    owner: "陈思",
    objective: "开发生物基环氧树脂合成与固化工艺，验证材料综合性能。",
    milestoneName: "完成力学性能测试",
    milestoneDate: "2026-08-05",
    urgency: "normal",
    countdownText: "倒计时 15 天",
  },
  {
    id: "PRJ-2026-PI-005",
    name: "耐高温聚酰亚胺薄膜制备工艺研究",
    startDate: "2026-02-10",
    endDate: "2026-11-20",
    type: "聚酰亚胺",
    owner: "李娜",
    objective: "开发长期耐温 300℃ 以上的聚酰亚胺薄膜制备工艺，完成连续制膜验证。",
    milestoneName: "完成工艺窗口验证",
    milestoneDate: "2026-07-20",
    urgency: "overdue",
    countdownText: "延期 1 天",
  },
  {
    id: "PRJ-2026-EP-006",
    name: "低介电环氧树脂电子封装材料开发",
    startDate: "2026-01-18",
    endDate: "2026-10-18",
    type: "环氧树脂",
    owner: "周浩",
    objective: "开发兼顾低介电与高可靠性的环氧封装体系，完成关键配方及工艺验证。",
    milestoneName: "完成介电性能复测",
    milestoneDate: "2026-07-26",
    urgency: "warning",
    countdownText: "倒计时 5 天",
  },
];
