<script setup lang="ts">
import { reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import type { FormInstance, FormRules } from 'element-plus';
import {
  Aim,
  ArrowRight,
  Connection,
  MagicStick,
  Timer,
} from '@element-plus/icons-vue';
import { useAuthStore } from '@/stores/auth';
import { ApiError } from '@/api/client';

const auth = useAuthStore();
const route = useRoute();
const router = useRouter();

const mode = ref<'login' | 'register'>('login');
const loading = ref(false);
const demoLoading = ref(false);
const formRef = ref<FormInstance>();
const form = reactive({
  email: '',
  password: '',
  displayName: '',
});

const rules: FormRules = {
  email: [
    { required: true, message: 'Enter your email', trigger: 'blur' },
    { type: 'email', message: 'That does not look like an email', trigger: 'blur' },
  ],
  password: [
    { required: true, message: 'Enter your password', trigger: 'blur' },
    { min: 8, message: 'Use at least 8 characters', trigger: 'blur' },
  ],
  displayName: [{ required: true, message: 'Tell us what to call you', trigger: 'blur' }],
};

const features = [
  {
    icon: Aim,
    title: 'Point, click, extract',
    copy: 'Select items directly on a rendered screenshot and generate stable selectors.',
  },
  {
    icon: MagicStick,
    title: 'Anti-bot doctor',
    copy: 'Inspect TLS, HTTP/2, CDN headers and challenge pages before scheduling.',
  },
  {
    icon: Connection,
    title: 'Portable generated code',
    copy: 'Export runnable Python, Java or Node scrapers that mirror the visual project.',
  },
];

function destination(): string {
  const redirect = route.query.redirect;
  return typeof redirect === 'string' && redirect.startsWith('/') ? redirect : '/';
}

async function submit(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false);
  if (!valid) {
    return;
  }

  loading.value = true;
  try {
    if (mode.value === 'login') {
      await auth.login(form.email.trim(), form.password);
    } else {
      await auth.register(form.email.trim(), form.password, form.displayName.trim());
    }
    ElMessage.success(mode.value === 'login' ? 'Welcome back' : 'Workspace created');
    await router.push(destination());
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : 'Authentication failed');
  } finally {
    loading.value = false;
  }
}

async function demoLogin(): Promise<void> {
  demoLoading.value = true;
  form.email = 'demo@scrapestudio.dev';
  form.password = 'demo1234';
  try {
    await auth.login(form.email, form.password);
    await router.push(destination());
  } catch (error) {
    ElMessage.error(
      error instanceof ApiError
        ? error.message
        : 'The demo account is unavailable. Register a new account instead.'
    );
  } finally {
    demoLoading.value = false;
  }
}

function switchMode(next: 'login' | 'register'): void {
  mode.value = next;
  formRef.value?.clearValidate();
}
</script>

