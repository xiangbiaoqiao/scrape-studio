import type {
  AuthResponse,
  CodeResponse,
  CreateProjectInput,
  DashboardSummary,
  DiagnosisReport,
  FieldDto,
  FieldInput,
  PageResponse,
  PickResponse,
  PreviewFieldInput,
  PreviewResponse,
  ProjectDetail,
  ProjectSummary,
  RenderResponse,
  RunDetail,
  RunSummary,
  UpdateProjectInput,
  User,
} from './types';

const BASE = import.meta.env.VITE_API_BASE ?? '/api';
const TOKEN_KEY = 'scrape-studio.token';
const USER_KEY = 'scrape-studio.user';

export class ApiError extends Error {
  status: number;
  fieldErrors?: Record<string, string>;

  constructor(status: number, message: string, fieldErrors?: Record<string, string>) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.fieldErrors = fieldErrors;
  }
}

export function getStoredToken(): string {
  return localStorage.getItem(TOKEN_KEY) ?? '';
}

export function storeSession(token: string, user: User): void {
  localStorage.setItem(TOKEN_KEY, token);
  localStorage.setItem(USER_KEY, JSON.stringify(user));
}

export function clearSession(): void {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(USER_KEY);
}

export function getStoredUser(): User | null {
  const raw = localStorage.getItem(USER_KEY);
  if (!raw) {
    return null;
  }
  try {
    return JSON.parse(raw) as User;
  } catch {
    return null;
  }
}

interface RequestOptions {
  method?: string;
  body?: unknown;
  query?: Record<string, string | number | boolean | null | undefined>;
}

async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const url = new URL(`${BASE}${path}`, window.location.origin);
  if (options.query) {
    for (const [key, value] of Object.entries(options.query)) {
      if (value !== undefined && value !== null && value !== '') {
        url.searchParams.set(key, String(value));
      }
    }
  }

  const headers = new Headers();
  const token = getStoredToken();
  if (token) {
    headers.set('Authorization', `Bearer ${token}`);
  }
  if (options.body !== undefined) {
    headers.set('Content-Type', 'application/json');
  }

  let response: Response;
  try {
    response = await fetch(url.toString(), {
      method: options.method ?? 'GET',
      headers,
      body: options.body !== undefined ? JSON.stringify(options.body) : undefined,
    });
  } catch {
    throw new ApiError(0, 'Cannot reach the API. Is the backend running on port 8080?');
  }

  if (response.status === 204) {
    return undefined as T;
  }

  const text = await response.text();
  let payload: unknown = null;
  if (text) {
    try {
      payload = JSON.parse(text);
    } catch {
      payload = null;
    }
  }

  if (!response.ok) {
    const problem = payload as {
      detail?: string;
      title?: string;
      message?: string;
      error?: string;
      errors?: Record<string, string>;
    } | null;
    const message =
      problem?.detail ??
      problem?.message ??
      problem?.error ??
      problem?.title ??
      `Request failed with HTTP ${response.status}`;
    throw new ApiError(response.status, message, problem?.errors);
  }

  return payload as T;
}

