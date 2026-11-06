<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import {
  ArrowLeft,
  Delete,
  Download,
  FirstAidKit,
  Histogram,
  Loading,
  Refresh,
  Timer,
  VideoPlay,
  View,
} from '@element-plus/icons-vue';
import { api, ApiError } from '@/api/client';
import type { DiagnosisReport, RunDetail } from '@/api/types';
import {
  formatDateTime,
  formatDuration,
  formatNumber,
  statusTagType,
  triggerLabel,
} from '@/utils/format';
import DiagnosisPanel from '@/components/DiagnosisPanel.vue';

const route = useRoute();
const router = useRouter();

const runId = computed(() => Number(route.params.id));
const run = ref<RunDetail | null>(null);
const loading = ref(true);
const diagnosing = ref(false);
const exporting = ref(false);
const search = ref('');
const screenshotVisible = ref(false);

let pollTimer: ReturnType<typeof setTimeout> | undefined;

const columns = computed(() => {
  const rows = run.value?.rows ?? [];
  const keys: string[] = [];
  const seen = new Set<string>();
  for (const row of rows.slice(0, 100)) {
    for (const key of Object.keys(row)) {
      if (!seen.has(key)) {
        seen.add(key);
        keys.push(key);
      }
    }
  }
  return keys;
});

const filteredRows = computed(() => {
  const rows = run.value?.rows ?? [];
  const term = search.value.trim().toLowerCase();
  if (!term) {
    return rows;
  }
  return rows.filter((row) =>
    columns.value.some((column) =>
      formatCell(row[column]).toLowerCase().includes(term)
    )
  );
});

const diagnosis = computed<DiagnosisReport | null>(() => run.value?.diagnosis ?? null);
const isRunning = computed(() => run.value?.status === 'RUNNING');

const meta = computed(() => {
  const value = run.value;
  if (!value) {
    return [];
  }
  return [
    { label: 'Started', value: formatDateTime(value.startedAt) },
    { label: 'Finished', value: formatDateTime(value.finishedAt) },
    { label: 'Duration', value: formatDuration(value.durationMs) },
    { label: 'Trigger', value: triggerLabel(value.triggerType) },
    { label: 'Items', value: formatNumber(value.itemCount) },
    { label: 'Run ID', value: `#${value.id}` },
  ];
});

function formatCell(value: unknown): string {
  if (value === null || value === undefined) {
    return '';
  }
  if (Array.isArray(value)) {
    return value.map((item) => formatCell(item)).join(', ');
  }
  if (typeof value === 'object') {
    return JSON.stringify(value);
  }
  return String(value);
}

function schedulePoll(): void {
  clearPoll();
  if (run.value?.status === 'RUNNING') {
    pollTimer = setTimeout(() => {
      void refresh(true);
    }, 2000);
  }
}

function clearPoll(): void {
  if (pollTimer) {
    clearTimeout(pollTimer);
    pollTimer = undefined;
  }
}

async function refresh(silent = false): Promise<void> {
  if (!silent) {
    loading.value = true;
  }
  try {
    run.value = await api.getRun(runId.value);
    schedulePoll();
  } catch (error) {
    if (!silent) {
      ElMessage.error(error instanceof ApiError ? error.message : 'Could not load this run');
    }
  } finally {
    loading.value = false;
  }
}

async function runDiagnosis(): Promise<void> {
  diagnosing.value = true;
  try {
    const report = await api.diagnoseRun(runId.value);
    if (run.value) {
      run.value.diagnosis = report;
    }
    ElMessage.success('Diagnosis complete');
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : 'Diagnosis failed');
  } finally {
    diagnosing.value = false;
  }
}

async function exportRun(format: 'csv' | 'json'): Promise<void> {
  exporting.value = true;
  try {
    await api.exportRun(runId.value, format, `run-${runId.value}`);
    ElMessage.success(`Exported as ${format.toUpperCase()}`);
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : 'Export failed');
  } finally {
    exporting.value = false;
  }
}

async function removeRun(): Promise<void> {
  try {
    await ElMessageBox.confirm(
      'Delete this run? The extracted rows will be removed permanently.',
      'Delete run',
      {
        confirmButtonText: 'Delete',
        cancelButtonText: 'Cancel',
        type: 'warning',
        confirmButtonClass: 'el-button--danger',
      }
    );
  } catch {
    return;
  }

  try {
    await api.deleteRun(runId.value);
    ElMessage.success('Run deleted');
    router.push({ name: 'runs' });
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : 'Could not delete the run');
  }
}

function openProject(): void {
  if (run.value) {
    router.push({ name: 'project-detail', params: { id: run.value.projectId } });
  }
}

function goBack(): void {
  router.push({ name: 'runs' });
}

watch(() => route.params.id, () => {
  void refresh();
});

onMounted(refresh);

onBeforeUnmount(clearPoll);
</script>