<template>
  <div class="login-shell">
    <section class="promo">
      <div class="promo-grid" />
      <div class="promo-glow one" />
      <div class="promo-glow two" />

      <div class="promo-content">
        <router-link to="/" class="brand">
          <span class="brand-mark">
            <svg viewBox="0 0 64 64" aria-hidden="true">
              <rect width="64" height="64" rx="16" fill="#111c33" />
              <path
                d="M18 22h10l4 20h-4l-.8-4.5H18.8L18 42h-4l4-20Zm1.4 12h7.2l-1.3-7.2L19.4 34Z"
                fill="#a5b4fc"
              />
              <path
                d="M38 22h6.5c5 0 8 2.6 8 6.4 0 2.5-1.3 4.3-3.5 5.3l4.5 8.3h-4.6l-3.9-7.5H42V42h-4V22Zm4 3.4v5.4h2.3c2.5 0 3.8-.9 3.8-2.8 0-1.8-1.3-2.6-3.8-2.6H42Z"
                fill="#e2e8f0"
              />
            </svg>
          </span>
          <span class="brand-copy">
            <strong>ScrapeStudio</strong>
            <small>visual scraping workbench</small>
          </span>
        </router-link>

        <div class="hero-copy">
          <span class="eyebrow"><span class="dot" /> Open-source · self-hosted</span>
          <h1>Turn any page into a clean dataset.</h1>
          <p>
            A visual workbench for web scraping: pick fields on a live screenshot, preview rows,
            generate production-ready code and diagnose anti-bot defenses before you scale.
          </p>
        </div>

        <ul class="feature-list">
          <li v-for="feature in features" :key="feature.title">
            <span class="feature-icon"><el-icon><component :is="feature.icon" /></el-icon></span>
            <div>
              <strong>{{ feature.title }}</strong>
              <p>{{ feature.copy }}</p>
            </div>
          </li>
        </ul>

        <div class="promo-foot">
          <span><el-icon><Timer /></el-icon> Schedules &amp; run history</span>
          <span><el-icon><Connection /></el-icon> Spring Boot + Vue 3 + Playwright</span>
        </div>
      </div>
    </section>

    <section class="form-side">
      <div class="form-card fade-in">
        <div class="form-head">
          <h2>{{ mode === 'login' ? 'Welcome back' : 'Create your workspace' }}</h2>
          <p>
            {{
              mode === 'login'
                ? 'Sign in to manage projects, pick fields and inspect runs.'
                : 'Start with a free local account. No email verification required.'
            }}
          </p>
        </div>

        <div class="mode-switch">
          <button
            type="button"
            :class="{ active: mode === 'login' }"
            @click="switchMode('login')"
          >
            Sign in
          </button>
          <button
            type="button"
            :class="{ active: mode === 'register' }"
            @click="switchMode('register')"
          >
            Register
          </button>
        </div>

        <el-form ref="formRef" :model="form" :rules="rules" label-position="top" size="large">
          <el-form-item v-if="mode === 'register'" label="Display name" prop="displayName">
            <el-input v-model="form.displayName" placeholder="Ada Lovelace" />
          </el-form-item>
          <el-form-item label="Email" prop="email">
            <el-input v-model="form.email" placeholder="you@example.com" autocomplete="email" />
          </el-form-item>
          <el-form-item label="Password" prop="password">
            <el-input
              v-model="form.password"
              type="password"
              show-password
              placeholder="At least 8 characters"
              autocomplete="current-password"
              @keyup.enter="submit"
            />
          </el-form-item>
        </el-form>

        <el-button
          type="primary"
          class="submit-button"
          :loading="loading"
          @click="submit"
        >
          {{ mode === 'login' ? 'Sign in' : 'Create account' }}
          <el-icon class="el-icon--right"><ArrowRight /></el-icon>
        </el-button>

        <div class="demo-box">
          <div>
            <strong>Just want the tour?</strong>
            <p>Use the seeded workspace with two demo projects and sample runs.</p>
            <code>demo@scrapestudio.dev · demo1234</code>
          </div>
          <el-button :loading="demoLoading" @click="demoLogin">Try the demo</el-button>
        </div>

        <p class="legal">
          By continuing you agree to respect target sites' robots.txt, rate limits and terms of
          service.
        </p>
      </div>
    </section>
  </div>
</template>

<style scoped>
.login-shell {
  display: grid;
  grid-template-columns: minmax(0, 1.08fr) minmax(0, 1fr);
  min-height: 100vh;
  background: #f5f6fb;
}

.promo {
  position: relative;
  overflow: hidden;
  display: flex;
  align-items: center;
  padding: 56px clamp(32px, 6vw, 88px);
  background: #0b1120;
  color: #e2e8f0;
}

.promo-grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(148, 163, 184, 0.07) 1px, transparent 1px),
    linear-gradient(90deg, rgba(148, 163, 184, 0.07) 1px, transparent 1px);
  background-size: 48px 48px;
  mask-image: radial-gradient(circle at 30% 20%, black, transparent 72%);
}

.promo-glow {
  position: absolute;
  width: 520px;
  height: 520px;
  border-radius: 50%;
  filter: blur(90px);
  opacity: 0.5;
}

.promo-glow.one {
  top: -180px;
  left: -100px;
  background: #4f46e5;
}

.promo-glow.two {
  right: -160px;
  bottom: -220px;
  background: #0891b2;
  opacity: 0.36;
}

.promo-content {
  position: relative;
  z-index: 1;
  width: 100%;
  max-width: 560px;
  margin: 0 auto;
}

.brand {
  display: inline-flex;
  align-items: center;
  gap: 12px;
  color: #f8fafc;
}

.brand-mark {
  width: 44px;
  height: 44px;
  border-radius: 13px;
  box-shadow: 0 10px 30px -12px rgba(99, 102, 241, 0.9);
}

.brand-mark svg {
  width: 100%;
  height: 100%;
  display: block;
}

.brand-copy {
  display: flex;
  flex-direction: column;
  line-height: 1.2;
}

.brand-copy strong {
  font-size: 17px;
  letter-spacing: -0.02em;
}

