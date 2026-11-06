import { chromium, type Browser } from 'playwright';

let browserPromise: Promise<Browser> | null = null;
let launchMode = 'default';

async function launch(): Promise<Browser> {
  const preferred = process.env.PLAYWRIGHT_CHANNEL?.trim();
  const attempts: Array<{ label: string; options: Parameters<typeof chromium.launch>[0] }> = [];

  if (preferred) {
    attempts.push({ label: preferred, options: { headless: true, channel: preferred } });
  }
  attempts.push({ label: 'bundled chromium', options: { headless: true } });
  attempts.push({ label: 'system chrome', options: { headless: true, channel: 'chrome' } });
  attempts.push({ label: 'system edge', options: { headless: true, channel: 'msedge' } });

  let lastError: unknown;
  for (const attempt of attempts) {
    try {
      const browser = await chromium.launch(attempt.options);
      launchMode = attempt.label;
      return browser;
    } catch (error) {
      lastError = error;
    }
  }

  throw new Error(
    'Could not launch Chromium. Run "npx playwright install chromium" or set PLAYWRIGHT_CHANNEL=chrome. ' +
      `Last error: ${lastError instanceof Error ? lastError.message : String(lastError)}`
  );
}

export function getBrowser(): Promise<Browser> {
  if (!browserPromise) {
    browserPromise = launch().catch((error) => {
      browserPromise = null;
      throw error;
    });
  }
  return browserPromise;
}

export function getLaunchMode(): string {
  return launchMode;
}

export async function closeBrowser(): Promise<void> {
  if (!browserPromise) {
    return;
  }
  try {
    const browser = await browserPromise;
    await browser.close();
  } catch {
    // Ignore shutdown failures.
  } finally {
    browserPromise = null;
  }
}
