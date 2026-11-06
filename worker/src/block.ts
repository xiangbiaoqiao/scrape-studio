export function detectBlocked(status: number, title: string, body: string): string | null {
  const sample = `${title}\n${body}`.slice(0, 400_000);

  if (/just a moment\.\.\./i.test(title) || /challenges\.cloudflare\.com/i.test(sample)) {
    return 'Cloudflare browser challenge detected';
  }
  if (/enable javascript and cookies to continue/i.test(sample)) {
    return 'JavaScript/cookie interstitial detected';
  }
  if (/attention required!\s*\|\s*cloudflare/i.test(title)) {
    return 'Cloudflare access-denied page detected';
  }
  if (
    /g-recaptcha|hcaptcha\.com|recaptcha\/api|cf-turnstile/i.test(sample) &&
    /captcha|verify you are human|are you a robot/i.test(sample)
  ) {
    return 'CAPTCHA challenge detected';
  }
  if (/access denied|request blocked/i.test(title)) {
    return `Access denied page detected (HTTP ${status})`;
  }
  if (status === 403) {
    return 'Target returned HTTP 403';
  }
  if (status === 429) {
    return 'Target returned HTTP 429 (rate limited)';
  }
  if (status === 503) {
    return 'Target returned HTTP 503 (service unavailable)';
  }
  return null;
}