.brand-copy small {
  color: #7c8aa5;
  font-size: 11px;
  margin-top: 3px;
}

.hero-copy {
  margin-top: 64px;
}

.eyebrow {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 6px 12px;
  border-radius: 999px;
  border: 1px solid rgba(148, 163, 184, 0.22);
  background: rgba(148, 163, 184, 0.08);
  font-size: 11.5px;
  font-weight: 600;
  color: #c7d2fe;
  letter-spacing: 0.03em;
}

.dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #34d399;
  box-shadow: 0 0 0 3px rgba(52, 211, 153, 0.16);
}

.hero-copy h1 {
  margin: 22px 0 0;
  font-size: clamp(34px, 4.2vw, 54px);
  line-height: 1.06;
  letter-spacing: -0.04em;
  font-weight: 780;
  background: linear-gradient(120deg, #ffffff 20%, #c7d2fe 62%, #67e8f9 100%);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}

.hero-copy p {
  margin: 20px 0 0;
  max-width: 500px;
  color: #94a3b8;
  font-size: 15px;
  line-height: 1.7;
}

.feature-list {
  list-style: none;
  margin: 40px 0 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.feature-list li {
  display: flex;
  gap: 14px;
  align-items: flex-start;
}

.feature-icon {
  width: 38px;
  height: 38px;
  border-radius: 11px;
  display: grid;
  place-items: center;
  background: rgba(99, 102, 241, 0.16);
  border: 1px solid rgba(129, 140, 248, 0.22);
  color: #a5b4fc;
  font-size: 18px;
  flex-shrink: 0;
}

.feature-list strong {
  font-size: 13.5px;
  color: #e2e8f0;
}

.feature-list p {
  margin: 4px 0 0;
  font-size: 12.5px;
  line-height: 1.55;
  color: #8497b3;
  max-width: 420px;
}

.promo-foot {
  display: flex;
  flex-wrap: wrap;
  gap: 18px;
  margin-top: 52px;
  padding-top: 22px;
  border-top: 1px solid rgba(148, 163, 184, 0.14);
  color: #6b7c99;
  font-size: 11.5px;
}

.promo-foot span {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.form-side {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px 24px;
}

.form-card {
  width: 100%;
  max-width: 432px;
  padding: 34px;
  background: #ffffff;
  border: 1px solid var(--ss-border);
  border-radius: 22px;
  box-shadow:
    0 1px 2px rgba(15, 23, 42, 0.04),
    0 30px 80px -40px rgba(15, 23, 42, 0.3);
}

.form-head h2 {
  margin: 0;
  font-size: 24px;
  letter-spacing: -0.03em;
}

.form-head p {
  margin: 8px 0 0;
  color: var(--ss-muted);
  font-size: 13.5px;
  line-height: 1.55;
}

.mode-switch {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 4px;
  margin: 24px 0 20px;
  padding: 4px;
  border-radius: 11px;
  background: #f1f5f9;
}

.mode-switch button {
  padding: 8px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: var(--ss-muted);
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.15s ease;
}

.mode-switch button.active {
  background: #ffffff;
  color: var(--ss-text);
  box-shadow: 0 1px 4px rgba(15, 23, 42, 0.1);
}

.submit-button {
  width: 100%;
  margin-top: 4px;
  height: 44px;
}

.demo-box {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  margin-top: 22px;
  padding: 14px 15px;
  border: 1px dashed #c7d2fe;
  border-radius: 13px;
  background: #eef2ff;
}

.demo-box strong {
  font-size: 12.5px;
  color: #3730a3;
}

.demo-box p {
  margin: 3px 0 6px;
  font-size: 11.5px;
  color: #6366f1;
  line-height: 1.45;
}

.demo-box code {
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-size: 11px;
  color: #4338ca;
}

.legal {
  margin: 20px 0 0;
  font-size: 11px;
  line-height: 1.5;
  color: #94a3b8;
  text-align: center;
}

@media (max-width: 980px) {
  .login-shell {
    grid-template-columns: minmax(0, 1fr);
  }

  .promo {
    padding: 36px 24px 42px;
  }

  .promo-content {
    max-width: 640px;
  }

  .hero-copy {
    margin-top: 38px;
  }

  .feature-list,
  .promo-foot {
    display: none;
  }

  .form-side {
    padding: 28px 18px 48px;
  }
}

@media (max-width: 560px) {
  .form-card {
    padding: 26px 22px;
  }

  .demo-box {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
