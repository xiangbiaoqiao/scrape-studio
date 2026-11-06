<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import {
  Aim,
  Connection,
  FirstAidKit,
  MagicStick,
  Refresh,
  Search,
  Timer,
  WarningFilled,
} from '@element-plus/icons-vue';
import { api, ApiError } from '@/api/client';
import type { DiagnosisReport } from '@/api/types';
import { formatDateTime, hostOf } from '@/utils/format';
import DiagnosisPanel from '@/components/DiagnosisPanel.vue';

interface RecentCheck {
  url: string;
  verdict: string;
  riskScore: number;
  checkedAt: string;
}

const STORAGE_KEY = 'scrape-studio.doctor.recent';

const url = ref('https://books.toscrape.com/');
const report = ref<DiagnosisReport | null>(null);
const loading = ref(false);
const errorMessage = ref('');
const recent = ref<RecentCheck[]>([]);

const examples = [
  { label: 'Books to Scrape', url: 'https://books.toscrape.com/', note: 'Static sandbox' },
  { label: 'Quotes to Scrape', url: 'https://quotes.toscrape.com/', note: 'Static sandbox' },
  { label: 'Hacker News', url: 'https://news.ycombinator.com/', note: 'Live site' },
  { label: 'HTTPBin headers', url: 'https://httpbin.org/headers', note: 'Echo endpoint' },
];

const signals = [
  {
    icon: Connection,
    title: 'TLS & HTTP/2',
    copy: 'Inspects the negotiated TLS version, cipher and ALPN so you know whether the handshake looks like a current browser.',
  },
  {
    icon: WarningFilled,
    title: 'Challenge markers',
    copy: 'Detects Cloudflare interstitials, CAPTCHA widgets, access-denied pages and rate-limit responses in the returned HTML.',
  },
  {
    icon: Aim,
    title: 'Edge fingerprint',
    copy: 'Reads CDN and bot-management headers plus cookies such as cf_clearance and __cf_bm to spot an active defense layer.',
  },
];

const isValidUrl = computed(() => {
  const candidate = normalizedUrl(url.value);
  try {
    const parsed = new URL(candidate);
    return parsed.protocol === 'http:' || parsed.protocol === 'https:';
  } catch {
    return false;
  }
});

function normalizedUrl(value: string): string {
  const trimmed = value.trim();
  if (!trimmed) {
    return '';
  }
  return /^[a-zA-Z][a-zA-Z0-9+.-]*:\/\//.test(trimmed) ? trimmed : `https://${trimmed}`;
}

function loadRecent(): void {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    recent.value = raw ? (JSON.parse(raw) as RecentCheck[]) : [];
  } catch {
    recent.value = [];
  }
}

function saveRecent(entry: RecentCheck): void {
  const urlKey = entry.url.replace(/\/$/, '');
  const next = [
    entry,
    ...recent.value.filter((item) => item.url.replace(/\/$/, '') !== urlKey),
  ].slice(0, 6);
  recent.value = next;
  localStorage.setItem(STORAGE_KEY, JSON.stringify(next));
}

function verdictTone(verdict: string): string {
  if (verdict === 'low-risk') {
    return 'low';
  }
  if (verdict === 'medium-risk') {
    return 'medium';
  }
  return 'high';
}

async function diagnose(target?: string): Promise<void> {
  const candidate = normalizedUrl(target ?? url.value);
  if (!candidate) {
    ElMessage.warning('Enter a URL to diagnose');
    return;
  }

  try {
    const parsed = new URL(candidate);
    if (!['http:', 'https:'].includes(parsed.protocol)) {
      throw new Error('protocol');
    }
  } catch {
    ElMessage.warning('Use a valid http:// or https:// URL');
    return;
  }

  url.value = candidate;
  loading.value = true;
  errorMessage.value = '';
  try {
    const result = await api.diagnoseUrl(candidate);
    report.value = result;
    saveRecent({
      url: candidate,
      verdict: result.verdict,
      riskScore: result.riskScore,
      checkedAt: result.checkedAt ?? new Date().toISOString(),
    });
    if (result.verdict === 'unreachable') {
      ElMessage.warning('The target could not be reached');
    } else {
      ElMessage.success(`Diagnosis complete · ${result.verdict.replace('-risk', ' risk')}`);
    }
  } catch (error) {
    errorMessage.value =
      error instanceof ApiError
        ? error.message
        : 'The doctor could not reach the worker. Start it with "cd worker && npm run dev".';
    ElMessage.error(errorMessage.value);
  } finally {
    loading.value = false;
  }
}

