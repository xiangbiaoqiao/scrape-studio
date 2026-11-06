<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import {
  CircleCheck,
  Collection,
  Files,
  Histogram,
  MagicStick,
  Refresh,
  Right,
} from '@element-plus/icons-vue';
import { api, ApiError } from '@/api/client';
import type { DashboardSummary, ProjectDetail, RunSummary } from '@/api/types';
import { useAuthStore } from '@/stores/auth';
import {
  formatDateTime,
  formatDuration,
  formatNumber,
  hostOf,
  statusLabel,
  statusTagType,
  timeAgo,
} from '@/utils/format';
import StatCard from '@/components/StatCard.vue';
import ActivityChart from '@/components/ActivityChart.vue';
import StatusTag from '@/components/StatusTag.vue';
import NewProjectDialog from '@/components/NewProjectDialog.vue';

const router = useRouter();
const auth = useAuthStore();

const summary = ref<DashboardSummary | null>(null);
const loading = ref(true);
const dialogVisible = ref(false);

const todayLabel = new Date().toLocaleDateString(undefined, {
  weekday: 'long',
  month: 'long',
  day: 'numeric',
});

const greeting = computed(() => {
  const hour = new Date().getHours();
  if (hour < 12) {
    return 'Good morning';
  }
  if (hour < 18) {
    return 'Good afternoon';
  }
  return 'Good evening';
});

const health = computed(() => {
  const data = summary.value;
  const total = data?.runCount ?? 0;
  return [
    {
      label: 'Successful',
      count: data?.successCount ?? 0,
      percent: total ? Math.round(((data?.successCount ?? 0) / total) * 100) : 0,
      color: '#10b981',
    },
    {
      label: 'Failed',
      count: data?.failedCount ?? 0,
      percent: total ? Math.round(((data?.failedCount ?? 0) / total) * 100) : 0,
      color: '#ef4444',
    },
    {
      label: 'Blocked',
      count: data?.blockedCount ?? 0,
      percent: total ? Math.round(((data?.blockedCount ?? 0) / total) * 100) : 0,
      color: '#f59e0b',
    },
  ];
});

async function load(): Promise<void> {
  loading.value = true;
  try {
    summary.value = await api.dashboard();
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : 'Could not load the dashboard');
  } finally {
    loading.value = false;
  }
}

function openRun(run: RunSummary): void {
  router.push({ name: 'run-detail', params: { id: run.id } });
}

function openProject(id: number): void {
  router.push({ name: 'project-detail', params: { id } });
}

function onProjectCreated(project: ProjectDetail): void {
  router.push({ name: 'project-detail', params: { id: project.id } });
}

onMounted(load);
</script>

