import Fastify from 'fastify';
import cors from '@fastify/cors';
import fastifyStatic from '@fastify/static';
import { randomUUID } from 'node:crypto';
import { mkdirSync } from 'node:fs';
import path from 'node:path';
import type { BrowserContext, Page } from 'playwright';

import { CHROME_USER_AGENT, config } from './config.js';
import { closeBrowser, getBrowser, getLaunchMode } from './browser.js';
import { detectBlocked } from './block.js';
import { assertHttpUrl, diagnose } from './diagnose.js';
import { extract, type PreviewField } from './extract.js';
import { pickAt } from './pick.js';

interface Session {
  page: Page;
  context: BrowserContext;
  createdAt: number;
  lastUsedAt: number;
  url: string;
  file: string;
}

const sessions = new Map<string, Session>();
const screenshotDir = path.resolve(config.screenshotDir);
mkdirSync(screenshotDir, { recursive: true });

const app = Fastify({
  logger: {
    level: process.env.LOG_LEVEL ?? 'info',
    transport:
      process.env.NODE_ENV === 'production'
        ? undefined
        : {
            target: 'pino-pretty',
            options: { translateTime: 'HH:MM:ss', ignore: 'pid,hostname' },
          },
  },
  bodyLimit: 2 * 1024 * 1024,
});

await app.register(cors, { origin: true });
await app.register(fastifyStatic, {
  root: screenshotDir,
  prefix: '/screenshots/',
  decorateReply: false,
});

app.addHook('onRequest', async (request, reply) => {
  if (request.url === '/api/health' || request.url.startsWith('/screenshots/')) {
    return;
  }
  if (request.headers['x-worker-token'] !== config.token) {
    await reply.code(401).send({ error: 'Invalid worker token' });
  }
});

app.get('/', async () => ({
  name: 'ScrapeStudio Worker',
  version: '0.1.0',
  endpoints: ['/api/health', '/api/render', '/api/pick', '/api/preview', '/api/diagnose'],
}));

app.get('/api/health', async () => ({
  ok: true,
  service: 'scrape-studio-worker',
  launchMode: getLaunchMode(),
  sessions: sessions.size,
  uptimeSeconds: Math.round(process.uptime()),
}));

app.post<{ Body: { url?: string; width?: number; height?: number } }>(
  '/api/render',
  async (request, reply) => {
    const url = assertHttpUrl(request.body?.url);
    const width = Math.min(Math.max(Number(request.body?.width ?? 1280), 320), 2400);
    const height = Math.min(Math.max(Number(request.body?.height ?? 900), 240), 1600);

    const browser = await getBrowser();
    const context = await browser.newContext({
      viewport: { width, height },
      userAgent: CHROME_USER_AGENT,
      locale: 'en-US',
      deviceScaleFactor: 1,
    });

    const page = await context.newPage();
    await page.addInitScript('globalThis.__name = globalThis.__name || (fn => fn);');
    let status = 0;
    let finalUrl = url;

    try {
      const response = await page.goto(url, {
        waitUntil: 'domcontentloaded',
        timeout: config.navigationTimeoutMs,
      });
      status = response?.status() ?? 0;
      finalUrl = page.url();
      await page.waitForTimeout(500);
    } catch (error) {
      await context.close();
      throw Object.assign(
        new Error(
          `Could not render ${url}: ${error instanceof Error ? error.message : String(error)}`
        ),
        { statusCode: 502 }
      );
    }

    const title = await page.title();
    const html = await page.content();
    const blockReason = detectBlocked(status, title, html);

    const sessionId = randomUUID();
    const file = `${sessionId}.png`;
    await page.screenshot({ path: path.join(screenshotDir, file) });

    const now = Date.now();
    sessions.set(sessionId, {
      page,
      context,
      createdAt: now,
      lastUsedAt: now,
      url: finalUrl,
      file,
    });

    return {
      sessionId,
      screenshotUrl: `${config.publicBaseUrl}/screenshots/${file}`,
      width,
      height,
      title,
      finalUrl,
      status,
      blocked: Boolean(blockReason),
      blockReason,
    };
  }
);

