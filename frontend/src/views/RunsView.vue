<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import {
  Delete,
  Download,
  Histogram,
  Refresh,
  Right,
  Search,
} from '@element-plus/icons-vue';
import { api, ApiError } from '@/api/client';
import type { ProjectSummary, RunSummary } from '@/api/types';
import {
  formatDateTime,
  formatDuration,
  triggerLabel,
} from '@/utils/format';
import StatusTag from '@/components/StatusTag.vue';

const router = useRouter();

const runs = ref<RunSummary[]>([]);
const projects = ref<ProjectSummary[]>([]);
const loading = ref(true);
const exportingId = ref<number | null>(null);

const projectFilter = ref<number | null>(null);
const statusFilter = ref('');
const page = ref(0);
const size = 20;
const total = ref(0);

const statusOptions = [
  { label: 'All statuses', value: '' },
  { label: 'Success', value: 'SUCCESS' },
  { label: 'Failed', value: 'FAILED' },
  { label: 'Blocked', value: 'BLOCKED' },
  { label: 'Running', value: 'RUNNING' },
];

const hasFilters = computed(() => Boolean(projectFilter.value || statusFilter.value));

const summary = computed(() => {
  const counts = { total: total.value, success: 0, failed: 0, blocked: 0 };
  for (const run of runs.value) {
    if (run.status === 'SUCCESS') counts.success += 1;
    if (run.status === 'FAILED') counts.failed += 1;
    if (run.status === 'BLOCKED') counts.blocked += 1;
  }
  return counts;
});

async function loadProjects(): Promise<void> {
  try {
    const response = await api.listProjects('', 0, 100);
    projects.value = response.items;
  } catch {
    // The filter is optional; the run list is the primary content.
  }
}

async function load(): Promise<void> {
  loading.value = true;
  try {
    const response = await api.listRuns({
      projectId: projectFilter.value,
      status: statusFilter.value || undefined,
      page: page.value,
      size,
    });
    runs.value = response.items;
    total.value = response.total;
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : 'Could not load runs');
  } finally {
    loading.value = false;
  }
}

watch([projectFilter, statusFilter], () => {
  page.value = 0;
  void load();
});

watch(page, load);

function openRun(run: RunSummary): void {
  router.push({ name: 'run-detail', params: { id: run.id } });
}

function openProject(run: RunSummary): void {
  router.push({ name: 'project-detail', params: { id: run.projectId } });
}

async function exportRun(run: RunSummary, format: 'csv' | 'json'): Promise<void> {
  exportingId.value = run.id;
  try {
    await api.exportRun(run.id, format, `run-${run.id}`);
    ElMessage.success(`Exported run #${run.id} as ${format.toUpperCase()}`);
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : 'Export failed');
  } finally {
    exportingId.value = null;
  }
}

async function removeRun(run: RunSummary): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `Delete run #${run.id}? The extracted rows will be removed permanently.`,
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
    await api.deleteRun(run.id);
    ElMessage.success('Run deleted');
    if (runs.value.length === 1 && page.value > 0) {
      page.value -= 1;
    } else {
      await load();
    }
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : 'Could not delete the run');
  }
}

function clearFilters(): void {
  projectFilter.value = null;
  statusFilter.value = '';
}

onMounted(async () => {
  await Promise.all([load(), loadProjects()]);
});
</script>

