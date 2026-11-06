import tls from 'node:tls';
import { BROWSER_HEADERS } from './config.js';
import { detectBlocked } from './block.js';

type SignalStatus = 'pass' | 'warn' | 'fail' | 'info';

interface Signal {
  name: string;
  status: SignalStatus;
  detail: string;
}

interface TlsInfo {
  protocol: string | null;
  cipher: string | null;
  alpn: string | null;
  authorized: boolean;
  issuer: string | null;
  validTo: string | null;
  error?: string;
}

export interface DiagnosisReport {
  target: string;
  finalUrl: string;
  checkedAt: string;
  durationMs: number;
  httpStatus: number;
  verdict: 'low-risk' | 'medium-risk' | 'high-risk' | 'unreachable';
  riskScore: number;
  summary: string;
  server: string | null;
  tls: TlsInfo | null;
  signals: Signal[];
  recommendations: string[];
}

export function assertHttpUrl(value: unknown): string {
  if (typeof value !== 'string' || value.trim().length === 0) {
    throw Object.assign(new Error('url is required'), { statusCode: 400 });
  }
  let url: URL;
  try {
    url = new URL(value.trim());
  } catch {
    throw Object.assign(new Error('Invalid URL'), { statusCode: 400 });
  }
  if (url.protocol !== 'http:' && url.protocol !== 'https:') {
    throw Object.assign(new Error('Only http:// and https:// URLs are supported'), { statusCode: 400 });
  }
  return url.toString();
}

function inspectTls(url: string): Promise<TlsInfo | null> {
  let target: URL;
  try {
    target = new URL(url);
  } catch {
    return Promise.resolve(null);
  }
  if (target.protocol !== 'https:') {
    return Promise.resolve(null);
  }

  const host = target.hostname;
  const port = Number(target.port || 443);

  return new Promise((resolve) => {
    let finished = false;
    const finish = (value: TlsInfo | null) => {
      if (finished) {
        return;
      }
      finished = true;
      resolve(value);
    };

    const socket = tls.connect(
      {
        host,
        port,
        servername: host,
        rejectUnauthorized: false,
        ALPNProtocols: ['h2', 'http/1.1'],
      },
      () => {
        const certificate = socket.getPeerCertificate();
        const cipher = socket.getCipher();
        const issuerValue = certificate?.issuer?.O ?? certificate?.issuer?.CN ?? null;
        const issuer = Array.isArray(issuerValue) ? issuerValue[0] ?? null : issuerValue;
        finish({
          protocol: socket.getProtocol(),
          cipher: cipher?.name ?? null,
          alpn: typeof socket.alpnProtocol === 'string' ? socket.alpnProtocol : null,
          authorized: socket.authorized,
          issuer,
          validTo: certificate?.valid_to ?? null,
        });
        socket.end();
      }
    );

    socket.setTimeout(7000, () => {
      socket.destroy();
      finish({
        protocol: null,
        cipher: null,
        alpn: null,
        authorized: false,
        issuer: null,
        validTo: null,
        error: 'TLS handshake timed out',
      });
    });
    socket.on('error', (error) => {
      finish({
        protocol: null,
        cipher: null,
        alpn: null,
        authorized: false,
        issuer: null,
        validTo: null,
        error: error.message,
      });
    });
  });
}

function providerFromHeaders(headers: Record<string, string>): string | null {
  if (headers['cf-ray'] || headers['server']?.includes('cloudflare')) {
    return 'Cloudflare';
  }
  if (headers['x-akamai-transformed'] || headers['akamai-grn'] || headers['server']?.includes('Akamai')) {
    return 'Akamai';
  }
  if (headers['x-sucuri-id']) {
    return 'Sucuri';
  }
  if (headers['x-amz-cf-id']) {
    return 'Amazon CloudFront';
  }
  if (headers['x-fastly-request-id']) {
    return 'Fastly';
  }
  return null;
}

async function robotsStatus(origin: string): Promise<{ status: number | null; detail: string }> {
  try {
    const response = await fetch(`${origin}/robots.txt`, {
      headers: { 'User-Agent': BROWSER_HEADERS['User-Agent'], Accept: 'text/plain,*/*' },
      redirect: 'follow',
      signal: AbortSignal.timeout(6000),
    });
    if (response.status === 200) {
      const body = (await response.text()).slice(0, 20_000);
      const disallowAll = /^\s*Disallow:\s*\/\s*$/im.test(body);
      return {
        status: response.status,
        detail: disallowAll
          ? 'robots.txt disallows all crawling for generic user-agents.'
          : 'robots.txt is available. Review it before scheduling production runs.',
      };
    }
    return { status: response.status, detail: `robots.txt returned HTTP ${response.status}.` };
  } catch {
    return { status: null, detail: 'robots.txt could not be loaded.' };
  }
}

