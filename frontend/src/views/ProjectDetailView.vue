<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import {
  Aim,
  ArrowLeft,
  Camera,
  CopyDocument,
  Delete,
  Download,
  Edit,
  MagicStick,
  Monitor,
  Plus,
  Refresh,
  Setting,
  Timer,
  VideoPlay,
  View,
} from '@element-plus/icons-vue';
import { api, ApiError } from '@/api/client';
import type {
  CodeResponse,
  DiagnosisReport,
  FieldDto,
  FieldInput,
  PickResponse,
  PreviewResponse,
  ProjectDetail,
  RenderResponse,
  RunSummary,
  SelectorCandidate,
} from '@/api/types';
import { formatDateTime, formatDuration, hostOf, timeAgo } from '@/utils/format';
import StatusTag from '@/components/StatusTag.vue';
import DiagnosisPanel from '@/components/DiagnosisPanel.vue';

const route = useRoute();
const router = useRouter();

const projectId = computed(() => Number(route.params.id));
const project = ref<ProjectDetail | null>(null);
const loading = ref(true);
const activeTab = ref('builder');

const saving = ref(false);
const rendering = ref(false);
const running = ref(false);
const picking = ref(false);
const previewing = ref(false);
const diagnosing = ref(false);

const sessionId = ref('');
const screenshotUrl = ref('');
const screenshotMeta = ref<RenderResponse | null>(null);
const pickResult = ref<PickResponse | null>(null);

const previewResult = ref<PreviewResponse | null>(null);

const diagnosis = ref<DiagnosisReport | null>(null);

const language = ref('python');
const code = ref('');
const codeLoading = ref(false);

const runs = ref<RunSummary[]>([]);
const runsLoading = ref(false);
const runsPage = ref(0);
const runsTotal = ref(0);

const settingsVisible = ref(false);
const settingsForm = reactive({
  name: '',
  url: '',
  description: '',
  itemSelector: '',
  itemSelectorType: 'CSS',
  scheduleEnabled: false,
  intervalMinutes: 60,
});

const fieldDialogVisible = ref(false);
const fieldMode = ref<'create' | 'edit'>('create');
const editingFieldId = ref<number | null>(null);
const fieldSaving = ref(false);
const fieldForm = reactive<FieldInput>({
  name: '',
  selector: '',
  selectorType: 'CSS',
  attribute: 'text',
  multiple: false,
});

const attributeOptions = ['text', 'html', 'href', 'src', 'title', 'class', 'value', 'data-src'];
const languageOptions = [
  { label: 'Python', value: 'python' },
  { label: 'Java', value: 'java' },
  { label: 'Node.js', value: 'node' },
];

const previewColumns = computed(() => {
  const first = previewResult.value?.rows[0];
  return first ? Object.keys(first) : [];
});

const codeFilename = computed(() => {
  if (language.value === 'python') {
    return 'scraper.py';
  }
  if (language.value === 'java') {
    return 'ScrapeResult.java';
  }
  return 'scraper.mjs';
});

function syncSettings(): void {
  if (!project.value) {
    return;
  }
  settingsForm.name = project.value.name;
  settingsForm.url = project.value.url;
  settingsForm.description = project.value.description ?? '';
  settingsForm.itemSelector = project.value.itemSelector ?? '';
  settingsForm.itemSelectorType = project.value.itemSelectorType;
  settingsForm.scheduleEnabled = project.value.scheduleEnabled;
  settingsForm.intervalMinutes = project.value.intervalMinutes;
}

async function load(): Promise<void> {
  loading.value = true;
  try {
    project.value = await api.getProject(projectId.value);
    syncSettings();
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : 'Could not load this project');
  } finally {
    loading.value = false;
  }
}

async function saveProject(silent = false): Promise<void> {
  if (!project.value) {
    return;
  }
  saving.value = true;
  try {
    project.value = await api.updateProject(projectId.value, {
      name: project.value.name,
      url: project.value.url,
      description: project.value.description,
      itemSelector: project.value.itemSelector,
      itemSelectorType: project.value.itemSelectorType,
      scheduleEnabled: project.value.scheduleEnabled,
      intervalMinutes: project.value.intervalMinutes,
    });
    syncSettings();
    if (!silent) {
      ElMessage.success('Project saved');
    }
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : 'Could not save the project');
  } finally {
    saving.value = false;
  }
}

async function saveSettings(): Promise<void> {
  if (!settingsForm.name.trim() || !settingsForm.url.trim()) {
    ElMessage.warning('Name and URL are required');
    return;
  }
  if (settingsForm.intervalMinutes < 1) {
    ElMessage.warning('Interval must be at least one minute');
    return;
  }

  saving.value = true;
  try {
    project.value = await api.updateProject(projectId.value, {
      name: settingsForm.name.trim(),
      url: settingsForm.url.trim(),
      description: settingsForm.description.trim() || null,
      itemSelector: settingsForm.itemSelector.trim() || null,
      itemSelectorType: settingsForm.itemSelectorType,
      scheduleEnabled: settingsForm.scheduleEnabled,
      intervalMinutes: settingsForm.intervalMinutes,
    });
    settingsVisible.value = false;
    ElMessage.success('Project settings saved');
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : 'Could not save settings');
  } finally {
    saving.value = false;
  }
}