function useExample(exampleUrl: string): void {
  url.value = exampleUrl;
  void diagnose(exampleUrl);
}

function clearHistory(): void {
  recent.value = [];
  localStorage.removeItem(STORAGE_KEY);
}

onMounted(loadRecent);
</script>

<template>
  <div class="page doctor-page fade-in">
    <div class="page-header">
      <div>
        <h2 class="page-title">Anti-bot doctor</h2>
        <p class="page-subtitle">
          Fingerprint a target before you schedule it. ScrapeStudio checks the TLS handshake,
          HTTP/2 negotiation, CDN headers, bot-management cookies and challenge markers, then
          explains what to do next.
        </p>
      </div>
      <div class="page-actions">
        <span class="doctor-badge">
          <el-icon><FirstAidKit /></el-icon>
          Live target inspection
        </span>
      </div>
    </div>

    <section class="doctor-hero panel">
      <div class="hero-glow" />
      <div class="hero-inner">
        <div class="hero-copy">
          <span class="eyebrow"><span class="pulse" /> Anti-bot diagnosis</span>
          <h3>Will this site fight back?</h3>
          <p>
            Paste any URL. The worker fetches it with a browser-like profile, inspects the edge
            layer and scores how likely a plain HTTP scraper is to be challenged.
          </p>
        </div>

        <div class="url-form">
          <el-input
            v-model="url"
            size="large"
            placeholder="https://example.com/products"
            clearable
            class="url-input"
            @keyup.enter="diagnose()"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
          <el-button
            type="primary"
            size="large"
            :loading="loading"
            :disabled="!isValidUrl"
            class="diagnose-button"
            @click="diagnose()"
          >
            <el-icon><MagicStick /></el-icon>
            Diagnose
          </el-button>
        </div>

        <div class="examples">
          <span class="examples-label">Try an example</span>
          <button
            v-for="example in examples"
            :key="example.url"
            type="button"
            class="example-chip"
            @click="useExample(example.url)"
          >
            <strong>{{ example.label }}</strong>
            <small>{{ example.note }}</small>
          </button>
        </div>
      </div>
    </section>

    <el-alert
      v-if="errorMessage"
      :title="errorMessage"
      type="error"
      :closable="false"
      show-icon
      class="doctor-error"
    />

    <div class="doctor-grid">
      <section class="panel report-panel">
        <div class="panel-header">
          <div>
            <div class="panel-title">Diagnosis report</div>
            <div class="panel-subtitle">
              {{ report ? hostOf(report.target ?? report.finalUrl ?? '') : 'Results appear here' }}
            </div>
          </div>
          <el-button
            v-if="report"
            size="small"
            :icon="Refresh"
            :loading="loading"
            @click="diagnose(report.target ?? url)"
          >
            Check again
          </el-button>
        </div>

        <div class="report-body">
          <DiagnosisPanel
            :report="report"
            :loading="loading"
            empty-text="Enter a URL above and press Diagnose. The report includes the risk score, each inspected signal and concrete recommendations for the next run."
          />
        </div>
      </section>

      <aside class="side-column">
        <section class="panel">
          <div class="panel-header">
            <div>
              <div class="panel-title">Recent checks</div>
              <div class="panel-subtitle">Stored locally in this browser</div>
            </div>
            <el-button v-if="recent.length" link size="small" @click="clearHistory">Clear</el-button>
          </div>
          <div v-if="recent.length" class="recent-list">
            <button
              v-for="item in recent"
              :key="item.url + item.checkedAt"
              type="button"
              class="recent-row"
              @click="useExample(item.url)"
            >
              <span class="recent-dot" :class="verdictTone(item.verdict)" />
              <span class="recent-copy">
                <strong>{{ hostOf(item.url) }}</strong>
                <small>{{ formatDateTime(item.checkedAt) }}</small>
              </span>
              <span class="recent-score" :class="verdictTone(item.verdict)">
                {{ item.riskScore }}
              </span>
            </button>
          </div>
          <div v-else class="recent-empty">
            <el-icon><Timer /></el-icon>
            <p>Checks you run will show up here for quick re-testing.</p>
          </div>
        </section>

        <section class="panel">
          <div class="panel-header">
            <div>
              <div class="panel-title">What we inspect</div>
              <div class="panel-subtitle">Signals behind the score</div>
            </div>
          </div>
          <div class="signal-list">
            <div v-for="signal in signals" :key="signal.title" class="signal-row">
              <span class="signal-icon"><el-icon><component :is="signal.icon" /></el-icon></span>
              <div>
                <strong>{{ signal.title }}</strong>
                <p>{{ signal.copy }}</p>
              </div>
            </div>
          </div>

          <div class="score-key">
            <div class="score-title">Reading the risk score</div>
            <div class="score-row">
              <span class="score-band low" /><span>0–34 · low risk</span>
            </div>
            <div class="score-row">
              <span class="score-band medium" /><span>35–69 · medium risk</span>
            </div>
            <div class="score-row">
              <span class="score-band high" /><span>70–100 · high risk</span>
            </div>
            <p>
              A high score means plain HTTP requests may be challenged. Prefer the Playwright
              worker, reuse sessions and slow down.
            </p>
          </div>
        </section>

        <section class="etiquette-card">
          <div class="etiquette-title">
            <el-icon><Connection /></el-icon>
            Scrape responsibly
          </div>
          <p>
            Respect robots.txt, rate limits and the target's terms of service. Diagnosis is for
            understanding defenses, not for bypassing access controls.
          </p>
        </section>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.doctor-badge {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 7px 13px;
  border-radius: 999px;
  border: 1px solid #c7d2fe;
  background: #eef2ff;
  color: #4338ca;
  font-size: 12px;
  font-weight: 650;
}