export async function diagnose(rawUrl: string): Promise<DiagnosisReport> {
  const target = assertHttpUrl(rawUrl);
  const startedAt = Date.now();
  const signals: Signal[] = [];

  let status = 0;
  let finalUrl = target;
  let headers: Record<string, string> = {};
  let body = '';

  try {
    const response = await fetch(target, {
      headers: BROWSER_HEADERS,
      redirect: 'follow',
      signal: AbortSignal.timeout(20_000),
    });
    status = response.status;
    finalUrl = response.url || target;
    response.headers.forEach((value, key) => {
      headers[key.toLowerCase()] = value;
    });
    body = (await response.text()).slice(0, 250_000);
  } catch (error) {
    return {
      target,
      finalUrl,
      checkedAt: new Date().toISOString(),
      durationMs: Date.now() - startedAt,
      httpStatus: 0,
      verdict: 'unreachable',
      riskScore: 100,
      summary: `The target could not be reached: ${error instanceof Error ? error.message : String(error)}`,
      server: null,
      tls: null,
      signals: [
        {
          name: 'Connectivity',
          status: 'fail',
          detail: error instanceof Error ? error.message : String(error),
        },
      ],
      recommendations: ['Check the URL, DNS and network access, then run the diagnosis again.'],
    };
  }

  const titleMatch = body.match(/<title[^>]*>([\s\S]*?)<\/title>/i);
  const title = titleMatch ? titleMatch[1].replace(/\s+/g, ' ').trim() : '';
  const blockReason = detectBlocked(status, title, body);
  const provider = providerFromHeaders(headers);
  const server = headers['server'] ?? null;
  const tls = await inspectTls(finalUrl);
  const robots = await robotsStatus(new URL(finalUrl).origin);

  signals.push({
    name: 'HTTP status',
    status: status >= 200 && status < 400 ? 'pass' : status === 403 || status === 429 ? 'fail' : 'warn',
    detail: `HTTP ${status}${status >= 300 && status < 400 ? ' (redirect)' : ''}.`,
  });

  signals.push({
    name: 'Challenge page',
    status: blockReason ? 'fail' : 'pass',
    detail: blockReason ?? 'No challenge markers were found in the returned HTML.',
  });

  signals.push({
    name: 'Edge / CDN',
    status: provider ? 'warn' : 'pass',
    detail: provider
      ? `${provider} is in front of the target${headers['cf-ray'] ? ` (ray ${headers['cf-ray']})` : ''}.`
      : `No well-known bot-management CDN headers were detected${server ? ` (server: ${server})` : ''}.`,
  });

  if (tls) {
    signals.push({
      name: 'TLS handshake',
      status: tls.error ? 'fail' : tls.protocol === 'TLSv1.3' ? 'pass' : 'warn',
      detail: tls.error
        ? tls.error
        : `${tls.protocol ?? 'unknown'} · ${tls.cipher ?? 'unknown cipher'} · ALPN ${tls.alpn ?? 'none'}`,
    });
    signals.push({
      name: 'HTTP/2',
      status: tls.alpn === 'h2' ? 'pass' : 'warn',
      detail: tls.alpn === 'h2' ? 'HTTP/2 negotiated via ALPN.' : 'HTTP/2 was not negotiated.',
    });
  } else if (target.startsWith('https://')) {
    signals.push({
      name: 'TLS handshake',
      status: 'fail',
      detail: 'The TLS handshake did not complete.',
    });
  }

  const cookieHeader = headers['set-cookie'] ?? '';
  const botCookies = ['cf_clearance', '__cf_bm', 'incap_ses', 'ak_bmsc', 'datadome'].filter((name) =>
    cookieHeader.toLowerCase().includes(name.toLowerCase())
  );
  if (botCookies.length > 0) {
    signals.push({
      name: 'Bot-management cookies',
      status: 'warn',
      detail: `The response issued ${botCookies.join(', ')}. A previous challenge may have been served.`,
    });
  }

  if (finalUrl !== target) {
    signals.push({
      name: 'Redirect chain',
      status: 'info',
      detail: `The target redirected to ${finalUrl}.`,
    });
  }

  signals.push({
    name: 'robots.txt',
    status: robots.status === 200 ? 'info' : 'pass',
    detail: robots.detail,
  });

  let riskScore = 6;
  if (status === 403 || status === 429 || status === 503) {
    riskScore += 35;
  } else if (status >= 400) {
    riskScore += 15;
  }
  if (blockReason) {
    riskScore += /captcha/i.test(blockReason) ? 28 : 30;
  }
  if (botCookies.length > 0) {
    riskScore += 8;
  }
  if (tls?.protocol === 'TLSv1.2') {
    riskScore += 5;
  }
  if (tls && tls.alpn !== 'h2') {
    riskScore += 5;
  }
  if (provider === 'Cloudflare' || provider === 'Akamai' || provider === 'DataDome') {
    riskScore += 3;
  }
  riskScore = Math.min(95, riskScore);

  const verdict: DiagnosisReport['verdict'] =
    riskScore < 35 ? 'low-risk' : riskScore < 70 ? 'medium-risk' : 'high-risk';

  const recommendations: string[] = [];
  if (blockReason) {
    recommendations.push(
      'Use the Playwright rendering worker instead of plain HTTP requests so the target sees a real browser session.'
    );
    recommendations.push('Warm up a session and reuse cookies before collecting at scale.');
  }
  if (status === 429) {
    recommendations.push('Honor Retry-After, add jitter between requests, and reduce concurrency.');
  }
  if (status === 403 && !blockReason) {
    recommendations.push('Retry with a browser context and a realistic header profile before escalating.');
  }
  if (tls && tls.protocol !== 'TLSv1.3') {
    recommendations.push('Prefer a client with a modern TLS stack so the handshake matches current browsers.');
  }
  if (recommendations.length === 0) {
    recommendations.push('Keep requests polite: respect robots.txt, rate limits and the site terms of service.');
  }

  const summary = blockReason
    ? `${blockReason}. The target is actively challenging automated traffic.`
    : status >= 400
      ? `The target answered with HTTP ${status}, but no challenge page was identified.`
      : `The target looks reachable. ${provider ? `${provider} is present, but no active challenge was detected.` : 'No active bot challenge was detected.'}`;

  return {
    target,
    finalUrl,
    checkedAt: new Date().toISOString(),
    durationMs: Date.now() - startedAt,
    httpStatus: status,
    verdict,
    riskScore,
    summary,
    server,
    tls,
    signals,
    recommendations,
  };
}