<template>
  <div class="page runs-page fade-in">
    <div class="page-header">
      <div>
        <h2 class="page-title">Run history</h2>
        <p class="page-subtitle">
          Every extraction, manual or scheduled. Open a run to inspect rows, export data or
          re-check the target with the anti-bot doctor.
        </p>
      </div>
      <div class="page-actions">
        <el-button :icon="Refresh" circle aria-label="Refresh runs" @click="load" />
      </div>
    </div>

    <div class="filter-bar panel">
      <div class="filter-group">
        <span class="filter-label">Project</span>
        <el-select
          v-model="projectFilter"
          placeholder="All projects"
          clearable
          class="project-select"
          filterable
        >
          <el-option
            v-for="project in projects"
            :key="project.id"
            :label="project.name"
            :value="project.id"
          />
        </el-select>
      </div>

      <div class="filter-group">
        <span class="filter-label">Status</span>
        <el-select v-model="statusFilter" class="status-select">
          <el-option
            v-for="option in statusOptions"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
      </div>

      <el-button v-if="hasFilters" link :icon="Search" @click="clearFilters">
        Clear filters
      </el-button>

      <div class="filter-summary">
        <span><strong>{{ total }}</strong> runs</span>
        <span v-if="runs.length" class="muted">on this page:</span>
        <span v-if="runs.length" class="tiny-pill success">{{ summary.success }} success</span>
        <span v-if="runs.length" class="tiny-pill danger">{{ summary.failed }} failed</span>
        <span v-if="runs.length" class="tiny-pill warning">{{ summary.blocked }} blocked</span>
      </div>
    </div>

    <section class="panel table-panel">
      <el-table
        v-if="runs.length"
        v-loading="loading"
        :data="runs"
        class="runs-table"
        row-key="id"
        @row-click="openRun"
      >
        <el-table-column prop="id" label="#" width="78">
          <template #default="{ row }">
            <span class="run-id mono">#{{ row.id }}</span>
          </template>
        </el-table-column>

        <el-table-column label="Project" min-width="190">
          <template #default="{ row }">
            <button type="button" class="project-link" @click.stop="openProject(row)">
              {{ row.projectName }}
            </button>
          </template>
        </el-table-column>

        <el-table-column label="Status" width="112">
          <template #default="{ row }"><StatusTag :status="row.status" /></template>
        </el-table-column>

        <el-table-column label="Trigger" width="106">
          <template #default="{ row }">
            <span class="muted">{{ triggerLabel(row.triggerType) }}</span>
          </template>
        </el-table-column>

        <el-table-column label="Started" min-width="150">
          <template #default="{ row }">{{ formatDateTime(row.startedAt) }}</template>
        </el-table-column>

        <el-table-column label="Duration" width="104" align="right">
          <template #default="{ row }">
            <span class="mono">{{ formatDuration(row.durationMs) }}</span>
          </template>
        </el-table-column>

        <el-table-column label="Items" width="86" align="right">
          <template #default="{ row }">
            <strong>{{ row.itemCount }}</strong>
          </template>
        </el-table-column>

        <el-table-column label="Note" min-width="170" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.errorMessage" class="error-note">{{ row.errorMessage }}</span>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>

        <el-table-column label="" width="148" align="right">
          <template #default="{ row }">
            <div class="row-actions" @click.stop>
              <el-dropdown trigger="click" @command="(format: string) => exportRun(row, format as 'csv' | 'json')">
                <el-button size="small" :loading="exportingId === row.id">
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
              <el-button
                size="small"
                type="danger"
                plain
                :icon="Delete"
                aria-label="Delete run"
                @click="removeRun(row)"
              />
              <el-icon class="row-arrow"><Right /></el-icon>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div v-else-if="!loading" class="empty-state">
        <div class="empty-icon"><el-icon><Histogram /></el-icon></div>
        <h3>{{ hasFilters ? 'No runs match these filters' : 'No runs yet' }}</h3>
        <p>
          {{
            hasFilters
              ? 'Clear the filters to see every execution, or run a project to create a new one.'
              : 'Open a project and press "Run now" to create your first extraction.'
          }}
        </p>
        <el-button v-if="hasFilters" @click="clearFilters">Clear filters</el-button>
        <el-button v-else type="primary" @click="router.push({ name: 'projects' })">
          Go to projects
        </el-button>
      </div>

      <div v-if="total > size" class="pagination">
        <el-pagination
          :current-page="page + 1"
          :page-size="size"
          :total="total"
          layout="prev, pager, next, total"
          background
          @current-change="(value: number) => (page = value - 1)"
        />
      </div>
    </section>
  </div>
</template>

<style scoped>
.filter-bar {
  display: flex;
  align-items: center;
  gap: 18px;
  flex-wrap: wrap;
  padding: 14px 18px;
  margin-bottom: 16px;
}

.filter-group {
  display: flex;
  align-items: center;
  gap: 9px;
}

.filter-label {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--ss-muted);
}

.project-select {
  width: 210px;
}

.status-select {
  width: 150px;
}

.filter-summary {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 9px;
  font-size: 12px;
  color: var(--ss-muted);
}

.filter-summary strong {
  color: var(--ss-text);
  font-size: 13px;
}

.tiny-pill {
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 10.5px;
  font-weight: 700;
}

.tiny-pill.success {
  background: #ecfdf5;
  color: #047857;
}

.tiny-pill.danger {
  background: #fef2f2;
  color: #b91c1c;
}

.tiny-pill.warning {
  background: #fffbeb;
  color: #b45309;
}

.table-panel {
  overflow: hidden;
}

.runs-table {
  cursor: pointer;
  width: 100%;
}

.run-id {
  font-size: 11.5px;
  color: var(--ss-muted);
}

.project-link {
  border: 0;
  background: transparent;
  padding: 0;
  font: inherit;
  font-weight: 650;
  font-size: 13px;
  color: var(--ss-text);
  cursor: pointer;
  text-align: left;
}

.project-link:hover {
  color: var(--ss-primary-dark);
  text-decoration: underline;
}

.error-note {
  color: #b91c1c;
  font-size: 12px;
}

.row-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 6px;
}

.row-arrow {
  color: #cbd5e1;
}

.pagination {
  display: flex;
  justify-content: center;
  padding: 16px;
}

@media (max-width: 860px) {
  .filter-summary {
    margin-left: 0;
    width: 100%;
  }
}

@media (max-width: 620px) {
  .project-select,
  .status-select {
    width: 100%;
  }

  .filter-group {
    width: 100%;
    flex-wrap: wrap;
  }
}
</style>