.doctor-hero {
  position: relative;
  overflow: hidden;
  background:
    radial-gradient(circle at 12% -20%, rgba(99, 102, 241, 0.12), transparent 48%),
    radial-gradient(circle at 95% 10%, rgba(6, 182, 212, 0.1), transparent 42%),
    #ffffff;
  margin-bottom: 16px;
}

.hero-glow {
  position: absolute;
  top: -140px;
  right: -80px;
  width: 320px;
  height: 320px;
  border-radius: 50%;
  background: #a5b4fc;
  filter: blur(110px);
  opacity: 0.3;
  pointer-events: none;
}

.hero-inner {
  position: relative;
  padding: 28px 28px 24px;
}

.hero-copy h3 {
  margin: 14px 0 0;
  font-size: 24px;
  letter-spacing: -0.03em;
}

.hero-copy p {
  margin: 9px 0 0;
  max-width: 680px;
  color: var(--ss-muted);
  font-size: 13.5px;
  line-height: 1.6;
}

.eyebrow {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 5px 11px;
  border-radius: 999px;
  border: 1px solid var(--ss-border);
  background: #ffffff;
  color: var(--ss-muted);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.05em;
  text-transform: uppercase;
}

.pulse {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #10b981;
  box-shadow: 0 0 0 3px rgba(16, 185, 129, 0.14);
}

.url-form {
  display: flex;
  gap: 10px;
  margin-top: 22px;
  max-width: 820px;
}

.url-input {
  flex: 1;
}

.url-input :deep(.el-input__wrapper) {
  border-radius: 12px;
}

.diagnose-button {
  height: 40px;
  padding: 0 22px;
  flex-shrink: 0;
}

.examples {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 16px;
}

.examples-label {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--ss-muted);
  margin-right: 3px;
}

.example-chip {
  display: inline-flex;
  align-items: baseline;
  gap: 6px;
  padding: 6px 11px;
  border: 1px solid var(--ss-border);
  border-radius: 999px;
  background: #ffffff;
  color: var(--ss-text);
  cursor: pointer;
  transition: all 0.14s ease;
}

