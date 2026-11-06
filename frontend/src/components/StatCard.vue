<script setup lang="ts">
import type { Component } from 'vue';

defineProps<{
  label: string;
  value: string | number;
  icon: Component;
  tone?: 'indigo' | 'cyan' | 'green' | 'amber';
}>();
</script>

<template>
  <div class="stat-card">
    <div class="stat-top">
      <div>
        <div class="stat-label">{{ label }}</div>
        <div class="stat-value">{{ value }}</div>
      </div>
      <div class="stat-icon" :class="tone ?? 'indigo'">
        <el-icon><component :is="icon" /></el-icon>
      </div>
    </div>
    <div class="stat-foot">
      <slot />
    </div>
  </div>
</template>

<style scoped>
.stat-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}
</style>
