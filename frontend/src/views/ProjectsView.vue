<script setup lang="ts">
import { onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import {
  Delete,
  Files,
  Plus,
  Search,
  Timer,
  VideoPlay,
} from '@element-plus/icons-vue';
import { api, ApiError } from '@/api/client';
import type { ProjectDetail, ProjectSummary } from '@/api/types';
import { hostOf, statusLabel, timeAgo } from '@/utils/format';
import StatusTag from '@/components/StatusTag.vue';
import NewProjectDialog from '@/components/NewProjectDialog.vue';

const route = useRoute();
const router = useRouter();

const projects = ref<ProjectSummary[]>([]);
const loading = ref(true);
const query = ref('');
const page = ref(0);
const total = ref(0);
const size = 24;
const dialogVisible = ref(false);
const runningId = ref<number | null>(null);

let searchTimer: ReturnType<typeof setTimeout> | undefined;

async function load(): Promise<void> {
  loading.value = true;
  try {
    const response = await api.listProjects(query.value.trim(), page.value, size);
    projects.value = response.items;
    total.value = response.total;
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : 'Could not load projects');
  } finally {
    loading.value = false;
  }
}

watch(query, () => {
  clearTimeout(searchTimer);
  searchTimer = setTimeout(() => {
    page.value = 0;
    void load();
  }, 300);
});

watch(page, load);

function openProject(project: ProjectSummary): void {
  router.push({ name: 'project-detail', params: { id: project.id } });
}

async function runProject(project: ProjectSummary): Promise<void> {
  runningId.value = project.id;
  try {
    const run = await api.createRun(project.id);
    ElMessage.success(`Run queued for ${project.name}`);
    router.push({ name: 'run-detail', params: { id: run.id } });
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : 'Could not start the run');
  } finally {
    runningId.value = null;
  }
}

async function removeProject(project: ProjectSummary): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `Delete "${project.name}"? Its fields and run history will be removed too.`,
      'Delete project',
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
    await api.deleteProject(project.id);
    ElMessage.success('Project deleted');
    await load();
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : 'Could not delete the project');
  }
}

function onCreated(project: ProjectDetail): void {
  router.push({ name: 'project-detail', params: { id: project.id } });
}

onMounted(() => {
  if (route.query.new === '1') {
    dialogVisible.value = true;
    router.replace({ name: 'projects' });
  }
  void load();
});
</script>

<template>
  <div class="page projects fade-in">
    <div class="page-header">
      <div>
        <h2 class="page-title">Projects</h2>
        <p class="page-subtitle">
          Each project maps one page to a repeatable extraction. Open a builder to pick fields,
          preview rows and generate code.
        </p>
      </div>
      <div class="page-actions">
        <el-input
          v-model="query"
          placeholder="Search projects…"
          :prefix-icon="Search"
          clearable
          class="search-input"
        />
        <el-button type="primary" @click="dialogVisible = true">
          <el-icon><Plus /></el-icon>
          New project
        </el-button>
      </div>
    </div>

    <div v-loading="loading">
      <div v-if="projects.length" class="project-grid">
        <article
          v-for="project in projects"
          :key="project.id"
          class="project-card"
          @click="openProject(project)"
        >
          <div class="card-head">
            <div class="project-avatar">{{ project.name.slice(0, 1).toUpperCase() }}</div>
            <div class="card-title">
              <h3>{{ project.name }}</h3>
              <span class="url-text" :title="project.url">{{ hostOf(project.url) }}</span>
            </div>
            <el-dropdown trigger="click" @click.stop>
              <button class="more-button" type="button" aria-label="Project actions">···</button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item @click="runProject(project)">
                    <el-icon><VideoPlay /></el-icon> Run now
                  </el-dropdown-item>
                  <el-dropdown-item divided @click="removeProject(project)">
                    <el-icon><Delete /></el-icon> Delete
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>

          <p class="card-description">
            {{ project.description || 'No description yet. Add one from the project builder.' }}
          </p>

          <div class="card-stats">
            <div class="stat">
              <strong>{{ project.fieldCount }}</strong>
              <span class="stat-label">fields</span>
            </div>
            <div class="stat">
              <strong>{{ project.runCount }}</strong>
              <span class="stat-label">runs</span>
            </div>
            <div class="stat status-stat">
              <StatusTag :status="project.lastStatus" />
              <span class="stat-label">{{ project.lastStatus ? 'last outcome' : 'ready to run' }}</span>
            </div>
          </div>

          <div class="card-foot">
            <span v-if="project.scheduleEnabled" class="schedule-pill">
              <el-icon><Timer /></el-icon> Every {{ project.intervalMinutes }} min
            </span>
            <span v-else class="muted tiny">Manual runs only</span>
            <span class="muted tiny">Updated {{ timeAgo(project.updatedAt) }}</span>
          </div>

          <div class="card-actions" @click.stop>
            <el-button size="small" @click="openProject(project)">Open builder</el-button>
            <el-button
              size="small"
              type="primary"
              plain
              :loading="runningId === project.id"
              @click="runProject(project)"
            >
              Run now
            </el-button>
          </div>
        </article>
      </div>

      <div v-else-if="!loading" class="panel empty-state">
        <div class="empty-icon"><el-icon><Files /></el-icon></div>
        <h3>{{ query ? 'No projects match that search' : 'Your workspace is empty' }}</h3>
        <p>
          {{
            query
              ? 'Try a different keyword, or clear the search to see every project.'
              : 'Create a project from any URL. ScrapeStudio will render it in Chromium and let you pick fields visually.'
          }}
        </p>
        <el-button v-if="!query" type="primary" @click="dialogVisible = true">
          Create your first project
        </el-button>
      </div>

      <div v-if="total > size" class="pagination">
        <el-pagination
          :current-page="page + 1"
          :page-size="size"
          :total="total"
          layout="prev, pager, next"
          background
          @current-change="(value: number) => (page = value - 1)"
        />
      </div>
    </div>

    <NewProjectDialog v-model="dialogVisible" @created="onCreated" />
  </div>