app.post<{ Body: { sessionId?: string; x?: number; y?: number } }>(
  '/api/pick',
  async (request, reply) => {
    const sessionId = request.body?.sessionId;
    const x = Number(request.body?.x);
    const y = Number(request.body?.y);

    if (!sessionId || !Number.isFinite(x) || !Number.isFinite(y)) {
      return reply.code(400).send({ error: 'sessionId, x and y are required' });
    }

    const session = sessions.get(sessionId);
    if (!session) {
      return reply.code(404).send({ error: 'Render session not found or expired' });
    }

    session.lastUsedAt = Date.now();
    return pickAt(session.page, x, y);
  }
);

app.post<{
  Body: {
    url?: string;
    itemSelector?: string;
    itemSelectorType?: string;
    fields?: PreviewField[];
    limit?: number;
  };
}>('/api/preview', async (request) => {
  const url = assertHttpUrl(request.body?.url);
  const fields = Array.isArray(request.body?.fields) ? request.body.fields : [];
  const limit = Math.min(Math.max(Number(request.body?.limit ?? 50), 1), config.maxPreviewRows);

  const browser = await getBrowser();
  const context = await browser.newContext({
    viewport: { width: 1366, height: 900 },
    userAgent: CHROME_USER_AGENT,
    locale: 'en-US',
  });
  const page = await context.newPage();
  await page.addInitScript('globalThis.__name = globalThis.__name || (fn => fn);');

  try {
    let status = 0;
    try {
      const response = await page.goto(url, {
        waitUntil: 'domcontentloaded',
        timeout: config.navigationTimeoutMs,
      });
      status = response?.status() ?? 0;
      await page.waitForTimeout(400);
    } catch (error) {
      throw Object.assign(
        new Error(
          `Could not load ${url}: ${error instanceof Error ? error.message : String(error)}`
        ),
        { statusCode: 502 }
      );
    }

    const title = await page.title();
    const html = await page.content();
    const blockReason = detectBlocked(status, title, html);

    const extraction = await extract(
      page,
      request.body?.itemSelector ?? null,
      request.body?.itemSelectorType ?? null,
      fields,
      limit
    );

    return {
      rows: extraction.rows,
      count: extraction.count,
      pageTitle: title,
      blocked: Boolean(blockReason),
      blockReason,
      warnings:
        extraction.totalMatches > extraction.count
          ? [`Showing the first ${extraction.count} of ${extraction.totalMatches} items.`]
          : [],
    };
  } finally {
    await context.close();
  }
});

app.post<{ Body: { url?: string } }>('/api/diagnose', async (request) => {
  return diagnose(String(request.body?.url ?? ''));
});

app.setErrorHandler((error, request, reply) => {
  request.log.warn({ err: error }, 'Worker request failed');
  const statusCode =
    typeof (error as { statusCode?: unknown }).statusCode === 'number'
      ? (error as { statusCode: number }).statusCode
      : 500;
  return reply.code(statusCode).send({
    error: error instanceof Error ? error.message : 'Worker error',
  });
});

const cleanup = setInterval(() => {
  const now = Date.now();
  for (const [sessionId, session] of sessions) {
    if (now - session.lastUsedAt > config.sessionTtlMs) {
      sessions.delete(sessionId);
      void session.context.close().catch(() => undefined);
      app.log.info({ sessionId }, 'Closed expired render session');
    }
  }
}, 60_000);
cleanup.unref();

async function shutdown(signal: string): Promise<void> {
  app.log.info({ signal }, 'Shutting down worker');
  clearInterval(cleanup);
  for (const session of sessions.values()) {
    await session.context.close().catch(() => undefined);
  }
  sessions.clear();
  await closeBrowser();
  await app.close();
  process.exit(0);
}

process.on('SIGINT', () => void shutdown('SIGINT'));
process.on('SIGTERM', () => void shutdown('SIGTERM'));

try {
  await app.listen({ host: config.host, port: config.port });
  app.log.info(
    `ScrapeStudio worker listening on http://${config.host}:${config.port} (base: ${config.publicBaseUrl})`
  );
} catch (error) {
  app.log.error(error);
  process.exit(1);
}
