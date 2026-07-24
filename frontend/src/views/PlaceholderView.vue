<script setup lang="ts">
import { Clock } from "@element-plus/icons-vue";
import { computed } from "vue";
import { useRoute } from "vue-router";

const route = useRoute();
const title = computed(() => String(route.meta.title ?? "功能建设中"));
const description = computed(() =>
  String(route.meta.description ?? "该功能将在后续纵向切片中实现。"),
);
</script>

<template>
  <section class="page-shell">
    <header class="page-header">
      <div>
        <h1 class="page-title">{{ title }}</h1>
        <p class="page-description">功能边界已纳入总体设计，当前尚未进入开发批次。</p>
      </div>
    </header>

    <article class="content-panel placeholder-panel">
      <span class="placeholder-icon"><Clock /></span>
      <h2>等待后续迭代</h2>
      <p>{{ description }}</p>
    </article>
  </section>
</template>

<style scoped>
.placeholder-panel {
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 0;
  color: var(--color-text-secondary);
}

.placeholder-icon {
  display: grid;
  width: 54px;
  height: 54px;
  color: var(--color-primary);
  background: var(--color-primary-soft);
  border-radius: 12px;
  place-items: center;
}

.placeholder-icon :deep(svg) {
  width: 25px;
}

h2 {
  margin: 18px 0 5px;
  color: var(--color-text-primary);
  font-size: 18px;
}

p {
  margin: 0;
  font-size: 13px;
}
</style>