async function renderPage(): Promise<void> {
  if (!project.value) {
    return;
  }
  rendering.value = true;
  try {
    const result = await api.renderProject(projectId.value, project.value.url);
    sessionId.value = result.sessionId ?? '';
    screenshotUrl.value = result.screenshotUrl ?? '';
    screenshotMeta.value = result;
    pickResult.value = null;
    if (result.blocked) {
      ElMessage.warning(result.blockReason ?? 'The target returned a challenge page');
    } else if (!result.sessionId) {
      ElMessage.warning('The worker did not return a render session');
    }
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : 'Could not render the page');
  } finally {
    rendering.value = false;
  }
}

async function handleScreenshotClick(event: MouseEvent): Promise<void> {
  if (!sessionId.value || picking.value) {
    return;
  }
  const img = event.currentTarget as HTMLImageElement;
  const rect = img.getBoundingClientRect();
  if (!rect.width || !rect.height) {
    return;
  }
  const x = ((event.clientX - rect.left) * img.naturalWidth) / rect.width;
  const y = ((event.clientY - rect.top) * img.naturalHeight) / rect.height;

  picking.value = true;
  try {
    const result = await api.pickElement(projectId.value, sessionId.value, x, y);
    pickResult.value = result;
    if (project.value && !project.value.itemSelector && result.itemSelector) {
      project.value.itemSelector = result.itemSelector;
      ElMessage.success(
        `Item selector suggested: ${result.itemSelector}. Save the project to keep it.`
      );
    }
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : 'Could not pick that element');
  } finally {
    picking.value = false;
  }
}

function useSuggestedItemSelector(): void {
  if (!project.value || !pickResult.value) {
    return;
  }
  project.value.itemSelector = pickResult.value.itemSelector;
  ElMessage.success('Suggested item selector applied');
}

function guessAttribute(candidate: SelectorCandidate, tag?: string): string {
  if (candidate.sample?.trim()) {
    return 'text';
  }
  if (tag === 'a') {
    return 'href';
  }
  if (tag === 'img') {
    return 'src';
  }
  return 'text';
}

function suggestName(tag: string | undefined, candidate: SelectorCandidate): string {
  const sample = candidate.sample ?? '';
  const selector = candidate.selector.toLowerCase();
  let base = 'field';

  if (/[£$€¥]\s?\d/.test(sample) || /price/.test(selector)) {
    base = 'price';
  } else if (/stock|available/i.test(sample)) {
    base = 'availability';
  } else if (/rating|star/.test(selector)) {
    base = 'rating';
  } else if (tag === 'a' && /h[1-6]|title/.test(selector)) {
    base = 'title';
  } else if (tag === 'a') {
    base = 'link';
  } else if (tag === 'img') {
    base = 'image';
  } else if (tag && /^h[1-6]$/.test(tag)) {
    base = 'title';
  } else if (tag === 'li' || tag === 'tr') {
    base = 'item';
  } else if (tag && ['p', 'span', 'div', 'td', 'strong'].includes(tag)) {
    base = 'text';
  } else if (tag) {
    base = tag;
  }

  const names = new Set(project.value?.fields.map((field) => field.name) ?? []);
  if (!names.has(base)) {
    return base;
  }
  let index = 2;
  while (names.has(`${base}${index}`)) {
    index += 1;
  }
  return `${base}${index}`;
}

function openFieldFromCandidate(candidate: SelectorCandidate): void {
  fieldMode.value = 'create';
  editingFieldId.value = null;
  Object.assign(fieldForm, {
    name: suggestName(pickResult.value?.element.tag, candidate),
    selector: candidate.selector,
    selectorType: candidate.type,
    attribute: guessAttribute(candidate, pickResult.value?.element.tag),
    multiple: false,
  });
  fieldDialogVisible.value = true;
}

function openFieldDialog(mode: 'create' | 'edit', field?: FieldDto): void {
  fieldMode.value = mode;
  editingFieldId.value = field?.id ?? null;
  Object.assign(fieldForm, {
    name: field?.name ?? '',
    selector: field?.selector ?? '',
    selectorType: field?.selectorType ?? 'CSS',
    attribute: field?.attribute ?? 'text',
    multiple: field?.multiple ?? false,
  });
  fieldDialogVisible.value = true;
}

async function saveField(): Promise<void> {
  if (!fieldForm.name.trim() || !fieldForm.selector.trim()) {
    ElMessage.warning('Field name and selector are required');
    return;
  }
  fieldSaving.value = true;
  try {
    const payload: FieldInput = {
      name: fieldForm.name.trim(),
      selector: fieldForm.selector.trim(),
      selectorType: fieldForm.selectorType,
      attribute: fieldForm.attribute.trim() || 'text',
      multiple: fieldForm.multiple,
    };

    if (fieldMode.value === 'create') {
      const created = await api.addField(projectId.value, payload);
      project.value?.fields.push(created);
      ElMessage.success(`Field "${created.name}" added`);
    } else if (editingFieldId.value !== null) {
      const updated = await api.updateField(projectId.value, editingFieldId.value, payload);
      const fields = project.value?.fields ?? [];
      const index = fields.findIndex((field) => field.id === updated.id);
      if (index >= 0) {
        fields.splice(index, 1, updated);
      }
      ElMessage.success(`Field "${updated.name}" updated`);
    }

    fieldDialogVisible.value = false;
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : 'Could not save the field');
  } finally {
    fieldSaving.value = false;
  }
}

