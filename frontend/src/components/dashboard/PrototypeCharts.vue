<script setup lang="ts">
import { Icon } from "@iconify/vue";
import { LineChart, PieChart } from "echarts/charts";
import {
  GridComponent,
  LegendComponent,
  TooltipComponent,
} from "echarts/components";
import * as echarts from "echarts/core";
import { CanvasRenderer } from "echarts/renderers";
import type { ECharts, EChartsCoreOption } from "echarts/core";
import { onBeforeUnmount, onMounted, ref } from "vue";

import {
  projectTypeDistribution,
  trendDates,
  trendSeries,
} from "@/data/prototype-dashboard";

echarts.use([
  PieChart,
  LineChart,
  GridComponent,
  LegendComponent,
  TooltipComponent,
  CanvasRenderer,
]);

const projectChartElement = ref<HTMLDivElement>();
const experimentChartElement = ref<HTMLDivElement>();
const trendChartElement = ref<HTMLDivElement>();
const selectedType = ref("all");

let projectChart: ECharts | undefined;
let experimentChart: ECharts | undefined;
let trendChart: ECharts | undefined;
let resizeObserver: ResizeObserver | undefined;

function createDonutOption(mode: "project" | "experiment"): EChartsCoreOption {
  const isProject = mode === "project";
  const values = projectTypeDistribution.map((item) => ({
    name: item.name,
    value: isProject ? item.projectCount : item.experimentCount,
    itemStyle: { color: item.color },
  }));
  const total = values.reduce((sum, item) => sum + item.value, 0);

  return {
    animationDuration: 420,
    tooltip: {
      trigger: "item",
      formatter: ({ name, value, percent }: { name: string; value: number; percent: number }) =>
        `${name}<br/><strong>${value}</strong> ${isProject ? "个项目" : "个实验"} · ${percent}%`,
    },
    graphic: [
      {
        type: "text",
        left: "center",
        top: "39%",
        style: {
          text: String(total),
          fill: "#172033",
          font: '700 18px "PingFang SC", sans-serif',
        },
      },
      {
        type: "text",
        left: "center",
        top: "52%",
        style: {
          text: isProject ? "个项目" : "个实验",
          fill: "#667085",
          font: '12px "PingFang SC", sans-serif',
        },
      },
    ],
    series: [
      {
        type: "pie",
        radius: ["47%", "75%"],
        center: ["50%", "48%"],
        startAngle: 90,
        minAngle: 3,
        avoidLabelOverlap: false,
        itemStyle: {
          borderColor: "#ffffff",
          borderWidth: 4,
          borderRadius: 7,
        },
        labelLine: { show: false },
        label: {
          show: true,
          position: "inside",
          color: "#ffffff",
          fontSize: 12,
          fontWeight: 700,
          formatter: "{c}",
        },
        emphasis: { scaleSize: 4 },
        data: values,
      },
    ],
  };
}

function createTrendOption(): EChartsCoreOption {
  const visibleSeries =
    selectedType.value === "all"
      ? trendSeries
      : trendSeries.filter((item) => item.name === selectedType.value);

  return {
    color: projectTypeDistribution.map((item) => item.color),
    animationDuration: 420,
    grid: { left: 42, right: 18, top: 24, bottom: 54 },
    tooltip: {
      trigger: "axis",
      backgroundColor: "#ffffff",
      borderColor: "#e2e8f0",
      textStyle: { color: "#334155", fontSize: 12 },
    },
    legend: {
      bottom: 4,
      itemWidth: 12,
      itemHeight: 12,
      icon: "circle",
      textStyle: { color: "#667085", fontSize: 12 },
    },
    xAxis: {
      type: "category",
      boundaryGap: false,
      data: trendDates,
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: {
        color: "#667085",
        fontSize: 11,
        interval: 4,
        showMaxLabel: true,
      },
    },
    yAxis: {
      type: "value",
      min: 0,
      max: 10,
      interval: 2,
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { color: "#667085", fontSize: 11 },
      splitLine: {
        lineStyle: { color: "#dce3ec", type: [4, 7] },
      },
    },
    series: visibleSeries.map((item) => ({
      name: item.name,
      type: "line",
      smooth: 0.35,
      showSymbol: false,
      lineStyle: { width: 2 },
      emphasis: { focus: "series" },
      data: item.values,
    })),
  };
}

function updateTrend(): void {
  trendChart?.setOption(createTrendOption(), true);
}

onMounted(() => {
  if (!projectChartElement.value || !experimentChartElement.value || !trendChartElement.value) {
    return;
  }
  projectChart = echarts.init(projectChartElement.value);
  experimentChart = echarts.init(experimentChartElement.value);
  trendChart = echarts.init(trendChartElement.value);
  projectChart.setOption(createDonutOption("project"));
  experimentChart.setOption(createDonutOption("experiment"));
  updateTrend();

  resizeObserver = new ResizeObserver(() => {
    projectChart?.resize();
    experimentChart?.resize();
    trendChart?.resize();
  });
  resizeObserver.observe(projectChartElement.value);
  resizeObserver.observe(experimentChartElement.value);
  resizeObserver.observe(trendChartElement.value);
});