<template>
  <div class="page dashboard fade-in" v-loading="loading">
    <div class="page-header">
      <div>
        <div class="greeting-row">
          <h2 class="page-title">{{ greeting }}, {{ auth.displayName }}</h2>
          <span
            class="worker-pill"
            :class="{ online: summary?.workerOnline, offline: summary && !summary.workerOnline }"
          >
            <span class="dot" />
            {{ summary?.workerOnline ? 'Render worker online' : 'Render worker offline' }}
          </span>
        </div>
        <p class="page-subtitle">
          {{ todayLabel }} · Here is how your scraping workspace is doing.
        </p>
      </div>
      <div class="page-actions">
        <el-button :icon="Refresh" circle @click="load" aria-label="Refresh dashboard" />
        <el-button type="primary" @click="dialogVisible = true">
          <el-icon><Files /></el-icon>
          New project
        </el-button>
      </div>
    </div>

    <div class="stat-grid">
      <StatCard label="Projects" :value="summary?.projectCount ?? 0" :icon="Files" tone="indigo">
        <span>{{ summary?.recentProjects.length ?? 0 }} recently active</span>
      </StatCard>
      <StatCard
        label="Total runs"
        :value="formatNumber(summary?.runCount ?? 0)"
        :icon="Histogram"
        tone="cyan"
      >
        <span>{{ summary?.runsToday ?? 0 }} started today</span>
      </StatCard>
      <StatCard
        label="Success rate"
        :value="`${summary?.successRate ?? 0}%`"
        :icon="CircleCheck"
        tone="green"
      >
        <span>{{ summary?.successCount ?? 0 }} successful runs</span>
      </StatCard>
      <StatCard
        label="Items collected"
        :value="formatNumber(summary?.itemsCollected ?? 0)"
        :icon="Collection"
        tone="amber"
      >
        <span>across all saved runs</span>
      </StatCard>
    </div>

    <div class="dash-grid">
      <section class="panel">
        <div class="panel-header">
          <div>
            <div class="panel-title">Run activity</div>
            <div class="panel-subtitle">Last 14 days of extraction jobs</div>
          </div>
          <router-link to="/runs" class="link-button">
            View all <el-icon><Right /></el-icon>
          </router-link>
        </div>
        <div class="chart-wrap">
          <ActivityChart :data="summary?.activity ?? []" />
        </div>
      </section>

      <section class="panel health-panel">
        <div class="panel-header">
          <div>
            <div class="panel-title">Workspace health</div>
            <div class="panel-subtitle">Outcome breakdown across all runs</div>
          </div>
          <el-icon class="health-icon"><MagicStick /></el-icon>
        </div>
        <div class="panel-body">
          <div v-for="row in health" :key="row.label" class="health-row">
            <div class="health-label">
              <span>{{ row.label }}</span>
              <strong>{{ row.count }}</strong>
            </div>
            <el-progress
              :percentage="row.percent"
              :show-text="false"
              :stroke-width="7"
              :color="row.color"
            />
          </div>

          <div class="health-divider" />

          <div class="worker-card">
            <div class="worker-head">
              <span
                class="status-dot"
                :class="{ online: summary?.workerOnline, offline: summary && !summary.workerOnline }"
              />
              <strong>{{ summary?.workerOnline ? 'Worker connected' : 'Worker not reachable' }}</strong>
            </div>
            <p>
              {{
                summary?.workerOnline
                  ? 'Playwright is ready to render pages, pick elements and run previews.'
                  : 'Start the worker with "cd worker && npm run dev" to enable rendering and picking.'
              }}
            </p>
            <router-link to="/doctor" class="link-button">
              Open anti-bot doctor <el-icon><Right /></el-icon>
            </router-link>
          </div>

          <div class="doctor-tip">
            <el-icon><MagicStick /></el-icon>
            <div>
              <strong>Bot wall before a run?</strong>
              <span>Diagnose TLS and challenge headers before wasting a schedule.</span>
            </div>
          </div>
        </div>
      </section>
    </div>

    <div class="bottom-grid">
      <section class="panel">
        <div class="panel-header">
          <div>
            <div class="panel-title">Recent runs</div>
            <div class="panel-subtitle">Click a row to inspect extracted rows</div>
          </div>
          <router-link to="/runs" class="link-button">Run history <el-icon><Right /></el-icon></router-link>
        </div>
        <el-table
          v-if="summary?.recentRuns.length"
          :data="summary.recentRuns"
          class="recent-table"
          @row-click="openRun"
        >
          <el-table-column prop="projectName" label="Project" min-width="150" show-overflow-tooltip />
          <el-table-column label="Status" width="110">
            <template #default="{ row }">
              <StatusTag :status="row.status" />
            </template>
          </el-table-column>
          <el-table-column prop="itemCount" label="Items" width="80" align="right" />
          <el-table-column label="Duration" width="100" align="right">
            <template #default="{ row }">{{ formatDuration(row.durationMs) }}</template>
          </el-table-column>
          <el-table-column label="Started" width="140">
            <template #default="{ row }">
              <span class="muted">{{ formatDateTime(row.startedAt) }}</span>
            </template>
          </el-table-column>
        </el-table>
        <div v-else class="empty-state small">
          <div class="empty-icon"><el-icon><Histogram /></el-icon></div>
          <h3>No runs yet</h3>
          <p>Open a project and press "Run now" to create your first extraction.</p>
        </div>
      </section>

      <section class="panel">
        <div class="panel-header">
          <div>
            <div class="panel-title">Active projects</div>
            <div class="panel-subtitle">Jump back into a builder</div>
          </div>
          <router-link to="/projects" class="link-button">All projects <el-icon><Right /></el-icon></router-link>
        </div>
        <div class="project-list">
          <button
            v-for="project in summary?.recentProjects ?? []"
            :key="project.id"
            type="button"
            class="project-row"
            @click="openProject(project.id)"
          >
            <span class="project-avatar">{{ project.name.slice(0, 1).toUpperCase() }}</span>
            <span class="project-copy">
              <strong>{{ project.name }}</strong>
              <small>{{ hostOf(project.url) }} · {{ project.fieldCount }} fields</small>
            </span>
            <span class="project-meta">
              <StatusTag :status="project.lastStatus" />
              <small class="muted">{{ timeAgo(project.lastRunAt) }}</small>
            </span>
          </button>

          <div v-if="!summary?.recentProjects.length" class="empty-state small">
            <div class="empty-icon"><el-icon><Files /></el-icon></div>
            <h3>Create your first project</h3>
            <p>Add a URL, pick fields on a screenshot, then run it.</p>
            <el-button type="primary" size="small" @click="dialogVisible = true">
              New project
            </el-button>
          </div>
        </div>
      </section>
    </div>

    <NewProjectDialog v-model="dialogVisible" @created="onProjectCreated" />
  </div>
