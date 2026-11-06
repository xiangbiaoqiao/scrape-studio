export interface PageResponse<T> {
  items: T[];
  total: number;
  page: number;
  size: number;
}

export interface User {
  id: number;
  email: string;
  displayName: string;
  role: string;
  createdAt: string;
}

export interface AuthResponse {
  token: string;
  user: User;
}

export interface FieldDto {
  id: number;
  name: string;
  selector: string;
  selectorType: 'CSS' | 'XPATH' | string;
  attribute: string;
  multiple: boolean;
  orderIndex: number;
}

export interface ProjectSummary {
  id: number;
  name: string;
  url: string;
  description: string | null;
  itemSelector: string | null;
  scheduleEnabled: boolean;
  intervalMinutes: number;
  lastRunAt: string | null;
  nextRunAt: string | null;
  fieldCount: number;
  runCount: number;
  lastStatus: RunStatus | null;
  updatedAt: string;
}

export interface ProjectDetail {
  id: number;
  name: string;
  url: string;
  description: string | null;
  itemSelector: string | null;
  itemSelectorType: 'CSS' | 'XPATH' | string;
  scheduleEnabled: boolean;
  intervalMinutes: number;
  lastRunAt: string | null;
  nextRunAt: string | null;
  fields: FieldDto[];
  createdAt: string;
  updatedAt: string;
}

export type RunStatus = 'RUNNING' | 'SUCCESS' | 'FAILED' | 'BLOCKED';
export type RunTrigger = 'MANUAL' | 'SCHEDULE';

export interface RunSummary {
  id: number;
  projectId: number;
  projectName: string;
  status: RunStatus;
  triggerType: RunTrigger;
  startedAt: string;
  finishedAt: string | null;
  durationMs: number | null;
  itemCount: number;
  errorMessage: string | null;
}

export interface DiagnosisSignal {
  name: string;
  status: 'pass' | 'warn' | 'fail' | 'info';
  detail: string;
}

export interface TlsInfo {
  protocol: string | null;
  cipher: string | null;
  alpn: string | null;
  authorized: boolean;
  issuer: string | null;
  validTo: string | null;
  error?: string;
}

export interface DiagnosisReport {
  target?: string;
  finalUrl?: string;
  checkedAt?: string;
  durationMs?: number;
  httpStatus?: number;
  verdict: 'low-risk' | 'medium-risk' | 'high-risk' | 'unreachable' | string;
  riskScore: number;
  summary: string;
  server?: string | null;
  tls?: TlsInfo | null;
  signals: DiagnosisSignal[];
  recommendations: string[];
}

export interface RunDetail {
  id: number;
  projectId: number;
  projectName: string;
  status: RunStatus;
  triggerType: RunTrigger;
  startedAt: string;
  finishedAt: string | null;
  durationMs: number | null;
  itemCount: number;
  errorMessage: string | null;
  rows: Array<Record<string, unknown>>;
  diagnosis: DiagnosisReport | null;
  screenshotUrl: string | null;
}

export interface DashboardSummary {
  projectCount: number;
  runCount: number;
  successCount: number;
  failedCount: number;
  blockedCount: number;
  runsToday: number;
  successRate: number;
  itemsCollected: number;
  workerOnline: boolean;
  recentRuns: RunSummary[];
  activity: Array<{ date: string; total: number; failed: number }>;
  recentProjects: ProjectSummary[];
}

export interface RenderResponse {
  sessionId: string | null;
  screenshotUrl: string | null;
  width: number;
  height: number;
  title: string | null;
  finalUrl: string | null;
  status: number;
  blocked: boolean;
  blockReason: string | null;
}

export interface SelectorCandidate {
  selector: string;
  type: 'CSS' | 'XPATH' | string;
  matchCount: number;
  sample: string;
}

export interface PickResponse {
  itemSelector: string;
  itemSelectorType: string;
  itemMatchCount: number;
  fieldSelectors: SelectorCandidate[];
  element: {
    tag: string;
    text: string;
    html: string;
    preview: string;
  };
  itemPreview: string;
}

export interface PreviewFieldInput {
  name: string;
  selector: string;
  selectorType: string;
  attribute: string;
  multiple: boolean;
}

export interface PreviewResponse {
  rows: Array<Record<string, unknown>>;
  count: number;
  pageTitle: string | null;
  blocked: boolean;
  blockReason: string | null;
  warnings: string[];
}

export interface CodeResponse {
  language: string;
  code: string;
}

export interface CreateProjectInput {
  name: string;
  url: string;
  description?: string | null;
}

export interface UpdateProjectInput {
  name: string;
  url: string;
  description?: string | null;
  itemSelector?: string | null;
  itemSelectorType?: string;
  scheduleEnabled?: boolean;
  intervalMinutes?: number;
}

export interface FieldInput {
  name: string;
  selector: string;
  selectorType: string;
  attribute: string;
  multiple: boolean;
}
