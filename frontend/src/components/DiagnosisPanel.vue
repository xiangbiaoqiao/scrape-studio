<script setup lang="ts">
import { computed } from 'vue';
import {
  CircleCheckFilled,
  CircleCloseFilled,
  InfoFilled,
  MagicStick,
  WarningFilled,
} from '@element-plus/icons-vue';
import type { DiagnosisReport } from '@/api/types';
import { formatDateTime, formatDuration } from '@/utils/format';

const props = defineProps<{
  report: DiagnosisReport | null;
  loading?: boolean;
  emptyText?: string;
}>();

const verdictLabel = computed(() => {
  const verdict = props.report?.verdict ?? '';
  return verdict.replace('-risk', '').replace(/^\w/, (char) => char.toUpperCase());
});

const verdictTone = computed(() => {
  switch (props.report?.verdict) {
    case 'low-risk':
      return 'low';
    case 'medium-risk':
      return 'medium';
    case 'high-risk':
      return 'high';
    default:
      return 'unknown';
  }
});

const scoreColor = computed(() => {
  const score = props.report?.riskScore ?? 0;
  if (score < 35) {
    return '#10b981';
  }
  if (score < 70) {
    return '#f59e0b';
  }
  return '#ef4444';
});

function signalIcon(status: string) {
  switch (status) {
    case 'pass':
      return CircleCheckFilled;
    case 'warn':
      return WarningFilled;
    case 'fail':
      return CircleCloseFilled;
    default:
      return InfoFilled;
  }
}

function formatScore(): string {
  return String(props.report?.riskScore ?? 0);
}
</script>

<template>
  <div v-if="loading" class="diagnosis-loading">
    <el-icon class="is-loading"><MagicStick /></el-icon>
    <span>Fingerprinting the target…</span>
  </div>

  <div v-else-if="!report" class="empty-state">
    <div class="empty-icon"><el-icon><MagicStick /></el-icon></div>
    <h3>No diagnosis yet</h3>
    <p>
      {{
        emptyText ??
        'Run the doctor to inspect TLS, HTTP/2, CDN headers, cookies and challenge markers for this target.'
      }}
    </p>
    <slot name="action" />
  </div>

  <div v-else class="diagnosis fade-in">
    <div class="diagnosis-head">
      <div class="score-ring">
        <el-progress
          type="circle"
          :percentage="Math.min(Math.max(report.riskScore, 0), 100)"
          :width="112"
          :stroke-width="9"
          :color="scoreColor"
          :format="formatScore"
        />
        <span class="score-caption">risk score</span>
      </div>

      <div class="verdict-block">
        <div class="verdict-line">
          <span class="verdict-pill" :class="verdictTone">{{ verdictLabel }}</span>
          <span class="muted tiny">HTTP {{ report.httpStatus ?? '—' }}</span>
          <span v-if="report.durationMs" class="muted tiny">
            · {{ formatDuration(report.durationMs) }}
          </span>
        </div>
        <h3>{{ report.target ?? report.finalUrl }}</h3>
        <p>{{ report.summary }}</p>
        <div class="head-meta tiny muted">
          <span>Checked {{ formatDateTime(report.checkedAt) }}</span>
          <span v-if="report.server">· Server: {{ report.server }}</span>
          <span v-if="report.tls?.protocol">· {{ report.tls.protocol }}</span>
          <span v-if="report.tls?.alpn">· ALPN {{ report.tls.alpn }}</span>
        </div>
      </div>

      <div class="head-actions">
        <slot name="action" />
      </div>
    </div>

    <div class="diagnosis-grid">
      <section class="signals">
        <h4>Signals</h4>
        <ul>
          <li v-for="signal in report.signals" :key="signal.name" :class="signal.status">
            <el-icon><component :is="signalIcon(signal.status)" /></el-icon>
            <div>
              <strong>{{ signal.name }}</strong>
              <p>{{ signal.detail }}</p>
            </div>
          </li>
        </ul>
      </section>

      <section class="recommendations">
        <h4>Recommended next steps</h4>
        <ol>
          <li v-for="item in report.recommendations" :key="item">{{ item }}</li>
        </ol>

        <div v-if="report.tls" class="tls-card">
          <div class="tls-title">TLS handshake</div>
          <div class="kv">
            <dt>Protocol</dt>
            <dd>{{ report.tls.protocol ?? 'unknown' }}</dd>
            <dt>Cipher</dt>
            <dd>{{ report.tls.cipher ?? 'unknown' }}</dd>
            <dt>ALPN</dt>
            <dd>{{ report.tls.alpn ?? 'none' }}</dd>
            <dt>Issuer</dt>
            <dd>{{ report.tls.issuer ?? 'unknown' }}</dd>
            <dt>Valid until</dt>
            <dd>{{ report.tls.validTo ?? 'unknown' }}</dd>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.diagnosis-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 64px;
  color: var(--ss-muted);
  font-size: 14px;
}

