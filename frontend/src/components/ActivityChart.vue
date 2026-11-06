<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue';
import * as echarts from 'echarts';

interface ActivityPoint {
  date: string;
  total: number;
  failed: number;
}

const props = defineProps<{ data: ActivityPoint[] }>();
const container = ref<HTMLDivElement | null>(null);
let chart: echarts.ECharts | null = null;

function shortDate(value: string): string {
  const parts = value.split('-');
  return parts.length === 3 ? `${parts[1]}/${parts[2]}` : value;
}

function render(): void {
  if (!chart) {
    return;
  }
  const labels = props.data.map((point) => shortDate(point.date));
  const totals = props.data.map((point) => point.total);
  const failures = props.data.map((point) => point.failed);

  chart.setOption(
    {
      animationDuration: 500,
      grid: { left: 8, right: 12, top: 34, bottom: 4, containLabel: true },
      tooltip: {
        trigger: 'axis',
        backgroundColor: '#0f172a',
        borderWidth: 0,
        textStyle: { color: '#e2e8f0', fontSize: 12 },
        axisPointer: { type: 'shadow' },
      },
      legend: {
        right: 4,
        top: 0,
        itemWidth: 9,
        itemHeight: 9,
        icon: 'circle',
        textStyle: { color: '#64748b', fontSize: 12 },
        data: ['Runs', 'Failed / blocked'],
      },
      xAxis: {
        type: 'category',
        data: labels,
        axisLine: { lineStyle: { color: '#e6e8f0' } },
        axisTick: { show: false },
        axisLabel: { color: '#94a3b8', fontSize: 11 },
      },
      yAxis: {
        type: 'value',
        minInterval: 1,
        axisLabel: { color: '#94a3b8', fontSize: 11 },
        splitLine: { lineStyle: { color: '#eef1f6' } },
      },
      series: [
        {
          name: 'Runs',
          type: 'bar',
          data: totals,
          barMaxWidth: 18,
          itemStyle: {
            borderRadius: [5, 5, 0, 0],
            color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
              { offset: 0, color: '#818cf8' },
              { offset: 1, color: '#6366f1' },
            ]),
          },
        },
        {
          name: 'Failed / blocked',
          type: 'bar',
          data: failures,
          barMaxWidth: 18,
          itemStyle: {
            borderRadius: [5, 5, 0, 0],
            color: '#fda4af',
          },
        },
      ],
    },
    true
  );
}

function resize(): void {
  chart?.resize();
}

onMounted(() => {
  if (container.value) {
    chart = echarts.init(container.value);
    render();
  }
  window.addEventListener('resize', resize);
});

watch(() => props.data, render, { deep: true });

onBeforeUnmount(() => {
  window.removeEventListener('resize', resize);
  chart?.dispose();
  chart = null;
});
</script>

<template>
  <div ref="container" class="chart" />
</template>

<style scoped>
.chart {
  width: 100%;
  height: 260px;
}
</style>