async function removeField(field: FieldDto): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `Delete the field "${field.name}"?`,
      'Delete field',
      { confirmButtonText: 'Delete', cancelButtonText: 'Cancel', type: 'warning' }
    );
  } catch {
    return;
  }

  try {
    await api.deleteField(projectId.value, field.id);
    if (project.value) {
      project.value.fields = project.value.fields.filter((item) => item.id !== field.id);
    }
    ElMessage.success('Field deleted');
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : 'Could not delete the field');
  }
}

async function previewRows(): Promise<void> {
  if (!project.value) {
    return;
  }
  if (!project.value.fields.length) {
    ElMessage.warning('Add at least one field before previewing');
    return;
  }
  previewing.value = true;
  try {
    previewResult.value = await api.previewProject(projectId.value, {
      url: project.value.url,
      itemSelector: project.value.itemSelector ?? undefined,
      itemSelectorType: project.value.itemSelectorType,
      limit: 50,
    });
    if (previewResult.value.blocked) {
      ElMessage.warning(previewResult.value.blockReason ?? 'The preview was blocked');
    } else {
      ElMessage.success(`Previewed ${previewResult.value.count} row(s)`);
    }
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : 'Could not build a preview');
  } finally {
    previewing.value = false;
  }
}

async function runNow(): Promise<void> {
  if (!project.value) {
    return;
  }
  running.value = true;
  try {
    const run = await api.createRun(projectId.value);
    ElMessage.success('Run queued');
    router.push({ name: 'run-detail', params: { id: run.id } });
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : 'Could not start the run');
  } finally {
    running.value = false;
  }
}

async function loadRuns(): Promise<void> {
  runsLoading.value = true;
  try {
    const response = await api.listProjectRuns(projectId.value, runsPage.value, 10);
    runs.value = response.items;
    runsTotal.value = response.total;
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : 'Could not load runs');
  } finally {
    runsLoading.value = false;
  }
}

async function loadCode(): Promise<void> {
  codeLoading.value = true;
  try {
    const response: CodeResponse = await api.generateCode(projectId.value, language.value);
    code.value = response.code || '# Add fields in the visual builder to generate a script.';
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : 'Could not generate code');
  } finally {
    codeLoading.value = false;
  }
}

async function runDiagnosis(): Promise<void> {
  diagnosing.value = true;
  try {
    diagnosis.value = await api.diagnoseProject(projectId.value, project.value?.url);
    ElMessage.success('Diagnosis complete');
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : 'Diagnosis failed');
  } finally {
    diagnosing.value = false;
  }
}

async function copyCode(): Promise<void> {
  try {
    await navigator.clipboard.writeText(code.value);
    ElMessage.success('Code copied to clipboard');
  } catch {
    ElMessage.warning('Clipboard access is blocked in this browser context');
  }
}

