<script setup lang="ts">
import { Icon } from "@iconify/vue";
</script>

<template>
  <section class="data-assets-placeholder" aria-labelledby="dataAssetsTitle">
    <Icon icon="tabler:database-cog" />
    <h2 id="dataAssetsTitle">研发中，敬请期待</h2>
  </section>
</template>

<style scoped>
.data-assets-placeholder {
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

.data-assets-placeholder svg {
  width: 52px;
  height: 52px;
  color: var(--color-faint);
}

.data-assets-placeholder h2 {
  margin: 0;
  color: var(--color-ink-2);
  font-size: 18px;
  font-weight: 600;
}
</style>
