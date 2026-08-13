<script setup lang="ts">
import { BarChart, PieChart } from "echarts/charts";
import {
  GraphicComponent,
  GridComponent,
  LegendComponent,
  TooltipComponent,
} from "echarts/components";
import * as echarts from "echarts/core";
import { CanvasRenderer } from "echarts/renderers";
import type { ECharts, EChartsCoreOption } from "echarts/core";
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import type {
  DashboardProjectOption,
  DashboardTrendSeries,
  DashboardTypeDistributionItem,
} from "@/types/api";

echarts.use([
  PieChart,
  BarChart,
  GraphicComponent,
  GridComponent,
  LegendComponent,
  TooltipComponent,
  CanvasRenderer,
]);

const projectChartElement = ref<HTMLDivElement>();
const experimentChartElement = ref<HTMLDivElement>();
const trendChartElement = ref<HTMLDivElement>();
const props = defineProps<{
  typeDistribution: DashboardTypeDistributionItem[];
  trend: {
    dates: string[];
    series: DashboardTrendSeries[];
    selected_project_id: string;
    project_options: DashboardProjectOption[];
  };
  projectStatistics: Array<{ id: string; name: string; value: number }>;
}>();
const palette = ["#2563eb", "#0f9b8e", "#f59e0b", "#7c3aed", "#e11d48", "#0891b2"];
const typeDistribution = computed(() =>
  props.typeDistribution.map((item, index) => ({ ...item, color: palette[index % palette.length] })),
);
const projectExperimentTotal = computed(() =>
  props.projectStatistics.reduce((sum, item) => sum + item.value, 0),
);

let projectChart: ECharts | undefined;
let experimentChart: ECharts | undefined;
let trendChart: ECharts | undefined;
let resizeObserver: ResizeObserver | undefined;

function createDonutOption(mode: "project" | "experiment"): EChartsCoreOption {
  const isProject = mode === "project";
  const values = typeDistribution.value.map((item) => ({
    name: item.name,
    value: isProject ? item.project_count : item.experiment_count,
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
  return {
    color: ["#087cf0"],
    animationDuration: 420,
    grid: { left: 300, right: 42, top: 8, bottom: 8 },
    tooltip: {
      trigger: "axis",
      axisPointer: { type: "shadow" },
      backgroundColor: "#ffffff",
      borderColor: "#e2e8f0",
      textStyle: { color: "#334155", fontSize: 12 },
    },
    xAxis: {
      type: "value",
      show: false,
    },
    yAxis: {
      type: "category",
      inverse: true,
      data: props.projectStatistics.map((item) => item.name),
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { color: "#344054", fontSize: 11, width: 278, overflow: "truncate", align: "left", margin: 294 },
    },
    series: [{
      type: "bar",
      barWidth: 9,
      showBackground: true,
      backgroundStyle: { color: "#e8eef5", borderRadius: 8 },
      itemStyle: { borderRadius: 8 },
      label: { show: true, position: "right", color: "#667085", fontSize: 11, formatter: "{c} 个" },
      data: props.projectStatistics.map((item) => item.value),
    }],
  };
}

function updateTrend(): void {
  trendChart?.setOption(createTrendOption(), true);
}

watch(
  () => [props.typeDistribution, props.trend, props.projectStatistics] as const,
  () => {
    projectChart?.setOption(createDonutOption("project"), true);
    experimentChart?.setOption(createDonutOption("experiment"), true);
    updateTrend();
  },
  { deep: true },
);

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
          <li v-for="item in typeDistribution" :key="item.name">
            <i :style="{ backgroundColor: item.color }" />
            <span>{{ item.name }}</span>
          </li>
        </ul>
      </div>
    </article>

    <article class="dashboard-panel trend-panel">
      <header class="panel-heading trend-heading">
        <h2>项目实验数统计</h2>
        <small>共 {{ projectExperimentTotal }} 个实验</small>
      </header>
      <div
        ref="trendChartElement"
        class="trend-chart"
        aria-label="按项目统计的实验数量横向条形图"
      />
    </article>
  </section>
</template>

<style scoped>
.dashboard-grid {
  display: grid;
  grid-template-columns: minmax(500px, 0.9fr) minmax(620px, 1.1fr);
  gap: 10px;
}

.dashboard-panel {
  min-width: 0;
  overflow: hidden;
  background: var(--color-paper);
  border: 1px solid var(--color-rule);
  border-radius: 7px;
  box-shadow: var(--shadow-whisper);
}

.panel-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 38px;
  padding: 7px 10px;
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
  height: 182px;
  padding: 0 10px 6px;
}

.pie-comparison {
  display: grid;
  height: 150px;
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
  height: 150px;
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

.trend-chart {
  width: 100%;
  height: 182px;
}

@media (max-width: 1180px) {
  .dashboard-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 700px) {
  .type-body {
    height: 226px;
    padding-inline: 4px;
  }

  .pie-comparison {
    height: 184px;
  }

  .donut-chart {
    height: 184px;
  }

  .type-legend {
    flex-wrap: wrap;
  }

  .trend-chart {
    height: 220px;
  }
}
</style>
