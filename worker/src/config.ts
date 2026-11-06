export const config = {
  host: process.env.HOST ?? '0.0.0.0',
  port: Number(process.env.PORT ?? 8787),
  token: process.env.WORKER_TOKEN ?? 'dev-worker-token',
  publicBaseUrl: (process.env.PUBLIC_BASE_URL ?? 'http://localhost:8787').replace(/\/$/, ''),
  screenshotDir: process.env.SCREENSHOT_DIR ?? '.data/screenshots',
  sessionTtlMs: Number(process.env.SESSION_TTL_MS ?? 10 * 60 * 1000),
  navigationTimeoutMs: Number(process.env.NAVIGATION_TIMEOUT_MS ?? 45_000),
  maxPreviewRows: 500,
} as const;

export const CHROME_USER_AGENT =
  'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 ' +
  '(KHTML, like Gecko) Chrome/139.0.0.0 Safari/537.36';

export const BROWSER_HEADERS: Record<string, string> = {
  'User-Agent': CHROME_USER_AGENT,
  Accept: 'text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8',
  'Accept-Language': 'en-US,en;q=0.9',
  'Accept-Encoding': 'gzip, deflate, br',
  'Upgrade-Insecure-Requests': '1',
  'Sec-Fetch-Dest': 'document',
  'Sec-Fetch-Mode': 'navigate',
  'Sec-Fetch-Site': 'none',
  'Sec-Fetch-User': '?1',
  'sec-ch-ua': '"Chromium";v="139", "Google Chrome";v="139", "Not.A/Brand";v="24"',
  'sec-ch-ua-mobile': '?0',
  'sec-ch-ua-platform': '"Windows"',
};
