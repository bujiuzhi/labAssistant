<script setup lang="ts">
import { computed } from "vue";

import type { ProjectStatus } from "@/types/api";

const props = defineProps<{
  status: ProjectStatus;
}>();

const statusConfig: Record<
  ProjectStatus,
  { label: string; type: "info" | "primary" | "warning" | "success"; effect: "light" | "plain" }
> = {
  draft: { label: "草稿", type: "info", effect: "plain" },
  not_started: { label: "待开始", type: "info", effect: "plain" },
  active: { label: "进行中", type: "primary", effect: "light" },
  at_risk: { label: "有风险", type: "warning", effect: "light" },
  suspended: { label: "已暂停", type: "warning", effect: "light" },
  completed: { label: "已完成", type: "success", effect: "light" },
  archived: { label: "已归档", type: "info", effect: "light" },
};

const config = computed(() => statusConfig[props.status]);
</script>

<template>
  <el-tag :type="config.type" :effect="config.effect" size="small">
    {{ config.label }}
  </el-tag>
</template>