<template>
  <div class="page run-detail fade-in" v-loading="loading && !run">
    <div class="back-row">
      <el-button link @click="goBack">
        <el-icon><ArrowLeft /></el-icon>
        Back to run history
      </el-button>
    </div>

    <template v-if="run">
      <section class="run-hero panel">
        <div class="hero-main">
          <div class="status-orb" :class="run.status.toLowerCase()">
            <el-icon v-if="run.status === 'RUNNING'" class="is-loading"><Loading /></el-icon>
            <el-icon v-else><Histogram /></el-icon>
          </div>
          <div class="hero-copy">
            <div class="hero-title-row">
              <h2>Run #{{ run.id }}</h2>
              <el-tag :type="statusTagType(run.status)" effect="light" round size="small">
                {{ run.status.charAt(0) + run.status.slice(1).toLowerCase() }}
              </el-tag>
              <span v-if="run.status === 'RUNNING'" class="live-pill">
                <span class="live-dot" /> live
              </span>
            </div>
            <button type="button" class="project-link" @click="openProject">
              {{ run.projectName }}
            </button>
            <p v-if="isRunning" class="hero-note">
              The worker is collecting rows. This page refreshes automatically every two seconds.
            </p>
          </div>
        </div>

        <div class="hero-actions">
          <el-button :icon="Refresh" :loading="loading" @click="refresh()">Refresh</el-button>
          <el-dropdown
            trigger="click"
            @command="(format: string) => exportRun(format as 'csv' | 'json')"
          >
            <el-button :loading="exporting" :disabled="!run.rows.length">
              <el-icon><Download /></el-icon>
              Export
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="csv">Download CSV</el-dropdown-item>
                <el-dropdown-item command="json">Download JSON</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <el-button type="danger" plain :icon="Delete" @click="removeRun">Delete</el-button>
        </div>
      </section>

      <div class="meta-strip">
        <div v-for="item in meta" :key="item.label" class="meta-item">
          <span class="meta-label">{{ item.label }}</span>
          <strong class="meta-value">{{ item.value }}</strong>
        </div>
        <div v-if="run.screenshotUrl" class="meta-item meta-shot">
          <button type="button" class="shot-preview" @click="screenshotVisible = true">
            <img :src="run.screenshotUrl" alt="Run screenshot" />
            <span class="shot-overlay"><el-icon><View /></el-icon> View screenshot</span>
          </button>
        </div>
      </div>

      <el-alert
        v-if="run.errorMessage"
        :title="run.status === 'BLOCKED' ? 'The target challenged this run' : 'Run failed'"
        :description="run.errorMessage"
        type="error"
        :closable="false"
        show-icon
        class="error-alert"
      />

      <section class="panel results-panel">
        <div class="panel-header">
          <div>
            <div class="panel-title">Extracted rows</div>
            <div class="panel-subtitle">
              {{ run.rows.length }} rows · {{ columns.length }} columns
              <span v-if="run.rows.length">· showing {{ filteredRows.length }}</span>
            </div>
          </div>
          <div class="results-actions">
            <el-input
              v-model="search"
              placeholder="Search rows…"
              clearable
              size="small"
              class="row-search"
            />
          </div>
        </div>

        <div v-if="run.rows.length" class="results-body">
          <el-table :data="filteredRows" max-height="560" class="data-table">
            <el-table-column type="index" label="#" width="58" align="center" />
            <el-table-column
              v-for="column in columns"
              :key="column"
              :prop="column"
              :label="column"
              min-width="180"
              show-overflow-tooltip
            >
              <template #default="{ row }">{{ formatCell(row[column]) }}</template>
            </el-table-column>
          </el-table>
        </div>

        <div v-else-if="isRunning" class="empty-state">
          <div class="empty-icon"><el-icon class="is-loading"><Loading /></el-icon></div>
          <h3>Collecting rows…</h3>
          <p>The worker is rendering the page and evaluating selectors. This usually takes a few seconds.</p>
        </div>

        <div v-else class="empty-state">
          <div class="empty-icon"><el-icon><Histogram /></el-icon></div>
          <h3>No rows were extracted</h3>
          <p>
            The selector may not have matched the page, or the run was blocked. Open the builder to
            preview selectors, then run again.
          </p>
          <el-button @click="openProject">Open project builder</el-button>
        </div>
      </section>

      <section class="panel doctor-panel">
        <div class="panel-header">
          <div>
            <div class="panel-title">Anti-bot diagnosis</div>
            <div class="panel-subtitle">
              Stored with this run when it was blocked, or re-check the target now
            </div>
          </div>
          <el-button
            type="primary"
            plain
            :icon="FirstAidKit"
            :loading="diagnosing"
            @click="runDiagnosis"
          >
            {{ diagnosis ? 'Run again' : 'Diagnose target' }}
          </el-button>
        </div>
        <div class="doctor-body">
          <DiagnosisPanel
            :report="diagnosis"
            :loading="diagnosing"
            empty-text="No diagnosis is attached to this run. Run the doctor to fetch the target with browser-like headers and inspect TLS, HTTP/2, CDN headers, cookies and challenge markers."
          >
            <template #action>
              <el-button v-if="!diagnosis" type="primary" :loading="diagnosing" @click="runDiagnosis">
                Diagnose {{ run.projectName }}
              </el-button>
            </template>
          </DiagnosisPanel>
        </div>
      </section>

      <el-dialog v-model="screenshotVisible" title="Run screenshot" width="min(1120px, 92vw)">
        <img v-if="run.screenshotUrl" :src="run.screenshotUrl" alt="Run screenshot" class="full-shot" />
      </el-dialog>
    </template>

    <div v-else-if="!loading" class="panel empty-state">
      <div class="empty-icon"><el-icon><VideoPlay /></el-icon></div>
      <h3>Run not found</h3>
      <p>It may have been deleted, or the link is incorrect.</p>
      <el-button type="primary" @click="goBack">Back to run history</el-button>
    </div>
  </div>