.example-chip strong {
  font-size: 11.5px;
  font-weight: 650;
}

.example-chip small {
  font-size: 10.5px;
  color: var(--ss-muted);
}

.example-chip:hover {
  border-color: #c7d2fe;
  background: #f8faff;
  transform: translateY(-1px);
}

.doctor-error {
  margin-bottom: 16px;
}

.doctor-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.6fr) minmax(300px, 1fr);
  gap: 16px;
  align-items: start;
}

.report-panel {
  overflow: hidden;
}

.report-body {
  padding: 20px 22px 24px;
  min-height: 360px;
}

.side-column {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.recent-list {
  padding: 8px;
}

.recent-row {
  display: flex;
  align-items: center;
  gap: 11px;
  width: 100%;
  padding: 10px;
  border: 0;
  border-radius: 10px;
  background: transparent;
  text-align: left;
  cursor: pointer;
}

.recent-row:hover {
  background: #f8faff;
}

.recent-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

.recent-dot.low {
  background: #10b981;
}

.recent-dot.medium {
  background: #f59e0b;
}

.recent-dot.high {
  background: #ef4444;
}

.recent-copy {
  display: flex;
  flex-direction: column;
  min-width: 0;
  flex: 1;
}

.recent-copy strong {
  font-size: 12.5px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.recent-copy small {
  color: var(--ss-muted);
  font-size: 10.5px;
  margin-top: 2px;
}

.recent-score {
  font-size: 12px;
  font-weight: 750;
  font-variant-numeric: tabular-nums;
}

.recent-score.low {
  color: #047857;
}

.recent-score.medium {
  color: #b45309;
}

.recent-score.high {
  color: #b91c1c;
}

.recent-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 30px 20px;
  text-align: center;
  color: var(--ss-muted);
}

.recent-empty .el-icon {
  font-size: 22px;
  color: #cbd5e1;
}

.recent-empty p {
  margin: 0;
  font-size: 11.5px;
  line-height: 1.5;
  max-width: 220px;
}

.signal-list {
  display: flex;
  flex-direction: column;
  gap: 15px;
  padding: 18px 20px;
}

.signal-row {
  display: flex;
  gap: 11px;
  align-items: flex-start;
}

.signal-icon {
  width: 32px;
  height: 32px;
  border-radius: 9px;
  display: grid;
  place-items: center;
  background: #eef2ff;
  color: #4f46e5;
  flex-shrink: 0;
  font-size: 15px;
}

.signal-row strong {
  font-size: 12.5px;
}

.signal-row p {
  margin: 3px 0 0;
  color: var(--ss-muted);
  font-size: 11.5px;
  line-height: 1.5;
}

.score-key {
  margin: 0 20px 20px;
  padding: 15px;
  border-radius: 12px;
  background: #f8fafc;
  border: 1px solid var(--ss-border);
}

.score-title {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.05em;
  text-transform: uppercase;
  color: var(--ss-muted);
  margin-bottom: 10px;
}

.score-row {
  display: flex;
  align-items: center;
  gap: 9px;
  font-size: 11.5px;
  color: #475569;
  margin-bottom: 7px;
}

.score-band {
  width: 22px;
  height: 6px;
  border-radius: 999px;
}

.score-band.low {
  background: #10b981;
}

.score-band.medium {
  background: #f59e0b;
}

.score-band.high {
  background: #ef4444;
}

.score-key p {
  margin: 11px 0 0;
  font-size: 11px;
  line-height: 1.55;
  color: var(--ss-muted);
}

.etiquette-card {
  padding: 17px 19px;
  border-radius: 14px;
  background: #0f172a;
  color: #cbd5e1;
}

.etiquette-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  font-weight: 700;
  color: #e2e8f0;
}

.etiquette-card p {
  margin: 9px 0 0;
  font-size: 11.5px;
  line-height: 1.6;
  color: #8497b3;
}

@media (max-width: 1100px) {
  .doctor-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media (max-width: 640px) {
  .url-form {
    flex-direction: column;
  }

  .diagnose-button {
    width: 100%;
  }

  .hero-inner {
    padding: 22px 18px;
  }

  .report-body {
    padding: 14px;
  }
}
</style>
