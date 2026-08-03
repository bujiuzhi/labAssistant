<script setup lang="ts">
import { Icon } from "@iconify/vue";
</script>

<template>
  <section class="tasks-placeholder" aria-labelledby="projectTasksTitle">
    <Icon icon="tabler:list-check" />
    <h2 id="projectTasksTitle">研发中，敬请期待</h2>
  </section>
</template>

<style scoped>
.tasks-placeholder {
  display: flex;
  min-height: 420px;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  margin-top: 14px;
  color: var(--color-muted);
  background: var(--color-paper);
  border: 1px solid var(--color-rule);
  border-radius: 8px;
  box-shadow: var(--shadow-whisper);
  gap: 14px;
}

.tasks-placeholder svg {
  width: 52px;
  height: 52px;
  color: var(--color-faint);
}

.tasks-placeholder h2 {
  margin: 0;
  color: var(--color-ink-2);
  font-size: 18px;
  font-weight: 600;
}
</style>