</template>

<style scoped>
.back-row {
  margin-bottom: 14px;
}

.run-hero {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
  padding: 22px 24px;
}

.hero-main {
  display: flex;
  gap: 15px;
  min-width: 0;
}

.status-orb {
  width: 52px;
  height: 52px;
  border-radius: 14px;
  display: grid;
  place-items: center;
  font-size: 24px;
  flex-shrink: 0;
  background: #f1f5f9;
  color: #475569;
}

.status-orb.success {
  background: #ecfdf5;
  color: #059669;
}

.status-orb.failed {
  background: #fef2f2;
  color: #dc2626;
}

.status-orb.blocked {
  background: #fffbeb;
  color: #d97706;
}

.status-orb.running {
  background: #eef2ff;
  color: #4f46e5;
}

.hero-title-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.hero-title-row h2 {
  margin: 0;
  font-size: 23px;
  letter-spacing: -0.03em;
}

.live-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 3px 9px;
  border-radius: 999px;
  background: #ecfdf5;
  color: #047857;
  font-size: 10.5px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.06em;
}

.live-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #10b981;
  animation: pulse 1.2s ease-in-out infinite;
}

@keyframes pulse {
  0%,
  100% {
    opacity: 1;
  }
  50% {
    opacity: 0.35;
  }
}

.project-link {
  display: inline-block;
  margin-top: 5px;
  border: 0;
  background: transparent;
  padding: 0;
  font: inherit;
  font-size: 13px;
  font-weight: 600;
  color: var(--ss-primary-dark);
  cursor: pointer;
}

.project-link:hover {
  text-decoration: underline;
}

.hero-note {
  margin: 8px 0 0;
  color: var(--ss-muted);
  font-size: 12.5px;
}

.hero-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  flex-shrink: 0;
}

.meta-strip {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 12px;
  margin: 14px 0;
}

.meta-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 12px 15px;
  border: 1px solid var(--ss-border);
  border-radius: 12px;
  background: #ffffff;
  min-height: 60px;
}

.meta-label {
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.07em;
  text-transform: uppercase;
  color: var(--ss-muted);
}

.meta-value {
  font-size: 13px;
  letter-spacing: -0.01em;
  overflow-wrap: anywhere;
}

.meta-item.meta-shot {
  grid-column: span 2;
  padding: 8px;
}

.shot-preview {
  position: relative;
  border: 0;
  padding: 0;
  border-radius: 8px;
  overflow: hidden;
  cursor: pointer;
  background: #0f172a;
  height: 72px;
  width: 100%;
}

.shot-preview img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  object-position: top;
  display: block;
  opacity: 0.85;
}

.shot-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  background: rgba(15, 23, 42, 0.45);
  color: #f8fafc;
  font-size: 11.5px;
  font-weight: 650;
}

.shot-preview:hover .shot-overlay {
  background: rgba(15, 23, 42, 0.62);
}

.error-alert {
  margin-bottom: 14px;
}

.results-panel {
  overflow: hidden;
}

.results-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.row-search {
  width: 220px;
}

.results-body {
  padding: 0 4px 4px;
}

.data-table {
  width: 100%;
}

.doctor-panel {
  margin-top: 14px;
  padding-bottom: 14px;
}

.doctor-body {
  padding: 18px;
}

.full-shot {
  width: 100%;
  height: auto;
  display: block;
  border-radius: 10px;
  border: 1px solid var(--ss-border);
}

@media (max-width: 1180px) {
  .meta-strip {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .meta-item.meta-shot {
    grid-column: span 1;
  }
}

@media (max-width: 860px) {
  .run-hero {
    flex-direction: column;
  }

  .hero-actions {
    width: 100%;
  }

  .meta-strip {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .row-search {
    width: 100%;
  }
}

@media (max-width: 560px) {
  .meta-strip {
    grid-template-columns: minmax(0, 1fr);
  }

  .meta-item.meta-shot {
    grid-column: span 1;
  }
}
</style>