.diagnosis {
  padding: 4px;
}

.diagnosis-head {
  display: flex;
  align-items: flex-start;
  gap: 24px;
  padding: 18px 8px 24px;
}

.score-ring {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
}

.score-caption {
  font-size: 11px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--ss-muted);
  font-weight: 600;
}

.verdict-block {
  flex: 1;
  min-width: 0;
}

.verdict-line {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.verdict-pill {
  display: inline-flex;
  align-items: center;
  padding: 4px 11px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.02em;
}

.verdict-pill.low {
  background: #ecfdf5;
  color: #047857;
}

.verdict-pill.medium {
  background: #fffbeb;
  color: #b45309;
}

.verdict-pill.high {
  background: #fef2f2;
  color: #b91c1c;
}

.verdict-pill.unknown {
  background: #f1f5f9;
  color: #475569;
}

.verdict-block h3 {
  margin: 12px 0 6px;
  font-size: 19px;
  letter-spacing: -0.02em;
  overflow-wrap: anywhere;
}

.verdict-block p {
  margin: 0;
  color: var(--ss-muted);
  font-size: 13.5px;
  line-height: 1.6;
}

.head-meta {
  margin-top: 10px;
  display: flex;
  gap: 5px;
  flex-wrap: wrap;
}

.head-actions {
  margin-left: auto;
}

.diagnosis-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.25fr) minmax(0, 1fr);
  gap: 26px;
  border-top: 1px solid var(--ss-border);
  padding-top: 22px;
}

.signals h4,
.recommendations h4 {
  margin: 0 0 14px;
  font-size: 13px;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: var(--ss-muted);
}

.signals ul {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.signals li {
  display: flex;
  gap: 10px;
  padding: 12px 14px;
  border: 1px solid var(--ss-border);
  border-radius: 12px;
  background: #fbfcfe;
}

.signals li .el-icon {
  margin-top: 2px;
  font-size: 17px;
  flex-shrink: 0;
}

.signals li.pass .el-icon {
  color: var(--ss-success);
}

.signals li.warn .el-icon {
  color: var(--ss-warning);
}

.signals li.fail .el-icon {
  color: var(--ss-danger);
}

.signals li.info .el-icon {
  color: #0ea5e9;
}

.signals strong {
  font-size: 13px;
}

.signals p {
  margin: 3px 0 0;
  font-size: 12.5px;
  line-height: 1.5;
  color: var(--ss-muted);
}

.recommendations ol {
  margin: 0;
  padding-left: 18px;
  display: flex;
  flex-direction: column;
  gap: 9px;
  font-size: 13px;
  line-height: 1.55;
}

.tls-card {
  margin-top: 22px;
  padding: 16px;
  border-radius: 12px;
  background: #f8fafc;
  border: 1px solid var(--ss-border);
}

.tls-title {
  font-weight: 700;
  font-size: 13px;
  margin-bottom: 12px;
}

.tls-card .kv {
  grid-template-columns: 84px minmax(0, 1fr);
  font-size: 12.5px;
}

@media (max-width: 900px) {
  .diagnosis-head {
    flex-direction: column;
    align-items: stretch;
  }

  .head-actions {
    margin-left: 0;
  }

  .diagnosis-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