</template>

<style scoped>
.greeting-row {
  display: flex;
  align-items: center;
  gap: 14px;
  flex-wrap: wrap;
}

.worker-pill {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 5px 11px;
  border-radius: 999px;
  border: 1px solid var(--ss-border);
  background: #ffffff;
  color: var(--ss-muted);
  font-size: 11.5px;
  font-weight: 600;
}

.worker-pill .dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #94a3b8;
}

.worker-pill.online {
  border-color: #a7f3d0;
  background: #ecfdf5;
  color: #047857;
}

.worker-pill.online .dot {
  background: #10b981;
  box-shadow: 0 0 0 3px rgba(16, 185, 129, 0.14);
}

.worker-pill.offline {
  border-color: #fde68a;
  background: #fffbeb;
  color: #b45309;
}

.worker-pill.offline .dot {
  background: #f59e0b;
}

.dash-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.62fr) minmax(300px, 1fr);
  gap: 18px;
  margin-top: 18px;
}

.chart-wrap {
  padding: 14px 18px 18px;
}

.link-button {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  border: 0;
  background: transparent;
  color: var(--ss-primary-dark);
  font-size: 12.5px;
  font-weight: 650;
  cursor: pointer;
  padding: 0;
}

.link-button:hover {
  color: var(--ss-primary);
}

.health-icon {
  color: #a5b4fc;
  font-size: 20px;
}

.health-row {
  margin-bottom: 15px;
}

.health-label {
  display: flex;
  justify-content: space-between;
  margin-bottom: 7px;
  font-size: 12.5px;
  color: var(--ss-muted);
}

.health-label strong {
  color: var(--ss-text);
  font-size: 13px;
}

.health-divider {
  height: 1px;
  background: var(--ss-border);
  margin: 18px 0;
}

.worker-card {
  padding: 14px;
  border: 1px solid var(--ss-border);
  border-radius: 12px;
  background: #fbfcfe;
}

.worker-head {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}

.status-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #94a3b8;
}

.status-dot.online {
  background: #10b981;
  box-shadow: 0 0 0 3px rgba(16, 185, 129, 0.14);
}

.status-dot.offline {
  background: #f59e0b;
}

.worker-card p {
  margin: 7px 0 11px;
  font-size: 12px;
  line-height: 1.55;
  color: var(--ss-muted);
}

.doctor-tip {
  display: flex;
  gap: 10px;
  align-items: flex-start;
  margin-top: 14px;
  padding: 12px 13px;
  border-radius: 11px;
  background: #eef2ff;
  color: #4338ca;
}

.doctor-tip .el-icon {
  margin-top: 2px;
  flex-shrink: 0;
}

.doctor-tip strong {
  display: block;
  font-size: 12px;
}

.doctor-tip span {
  display: block;
  margin-top: 2px;
  font-size: 11.5px;
  line-height: 1.5;
  color: #6366f1;
}

.bottom-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.55fr) minmax(300px, 1fr);
  gap: 18px;
  margin-top: 18px;
}

.recent-table {
  cursor: pointer;
}

.project-list {
  padding: 8px 10px 10px;
}

.project-row {
  display: flex;
  align-items: center;
  gap: 11px;
  width: 100%;
  padding: 11px 10px;
  border: 0;
  border-radius: 11px;
  background: transparent;
  text-align: left;
  cursor: pointer;
  transition: background 0.15s ease;
}

.project-row:hover {
  background: #f8faff;
}

.project-avatar {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  display: grid;
  place-items: center;
  background: linear-gradient(135deg, #eef2ff, #e0f2fe);
  color: #4f46e5;
  font-weight: 750;
  font-size: 13px;
  flex-shrink: 0;
}

.project-copy {
  display: flex;
  flex-direction: column;
  min-width: 0;
  flex: 1;
}

.project-copy strong {
  font-size: 13px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.project-copy small {
  color: var(--ss-muted);
  font-size: 11.5px;
  margin-top: 2px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.project-meta {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 4px;
  flex-shrink: 0;
}

.empty-state.small {
  padding: 36px 20px;
}

@media (max-width: 1150px) {
  .dash-grid,
  .bottom-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
