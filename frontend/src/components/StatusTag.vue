<script setup lang="ts">
import { statusLabel, statusTagType } from '@/utils/format';

defineProps<{
  status?: string | null;
  size?: 'large' | 'default' | 'small';
}>();
</script>

<template>
  <el-tag :type="statusTagType(status)" effect="light" round :size="size ?? 'small'">
    {{ statusLabel(status) }}
  </el-tag>
</template>