function downloadCode(): void {
  const blob = new Blob([code.value], { type: 'text/plain;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = codeFilename.value;
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  URL.revokeObjectURL(url);
}

function openRun(run: RunSummary): void {
  router.push({ name: 'run-detail', params: { id: run.id } });
}

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

function goBack(): void {
  router.push({ name: 'projects' });
}

watch(activeTab, (tab) => {
  if (tab === 'runs') {
    void loadRuns();
  }
  if (tab === 'code') {
    void loadCode();
  }
});

watch(language, () => {
  if (activeTab.value === 'code') {
    void loadCode();
  }
});

watch(runsPage, () => {
  if (activeTab.value === 'runs') {
    void loadRuns();
  }
});

onMounted(load);
</script>

<template>
  <div class="page project-detail fade-in" v-loading="loading">
    <div class="back-row">
      <el-button link @click="goBack">
        <el-icon><ArrowLeft /></el-icon>
        Back to projects
      </el-button>
    </div>

    <section v-if="project" class="project-hero panel">
      <div class="hero-main">
        <div class="project-avatar">{{ project.name.slice(0, 1).toUpperCase() }}</div>
        <div class="hero-copy">
          <div class="hero-title-row">
            <h2>{{ project.name }}</h2>
            <span v-if="project.scheduleEnabled" class="schedule-pill">
              <el-icon><Timer /></el-icon> every {{ project.intervalMinutes }} min
            </span>
          </div>
          <a :href="project.url" target="_blank" rel="noreferrer" class="hero-url">
            {{ project.url }}
          </a>
          <p v-if="project.description" class="hero-description">{{ project.description }}</p>
        </div>
      </div>

      <div class="hero-actions">
        <el-button :icon="Setting" @click="settingsVisible = true">Settings</el-button>
        <el-button :loading="saving" @click="saveProject()">Save</el-button>
        <el-button type="primary" :loading="running" @click="runNow">
          <el-icon><VideoPlay /></el-icon>
          Run now
        </el-button>
      </div>
    </section>

    <div v-if="project" class="hero-stats">
      <div class="hero-stat">
        <span class="muted tiny">Fields</span>
        <strong>{{ project.fields.length }}</strong>
      </div>
      <div class="hero-stat">
        <span class="muted tiny">Last run</span>
        <strong>{{ timeAgo(project.lastRunAt) }}</strong>
      </div>
      <div class="hero-stat">
        <span class="muted tiny">Next run</span>
        <strong>{{ project.scheduleEnabled ? formatDateTime(project.nextRunAt) : 'Manual' }}</strong>
      </div>
      <div class="hero-stat">
        <span class="muted tiny">Updated</span>
        <strong>{{ timeAgo(project.updatedAt) }}</strong>
      </div>
    </div>

    <el-tabs v-if="project" v-model="activeTab" class="detail-tabs">
      <el-tab-pane label="Visual builder" name="builder">
        <div class="builder-grid">
          <section class="panel canvas-panel">
            <div class="panel-header">
              <div>
                <div class="panel-title">Rendered page</div>
                <div class="panel-subtitle">Render with Playwright, then click any element to inspect it</div>
              </div>
              <div class="canvas-actions">
                <el-button
                  :loading="rendering"
                  :icon="Camera"
                  type="primary"
                  plain
                  @click="renderPage"
                >
                  {{ screenshotUrl ? 'Re-render' : 'Render page' }}
                </el-button>
              </div>
            </div>

            <div class="canvas-body">
              <div v-if="screenshotMeta" class="canvas-meta">
                <span class="http-pill" :class="{ danger: screenshotMeta.status >= 400 }">
                  HTTP {{ screenshotMeta.status }}
                </span>
                <span class="canvas-title">{{ screenshotMeta.title || 'Untitled page' }}</span>
                <span v-if="screenshotMeta.blocked" class="blocked-pill">Challenge detected</span>
              </div>

              <div v-if="screenshotUrl" class="screenshot-wrap" :class="{ picking }">
                <img
                  :src="screenshotUrl"
                  :alt="screenshotMeta?.title ?? 'Rendered page'"
                  class="page-shot"
                  @click="handleScreenshotClick"
                />
                <div class="pick-hint">
                  <el-icon><Aim /></el-icon>
                  Click an element to generate selectors
                </div>
              </div>

              <div v-else class="canvas-empty">
                <div class="canvas-illustration">
                  <el-icon><Monitor /></el-icon>
                </div>
                <h3>Render the page to start picking</h3>
                <p>
                  The worker opens {{ hostOf(project.url) }} in Chromium and captures a full
                  screenshot. No selectors are guessed until you click.
                </p>
                <el-button type="primary" :loading="rendering" @click="renderPage">
                  Render page
                </el-button>
              </div>

              <el-alert
                v-if="screenshotMeta?.blocked"
                class="canvas-alert"
                type="warning"
                :closable="false"
                show-icon
                :title="screenshotMeta.blockReason ?? 'The page looks like a challenge page'"
                description="Try the anti-bot doctor tab, or render again with a warm browser session."
              />
            </div>
          </section>

          <section class="panel fields-panel">
            <div class="panel-header">
              <div>
                <div class="panel-title">Fields</div>
                <div class="panel-subtitle">Selectors are evaluated inside each matched item</div>
              </div>
              <el-button size="small" type="primary" :icon="Plus" @click="openFieldDialog('create')">
                Add field
              </el-button>
            </div>

            <div class="panel-body selector-box">
              <label class="selector-label">Item selector</label>
              <div class="selector-row">
                <el-input
                  v-model="project.itemSelector"
                  placeholder=".product_pod"
                  size="small"
                  class="mono-input"
                />
                <el-select v-model="project.itemSelectorType" size="small" class="type-select">
                  <el-option label="CSS" value="CSS" />
                  <el-option label="XPath" value="XPATH" />
                </el-select>
              </div>
              <p class="selector-help">
                Each field is read relative to this repeated element. Click the page to let
                ScrapeStudio suggest one.
              </p>
            </div>

            <div v-if="project.fields.length" class="field-list">
              <div v-for="field in project.fields" :key="field.id" class="field-row">
                <div class="field-order">{{ field.orderIndex + 1 }}</div>
                <div class="field-copy">
                  <div class="field-name">
                    {{ field.name }}
                    <span v-if="field.multiple" class="mini-badge">multi</span>
                  </div>
                  <div class="field-selector mono" :title="field.selector">
                    {{ field.selector }}
                  </div>
                </div>
                <span class="attr-badge">{{ field.attribute }}</span>
                <div class="field-actions">
                  <el-button link :icon="Edit" aria-label="Edit field" @click="openFieldDialog('edit', field)" />
                  <el-button
                    link
                    type="danger"
                    :icon="Delete"
                    aria-label="Delete field"
                    @click="removeField(field)"
                  />
                </div>
              </div>
            </div>

            <div v-else class="empty-state small">
              <div class="empty-icon"><el-icon><View /></el-icon></div>
              <h3>No fields yet</h3>
              <p>Click an element on the rendered page, then add one of the suggested selectors.</p>
            </div>
          </section>
        </div>

        <section v-if="pickResult" class="panel pick-panel fade-in">
          <div class="panel-header">
            <div>
              <div class="panel-title">Picked element</div>
              <div class="panel-subtitle">
                <span class="mono">&lt;{{ pickResult.element.tag }}&gt;</span>
                {{ pickResult.element.preview || '(no text)' }}
              </div>
            </div>
            <el-tag size="small" effect="plain">{{ pickResult.fieldSelectors.length }} candidates</el-tag>
          </div>
          <div class="panel-body pick-grid">
            <div class="item-suggestion">
              <span class="selector-label">Repeating item</span>
              <div class="suggestion-value mono">{{ pickResult.itemSelector }}</div>
              <div class="suggestion-foot">
                <span class="muted tiny">{{ pickResult.itemMatchCount }} matches on page</span>
                <el-button
                  size="small"
                  type="primary"
                  plain
                  :disabled="project.itemSelector === pickResult.itemSelector"
                  @click="useSuggestedItemSelector"
                >
                  Use selector
                </el-button>
              </div>
              <p class="item-preview mono">{{ pickResult.itemPreview }}</p>
            </div>

            <div class="candidates">
              <div class="candidates-title">Field selector candidates</div>
              <button
                v-for="candidate in pickResult.fieldSelectors"
                :key="candidate.type + candidate.selector"
                type="button"
                class="candidate-row"
                @click="openFieldFromCandidate(candidate)"
              >
                <span class="candidate-type">{{ candidate.type }}</span>
                <span class="candidate-selector mono">{{ candidate.selector }}</span>
                <span class="candidate-count">{{ candidate.matchCount }}×</span>
                <span class="candidate-sample">{{ candidate.sample || '—' }}</span>
                <el-icon class="candidate-add"><Plus /></el-icon>
              </button>
            </div>
          </div>
        </section>

        <section class="panel preview-panel">
          <div class="panel-header">
            <div>
              <div class="panel-title">Preview</div>
              <div class="panel-subtitle">Run the saved selectors against the live page</div>
            </div>
            <div class="preview-actions">
              <el-tag v-if="previewResult" size="small" effect="plain">
                {{ previewResult.count }} rows
              </el-tag>
              <el-button :loading="previewing" :icon="Refresh" @click="previewRows">
                Preview rows
              </el-button>
            </div>
          </div>

          <div v-if="previewResult" class="preview-body">
            <el-alert
              v-if="previewResult.blocked"
              :title="previewResult.blockReason ?? 'Preview blocked'"
              type="warning"
              :closable="false"
              show-icon
              class="preview-alert"
            />
            <el-alert
              v-for="warning in previewResult.warnings"
              :key="warning"
              :title="warning"
              type="info"
              :closable="false"
              show-icon
              class="preview-alert"
            />
            <el-table
              v-if="previewResult.rows.length"
              :data="previewResult.rows"
              max-height="380"
              class="preview-table"
            >
              <el-table-column
                v-for="column in previewColumns"
                :key="column"
                :label="column"
                min-width="160"
                show-overflow-tooltip
              >
                <template #default="{ row }">{{ formatCell(row[column]) }}</template>
              </el-table-column>
            </el-table>
            <div v-else class="empty-state small">
              <div class="empty-icon"><el-icon><View /></el-icon></div>
              <h3>No rows matched</h3>
              <p>Check the item selector, or click a different element on the page.</p>
            </div>
          </div>

          <div v-else class="empty-state small">
            <div class="empty-icon"><el-icon><View /></el-icon></div>
            <h3>Preview before you run</h3>
            <p>Save the fields you need, then preview the first 50 rows without storing a run.</p>
            <el-button :loading="previewing" @click="previewRows">Preview rows</el-button>
          </div>
        </section>
      </el-tab-pane>

      <el-tab-pane label="Generated code" name="code">
        <section class="panel">
          <div class="panel-header">
            <div>
              <div class="panel-title">Generated scraper</div>
              <div class="panel-subtitle">
                Mirrors the saved item selector and fields · {{ project.fields.length }} fields
              </div>
            </div>
            <div class="code-actions">
              <el-radio-group v-model="language" size="small">
                <el-radio-button
                  v-for="option in languageOptions"
                  :key="option.value"
                  :value="option.value"
                >
                  {{ option.label }}
                </el-radio-button>
              </el-radio-group>
              <el-button size="small" :icon="CopyDocument" @click="copyCode">Copy</el-button>
              <el-button size="small" :icon="Download" @click="downloadCode">Download</el-button>
            </div>
          </div>
          <pre v-loading="codeLoading" class="code-block">{{ code }}</pre>
        </section>
      </el-tab-pane>

      <el-tab-pane label="Run history" name="runs">
        <section class="panel">
          <div class="panel-header">
            <div>
              <div class="panel-title">Runs for this project</div>
              <div class="panel-subtitle">Manual and scheduled executions</div>
            </div>
            <div class="preview-actions">
              <el-button :icon="Refresh" :loading="runsLoading" @click="loadRuns">Refresh</el-button>
              <el-button type="primary" :loading="running" @click="runNow">
                <el-icon><VideoPlay /></el-icon> Run now
              </el-button>
            </div>
          </div>

          <el-table
            v-if="runs.length"
            v-loading="runsLoading"
            :data="runs"
            class="runs-table"
            @row-click="openRun"
          >
            <el-table-column prop="id" label="#" width="70" />
            <el-table-column label="Status" width="110">
              <template #default="{ row }"><StatusTag :status="row.status" /></template>
            </el-table-column>
            <el-table-column prop="triggerType" label="Trigger" width="110">
              <template #default="{ row }">
                <span class="muted">{{ row.triggerType === 'SCHEDULE' ? 'Scheduled' : 'Manual' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="Started" min-width="150">
              <template #default="{ row }">{{ formatDateTime(row.startedAt) }}</template>
            </el-table-column>
            <el-table-column label="Duration" width="110" align="right">
              <template #default="{ row }">{{ formatDuration(row.durationMs) }}</template>
            </el-table-column>
            <el-table-column prop="itemCount" label="Items" width="90" align="right" />
            <el-table-column label="" width="60">
              <template #default>
                <el-icon class="row-arrow"><ArrowLeft /></el-icon>
              </template>
            </el-table-column>
          </el-table>
          <div v-else class="empty-state small">
            <div class="empty-icon"><el-icon><VideoPlay /></el-icon></div>
            <h3>No runs yet</h3>
            <p>Start the first extraction with "Run now".</p>
          </div>

          <div v-if="runsTotal > 10" class="pagination">
            <el-pagination
              :current-page="runsPage + 1"
              :page-size="10"
              :total="runsTotal"
              layout="prev, pager, next"
              background
              @current-change="(value: number) => (runsPage = value - 1)"
            />
          </div>
        </section>
      </el-tab-pane>

      <el-tab-pane label="Anti-bot doctor" name="diagnosis">
        <section class="panel doctor-panel">
          <div class="panel-header">
            <div>
              <div class="panel-title">Anti-bot diagnosis</div>
              <div class="panel-subtitle">Checks TLS, HTTP/2, CDN, cookies and challenge markers</div>
            </div>
            <el-button type="primary" plain :loading="diagnosing" :icon="MagicStick" @click="runDiagnosis">
              {{ diagnosis ? 'Run again' : 'Run diagnosis' }}
            </el-button>
          </div>
          <DiagnosisPanel
            :report="diagnosis"
            :loading="diagnosing"
            empty-text="Run the doctor against this project URL. It fetches the page with browser-like headers and inspects the TLS handshake, HTTP/2 negotiation, CDN headers and challenge markers."
          >
            <template #action>
              <el-button v-if="!diagnosis" type="primary" :loading="diagnosing" @click="runDiagnosis">
                Diagnose {{ hostOf(project.url) }}
              </el-button>
            </template>
          </DiagnosisPanel>
        </section>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="settingsVisible" title="Project settings" width="580px">
      <el-form label-position="top">
        <div class="form-row">
          <el-form-item label="Project name" class="grow">
            <el-input v-model="settingsForm.name" maxlength="120" />
          </el-form-item>
          <el-form-item label="Item selector type" class="narrow">
            <el-select v-model="settingsForm.itemSelectorType">
              <el-option label="CSS" value="CSS" />
              <el-option label="XPath" value="XPATH" />
            </el-select>
          </el-form-item>
        </div>
        <el-form-item label="Start URL">
          <el-input v-model="settingsForm.url" />
        </el-form-item>
        <el-form-item label="Item selector">
          <el-input v-model="settingsForm.itemSelector" class="mono-input" placeholder=".product_pod" />
        </el-form-item>
        <el-form-item label="Description">
          <el-input v-model="settingsForm.description" type="textarea" :rows="2" maxlength="1000" />
        </el-form-item>
        <div class="schedule-block">
          <div class="schedule-copy">
            <strong>Scheduled runs</strong>
            <p>Run this project on a fixed interval while the backend is online.</p>
          </div>
          <el-switch v-model="settingsForm.scheduleEnabled" />
        </div>
        <el-form-item v-if="settingsForm.scheduleEnabled" label="Interval (minutes)">
          <el-input-number v-model="settingsForm.intervalMinutes" :min="1" :max="10080" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="settingsVisible = false">Cancel</el-button>
        <el-button type="primary" :loading="saving" @click="saveSettings">Save settings</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="fieldDialogVisible"
      :title="fieldMode === 'create' ? 'Add field' : 'Edit field'"
      width="560px"
    >
      <el-form label-position="top">
        <div class="form-row">
          <el-form-item label="Field name" class="grow">
            <el-input v-model="fieldForm.name" placeholder="title" maxlength="80" />
          </el-form-item>
          <el-form-item label="Multiple values" class="narrow switch-item">
            <el-switch v-model="fieldForm.multiple" />
          </el-form-item>
        </div>
        <el-form-item label="Selector">
          <el-input v-model="fieldForm.selector" class="mono-input" placeholder="h3 a" />
        </el-form-item>
        <div class="form-row">
          <el-form-item label="Selector type" class="narrow">
            <el-select v-model="fieldForm.selectorType">
              <el-option label="CSS" value="CSS" />
              <el-option label="XPath" value="XPATH" />
            </el-select>
          </el-form-item>
          <el-form-item label="Read attribute" class="grow">
            <el-select
              v-model="fieldForm.attribute"
              filterable
              allow-create
              default-first-option
            >
              <el-option v-for="option in attributeOptions" :key="option" :label="option" :value="option" />
            </el-select>
          </el-form-item>
        </div>
        <p class="field-help">
          Use <code>text</code> for visible text, <code>html</code> for inner markup, or any
          attribute such as <code>href</code>, <code>src</code>, <code>title</code> or
          <code>class</code>.
        </p>
      </el-form>
      <template #footer>
        <el-button @click="fieldDialogVisible = false">Cancel</el-button>
        <el-button type="primary" :loading="fieldSaving" @click="saveField">
          {{ fieldMode === 'create' ? 'Add field' : 'Save field' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.back-row {
  margin-bottom: 14px;
}

.project-hero {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
  padding: 24px;
}

.hero-main {
  display: flex;
  gap: 15px;
  min-width: 0;
}

.project-avatar {
  width: 52px;
  height: 52px;
  border-radius: 14px;
  display: grid;
  place-items: center;
  background: linear-gradient(135deg, #eef2ff, #e0f2fe);
  color: #4f46e5;
  font-weight: 800;
  font-size: 20px;
  flex-shrink: 0;
}

.hero-copy {
  min-width: 0;
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

.schedule-pill {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 4px 9px;
  border-radius: 999px;
  background: #eef2ff;
  color: #4338ca;
  font-size: 11px;
  font-weight: 650;
}

.hero-url {
  display: inline-block;
  margin-top: 5px;
  color: var(--ss-muted);
  font-size: 12.5px;
  overflow-wrap: anywhere;
}

.hero-url:hover {
  color: var(--ss-primary-dark);
}

.hero-description {
  margin: 10px 0 0;
  max-width: 720px;
  color: var(--ss-muted);
  font-size: 13px;
  line-height: 1.55;
}

.hero-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
  flex-wrap: wrap;
}

.hero-stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin: 14px 0 8px;
}

.hero-stat {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 12px 16px;
  border: 1px solid var(--ss-border);
  border-radius: 12px;
  background: #ffffff;
}

.hero-stat strong {
  font-size: 15px;
  letter-spacing: -0.02em;
}

.detail-tabs {
  margin-top: 16px;
}

.detail-tabs :deep(.el-tabs__header) {
  margin-bottom: 16px;
}

.detail-tabs :deep(.el-tabs__nav-wrap::after) {
  height: 1px;
  background: var(--ss-border);
}

.detail-tabs :deep(.el-tabs__item) {
  font-weight: 600;
  font-size: 13.5px;
  height: 42px;
}

.builder-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.6fr) minmax(330px, 1fr);
  gap: 18px;
}

.canvas-actions {
  display: flex;
  gap: 8px;
}

.canvas-body {
  padding: 16px;
}

.canvas-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
  min-width: 0;
}

.http-pill {
  padding: 3px 8px;
  border-radius: 6px;
  background: #ecfdf5;
  color: #047857;
  font-size: 11px;
  font-weight: 700;
}

.http-pill.danger {
  background: #fef2f2;
  color: #b91c1c;
}

.canvas-title {
  font-size: 12.5px;
  color: var(--ss-muted);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.blocked-pill {
  margin-left: auto;
  padding: 3px 8px;
  border-radius: 6px;
  background: #fffbeb;
  color: #b45309;
  font-size: 11px;
  font-weight: 700;
  white-space: nowrap;
}

.screenshot-wrap {
  position: relative;
  border: 1px solid var(--ss-border);
  border-radius: 12px;
  overflow: hidden;
  background: #f8fafc;
}

.screenshot-wrap.picking {
  cursor: wait;
  opacity: 0.75;
}

.page-shot {
  display: block;
  width: 100%;
  height: auto;
  cursor: crosshair;
}

.pick-hint {
  position: absolute;
  left: 12px;
  bottom: 12px;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 7px 11px;
  border-radius: 999px;
  background: rgba(15, 23, 42, 0.82);
  color: #e2e8f0;
  font-size: 11.5px;
  font-weight: 600;
  backdrop-filter: blur(6px);
  pointer-events: none;
}

.canvas-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  min-height: 440px;
  padding: 30px;
  border: 1px dashed #dbe0ee;
  border-radius: 13px;
  background:
    radial-gradient(circle at 50% 0%, rgba(99, 102, 241, 0.06), transparent 55%),
    #fbfcfe;
  text-align: center;
}

.canvas-illustration {
  width: 74px;
  height: 74px;
  display: grid;
  place-items: center;
  border-radius: 22px;
  background: #eef2ff;
  color: #6366f1;
  font-size: 34px;
}

.canvas-empty h3 {
  margin: 6px 0 0;
  font-size: 16px;
}

.canvas-empty p {
  margin: 0 0 10px;
  max-width: 390px;
  color: var(--ss-muted);
  font-size: 13px;
  line-height: 1.6;
}

.canvas-alert {
  margin-top: 12px;
}

.fields-panel {
  display: flex;
  flex-direction: column;
  min-height: 540px;
}

.selector-box {
  border-bottom: 1px solid var(--ss-border);
  padding: 16px 20px;
}

.selector-label {
  display: block;
  font-size: 11px;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--ss-muted);
  font-weight: 700;
  margin-bottom: 7px;
}

.selector-row {
  display: flex;
  gap: 8px;
}

.mono-input :deep(.el-input__inner) {
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-size: 12.5px;
}

.type-select {
  width: 96px;
  flex-shrink: 0;
}

.selector-help {
  margin: 8px 0 0;
  font-size: 11.5px;
  line-height: 1.5;
  color: var(--ss-muted);
}

.field-list {
  display: flex;
  flex-direction: column;
  padding: 8px 12px 14px;
  overflow-y: auto;
}

.field-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 11px 8px;
  border-bottom: 1px solid #f1f4f9;
}

.field-row:last-child {
  border-bottom: 0;
}

.field-order {
  width: 22px;
  height: 22px;
  border-radius: 6px;
  display: grid;
  place-items: center;
  background: #f1f5f9;
  color: var(--ss-muted);
  font-size: 10.5px;
  font-weight: 700;
  flex-shrink: 0;
}

.field-copy {
  min-width: 0;
  flex: 1;
}

.field-name {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 650;
}

.mini-badge {
  padding: 1px 6px;
  border-radius: 5px;
  background: #ecfeff;
  color: #0e7490;
  font-size: 9.5px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

.field-selector {
  margin-top: 3px;
  color: var(--ss-muted);
  font-size: 11.5px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.attr-badge {
  padding: 2px 7px;
  border-radius: 5px;
  background: #f1f5f9;
  color: #475569;
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-size: 10px;
  flex-shrink: 0;
}

.field-actions {
  display: flex;
  flex-shrink: 0;
}

.empty-state.small {
  padding: 38px 20px;
}

.pick-panel {
  margin-top: 18px;
}

.pick-grid {
  display: grid;
  grid-template-columns: minmax(260px, 0.9fr) minmax(0, 1.4fr);
  gap: 24px;
}

.item-suggestion {
  padding: 14px;
  border: 1px solid var(--ss-border);
  border-radius: 12px;
  background: #fbfcfe;
}

.suggestion-value {
  padding: 9px 11px;
  border-radius: 8px;
  background: #0f172a;
  color: #c7d2fe;
  font-size: 12px;
  overflow-wrap: anywhere;
}

.suggestion-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-top: 10px;
}

.item-preview {
  margin: 12px 0 0;
  max-height: 90px;
  overflow: hidden;
  color: #94a3b8;
  font-size: 10.5px;
  line-height: 1.5;
  overflow-wrap: anywhere;
}

.candidates-title {
  font-size: 11px;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--ss-muted);
  font-weight: 700;
  margin-bottom: 9px;
}

.candidate-row {
  display: grid;
  grid-template-columns: 46px minmax(0, 1.4fr) 48px minmax(0, 1fr) 20px;
  align-items: center;
  gap: 9px;
  width: 100%;
  padding: 9px 10px;
  margin-bottom: 6px;
  border: 1px solid var(--ss-border);
  border-radius: 9px;
  background: #ffffff;
  text-align: left;
  cursor: pointer;
  transition: all 0.14s ease;
}

.candidate-row:hover {
  border-color: #c7d2fe;
  background: #f8faff;
}

.candidate-type {
  font-size: 9.5px;
  font-weight: 700;
  letter-spacing: 0.05em;
  color: #4338ca;
  background: #eef2ff;
  border-radius: 4px;
  padding: 2px 5px;
  text-align: center;
}

.candidate-selector {
  font-size: 11.5px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.candidate-count {
  font-size: 11px;
  color: var(--ss-muted);
  text-align: right;
}

.candidate-sample {
  font-size: 11px;
  color: var(--ss-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.candidate-add {
  color: #6366f1;
}

.preview-panel {
  margin-top: 18px;
}

.preview-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.preview-body {
  padding: 16px 20px 20px;
}

.preview-alert {
  margin-bottom: 10px;
}

.preview-table {
  width: 100%;
}

.runs-table {
  cursor: pointer;
  width: 100%;
}

.row-arrow {
  transform: rotate(180deg);
  color: #cbd5e1;
}

.pagination {
  display: flex;
  justify-content: center;
  padding: 16px;
}

.doctor-panel {
  padding-bottom: 14px;
}

.doctor-panel :deep(.diagnosis) {
  padding: 0 18px;
}

.code-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.form-row {
  display: flex;
  gap: 12px;
}

.form-row .grow {
  flex: 1;
}

.form-row .narrow {
  width: 180px;
}

.switch-item :deep(.el-form-item__content) {
  justify-content: flex-end;
}

.schedule-block {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 14px 16px;
  margin-bottom: 16px;
  border: 1px solid var(--ss-border);
  border-radius: 12px;
  background: #fbfcfe;
}

.schedule-copy strong {
  font-size: 13px;
}

.schedule-copy p {
  margin: 4px 0 0;
  font-size: 11.5px;
  color: var(--ss-muted);
  line-height: 1.45;
}

.field-help {
  margin: 0;
  font-size: 11.5px;
  line-height: 1.55;
  color: var(--ss-muted);
}

.field-help code {
  padding: 1px 4px;
  border-radius: 4px;
  background: #f1f5f9;
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-size: 10.5px;
}

@media (max-width: 1180px) {
  .builder-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .pick-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media (max-width: 900px) {
  .project-hero {
    flex-direction: column;
  }

  .hero-stats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .candidate-row {
    grid-template-columns: 44px minmax(0, 1fr) 42px 20px;
  }

  .candidate-sample {
    display: none;
  }
}

@media (max-width: 640px) {
  .hero-stats {
    grid-template-columns: minmax(0, 1fr);
  }

  .hero-actions {
    width: 100%;
  }

  .hero-actions .el-button {
    flex: 1;
  }

  .form-row {
    flex-direction: column;
    gap: 0;
  }

  .form-row .narrow {
    width: 100%;
  }
}
</style>