</template>

<style scoped>
.search-input {
  width: 240px;
}

.project-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 18px;
}

.project-card {
  position: relative;
  display: flex;
  flex-direction: column;
  padding: 20px;
  background: #ffffff;
  border: 1px solid var(--ss-border);
  border-radius: 16px;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
  cursor: pointer;
  transition:
    transform 0.18s ease,
    box-shadow 0.18s ease,
    border-color 0.18s ease;
}

.project-card::before {
  content: '';
  position: absolute;
  inset: 0 0 auto 0;
  height: 3px;
  border-radius: 16px 16px 0 0;
  background: linear-gradient(90deg, #6366f1, #06b6d4);
  opacity: 0;
  transition: opacity 0.18s ease;
}

.project-card:hover {
  transform: translateY(-2px);
  border-color: #dbe0ee;
  box-shadow: 0 18px 40px -28px rgba(15, 23, 42, 0.35);
}

.project-card:hover::before {
  opacity: 1;
}

.card-head {
  display: flex;
  align-items: center;
  gap: 11px;
}

.project-avatar {
  width: 40px;
  height: 40px;
  border-radius: 11px;
  display: grid;
  place-items: center;
  background: linear-gradient(135deg, #eef2ff, #e0f2fe);
  color: #4f46e5;
  font-weight: 750;
  font-size: 15px;
  flex-shrink: 0;
}

.card-title {
  min-width: 0;
  flex: 1;
}

.card-title h3 {
  margin: 0;
  font-size: 15.5px;
  letter-spacing: -0.015em;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.card-title .url-text {
  display: block;
  margin-top: 3px;
}

.more-button {
  width: 30px;
  height: 30px;
  border: 1px solid transparent;
  border-radius: 8px;
  background: transparent;
  color: var(--ss-muted);
  font-size: 16px;
  cursor: pointer;
  line-height: 1;
}

.more-button:hover {
  background: #f1f5f9;
  border-color: var(--ss-border);
}

.card-description {
  margin: 16px 0;
  min-height: 40px;
  color: var(--ss-muted);
  font-size: 12.5px;
  line-height: 1.55;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.card-stats {
  display: flex;
  align-items: center;
  gap: 24px;
  padding: 14px 0;
  border-top: 1px solid var(--ss-border);
  border-bottom: 1px solid var(--ss-border);
}

.stat {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.stat strong {
  font-size: 18px;
  letter-spacing: -0.03em;
  line-height: 1;
}

.stat-label {
  font-size: 10.5px;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  color: var(--ss-muted);
  font-weight: 600;
}

.status-stat {
  margin-left: auto;
  align-items: flex-end;
}

.card-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 13px 0;
  min-height: 41px;
}

.schedule-pill {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 4px 9px;
  border-radius: 999px;
  background: #eef2ff;
  color: #4338ca;
  font-size: 11px;
  font-weight: 600;
}

.card-actions {
  display: flex;
  gap: 8px;
  margin-top: auto;
}

.card-actions .el-button {
  flex: 1;
}

.pagination {
  display: flex;
  justify-content: center;
  margin-top: 28px;
}

@media (max-width: 720px) {
  .search-input {
    width: 100%;
  }
}
</style>