onBeforeUnmount(() => {
  resizeObserver?.disconnect();
  projectChart?.dispose();
  experimentChart?.dispose();
  trendChart?.dispose();
});
</script>

<template>
  <section class="dashboard-grid">
    <article class="dashboard-panel type-panel">
      <header class="panel-heading">
        <h2>项目类型分布</h2>
      </header>
      <div class="type-body">
        <div class="pie-comparison">
          <section class="pie-item">
            <h3>按项目数</h3>
            <div ref="projectChartElement" class="donut-chart" aria-label="按项目数统计的项目类型分布图" />
          </section>
          <section class="pie-item">
            <h3>按实验数</h3>
            <div
              ref="experimentChartElement"
              class="donut-chart"
              aria-label="按实验数统计的项目类型分布图"
            />
          </section>
        </div>
        <ul class="type-legend" aria-label="项目类型图例">
          <li v-for="item in projectTypeDistribution" :key="item.name">
            <i :style="{ backgroundColor: item.color }" />
            <span>{{ item.name }}</span>
          </li>
        </ul>
      </div>
    </article>

    <article class="dashboard-panel trend-panel">
      <header class="panel-heading trend-heading">
        <div>
          <h2>近30天实验趋势</h2>
          <small>实验个数</small>
        </div>
        <label class="trend-select">
          <select v-model="selectedType" aria-label="筛选趋势项目类型" @change="updateTrend">
            <option value="all">全部类型</option>
            <option v-for="item in projectTypeDistribution" :key="item.name" :value="item.name">
              {{ item.name }}
            </option>
          </select>
          <Icon icon="tabler:chevron-down" aria-hidden="true" />
        </label>
      </header>
      <span class="chart-date-range">06/21–07/20</span>
      <div ref="trendChartElement" class="trend-chart" aria-label="近30天实验趋势折线图" />
    </article>
  </section>
</template>

<style scoped>
.dashboard-grid {
  display: grid;
  grid-template-columns: minmax(440px, 0.96fr) minmax(560px, 1.17fr);
  gap: 10px;
}

.dashboard-panel {
  min-width: 0;
  overflow: hidden;
  background: var(--color-paper);
  border: 1px solid var(--color-rule);
  border-radius: 8px;
  box-shadow: var(--shadow-whisper);
}

.panel-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 44px;
  padding: 8px 12px;
  background: var(--color-paper-2);
}

.panel-heading h2 {
  margin: 0;
  color: var(--color-ink);
  font-size: 15px;
  font-weight: 700;
}

.panel-heading small {
  display: block;
  margin-top: 2px;
  color: var(--color-muted);
  font-size: 11px;
}

.type-body {
  height: 210px;
  padding: 0 10px 6px;
}

.pie-comparison {
  display: grid;
  height: 176px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.pie-item {
  position: relative;
  min-width: 0;
}

.pie-item + .pie-item {
  border-left: 1px solid var(--color-rule);
}

.pie-item h3 {
  position: absolute;
  z-index: 1;
  top: 4px;
  width: 100%;
  margin: 0;
  color: var(--color-muted);
  font-size: 12px;
  font-weight: 600;
  text-align: center;
}

.donut-chart {
  width: 100%;
  height: 176px;
}

.type-legend {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 5px 13px;
  padding: 0;
  margin: 0;
  list-style: none;
}

.type-legend li {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: var(--color-muted);
  font-size: 11px;
  white-space: nowrap;
}

.type-legend i {
  width: 8px;
  height: 8px;
  border-radius: 3px;
}

.trend-panel {
  position: relative;
}

.trend-heading {
  min-height: 56px;
}

.trend-select {
  position: relative;
  display: flex;
  align-items: center;
}

.trend-select select {
  width: 132px;
  height: 34px;
  padding: 0 30px 0 11px;
  color: var(--color-ink-2);
  appearance: none;
  background: var(--color-paper);
  border: 1px solid var(--color-rule-2);
  border-radius: 6px;
  outline: none;
}

.trend-select svg {
  position: absolute;
  right: 9px;
  pointer-events: none;
}

.trend-select select:focus-visible {
  border-color: var(--color-focus);
  box-shadow: 0 0 0 2px color-mix(in oklch, var(--color-focus) 18%, transparent);
}

.chart-date-range {
  position: absolute;
  z-index: 2;
  top: 58px;
  right: 13px;
  color: var(--color-faint);
  font-size: 11px;
}

.trend-chart {
  width: 100%;
  height: 198px;
}

@media (max-width: 1180px) {
  .dashboard-grid {
    grid-template-columns: 1fr;
  }
}
</style>