export const api = {
  login(email: string, password: string) {
    return request<AuthResponse>('/auth/login', {
      method: 'POST',
      body: { email, password },
    });
  },

  register(email: string, password: string, displayName: string) {
    return request<AuthResponse>('/auth/register', {
      method: 'POST',
      body: { email, password, displayName },
    });
  },

  me() {
    return request<User>('/auth/me');
  },

  dashboard() {
    return request<DashboardSummary>('/dashboard/summary');
  },

  listProjects(query = '', page = 0, size = 24) {
    return request<PageResponse<ProjectSummary>>('/projects', {
      query: { query, page, size },
    });
  },

  getProject(id: number) {
    return request<ProjectDetail>(`/projects/${id}`);
  },

  createProject(input: CreateProjectInput) {
    return request<ProjectDetail>('/projects', { method: 'POST', body: input });
  },

  updateProject(id: number, input: UpdateProjectInput) {
    return request<ProjectDetail>(`/projects/${id}`, { method: 'PUT', body: input });
  },

  deleteProject(id: number) {
    return request<void>(`/projects/${id}`, { method: 'DELETE' });
  },

  addField(projectId: number, input: FieldInput) {
    return request<FieldDto>(`/projects/${projectId}/fields`, {
      method: 'POST',
      body: input,
    });
  },

  updateField(projectId: number, fieldId: number, input: FieldInput) {
    return request<FieldDto>(`/projects/${projectId}/fields/${fieldId}`, {
      method: 'PUT',
      body: input,
    });
  },

  deleteField(projectId: number, fieldId: number) {
    return request<void>(`/projects/${projectId}/fields/${fieldId}`, {
      method: 'DELETE',
    });
  },

  renderProject(id: number, url?: string) {
    return request<RenderResponse>(`/projects/${id}/render`, {
      method: 'POST',
      body: { url },
    });
  },

  pickElement(id: number, sessionId: string, x: number, y: number) {
    return request<PickResponse>(`/projects/${id}/pick`, {
      method: 'POST',
      body: { sessionId, x, y },
    });
  },

  previewProject(
    id: number,
    payload: {
      url?: string;
      itemSelector?: string;
      itemSelectorType?: string;
      fields?: PreviewFieldInput[];
      limit?: number;
    }
  ) {
    return request<PreviewResponse>(`/projects/${id}/preview`, {
      method: 'POST',
      body: payload,
    });
  },

  diagnoseProject(id: number, url?: string) {
    return request<DiagnosisReport>(`/projects/${id}/diagnose`, {
      method: 'POST',
      body: { url },
    });
  },

  generateCode(id: number, language: string) {
    return request<CodeResponse>(`/projects/${id}/code`, { query: { language } });
  },

  listRuns(params: {
    projectId?: number | null;
    status?: string;
    page?: number;
    size?: number;
  } = {}) {
    return request<PageResponse<RunSummary>>('/runs', {
      query: {
        projectId: params.projectId ?? undefined,
        status: params.status,
        page: params.page ?? 0,
        size: params.size ?? 20,
      },
    });
  },

  listProjectRuns(projectId: number, page = 0, size = 10, status?: string) {
    return request<PageResponse<RunSummary>>(`/projects/${projectId}/runs`, {
      query: { page, size, status },
    });
  },

  createRun(projectId: number) {
    return request<RunDetail>(`/projects/${projectId}/runs`, { method: 'POST' });
  },

  getRun(id: number) {
    return request<RunDetail>(`/runs/${id}`);
  },

  deleteRun(id: number) {
    return request<void>(`/runs/${id}`, { method: 'DELETE' });
  },

  diagnoseRun(id: number) {
    return request<DiagnosisReport>(`/runs/${id}/diagnose`, { method: 'POST' });
  },

  diagnoseUrl(url: string) {
    return request<DiagnosisReport>('/doctor/diagnose', {
      method: 'POST',
      body: { url },
    });
  },

  async exportRun(id: number, format: 'csv' | 'json', fallbackName = 'export'): Promise<void> {
    const token = getStoredToken();
    const response = await fetch(`${BASE}/runs/${id}/export?format=${format}`, {
      headers: token ? { Authorization: `Bearer ${token}` } : undefined,
    });
    if (!response.ok) {
      let message = `Export failed with HTTP ${response.status}`;
      try {
        const problem = (await response.json()) as { detail?: string };
        if (problem.detail) {
          message = problem.detail;
        }
      } catch {
        // Keep the fallback message.
      }
      throw new ApiError(response.status, message);
    }

    const blob = await response.blob();
    const disposition = response.headers.get('Content-Disposition') ?? '';
    const match = /filename="([^"]+)"/i.exec(disposition);
    const filename = match?.[1] ?? `${fallbackName}.${format}`;
    const objectUrl = URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = objectUrl;
    anchor.download = filename;
    document.body.appendChild(anchor);
    anchor.click();
    anchor.remove();
    URL.revokeObjectURL(objectUrl);
  },
};

export { TOKEN_KEY, USER_KEY };
