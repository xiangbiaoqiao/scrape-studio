<script setup lang="ts">
import { computed, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import {
  ArrowRight,
  FirstAidKit,
  Files,
  Histogram,
  Menu,
  MoreFilled,
  Odometer,
  Plus,
  SwitchButton,
} from '@element-plus/icons-vue';
import { useAuthStore } from '@/stores/auth';

const route = useRoute();
const router = useRouter();
const auth = useAuthStore();
const drawer = ref(false);

const nav = [
  { label: 'Dashboard', to: '/', icon: Odometer },
  { label: 'Projects', to: '/projects', icon: Files },
  { label: 'Run history', to: '/runs', icon: Histogram },
  { label: 'Anti-bot doctor', to: '/doctor', icon: FirstAidKit },
];

const pageTitle = computed(() => (route.meta.title as string) ?? 'ScrapeStudio');
const pageSubtitle = computed(() => (route.meta.subtitle as string) ?? '');

function isActive(to: string): boolean {
  return to === '/' ? route.path === '/' : route.path.startsWith(to);
}

function goNewProject(): void {
  router.push({ name: 'projects', query: { new: '1' } });
}

function handleCommand(command: string): void {
  if (command === 'logout') {
    auth.logout();
    router.push({ name: 'login' });
  }
}
</script>

<template>
  <div class="shell">
    <aside class="sidebar">
      <router-link to="/" class="brand">
        <span class="brand-mark">
          <svg viewBox="0 0 64 64" aria-hidden="true">
            <defs>
              <linearGradient id="brand-gradient" x1="0" y1="0" x2="1" y2="1">
                <stop offset="0%" stop-color="#a5b4fc" />
                <stop offset="100%" stop-color="#22d3ee" />
              </linearGradient>
            </defs>
            <rect width="64" height="64" rx="16" fill="#111c33" />
            <path
              d="M18 22h10l4 20h-4l-.8-4.5H18.8L18 42h-4l4-20Zm1.4 12h7.2l-1.3-7.2L19.4 34Z"
              fill="url(#brand-gradient)"
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

      <div class="nav-label">Workspace</div>
      <nav class="nav">
        <router-link
          v-for="item in nav"
          :key="item.to"
          :to="item.to"
          class="nav-item"
          :class="{ active: isActive(item.to) }"
        >
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.label }}</span>
        </router-link>
      </nav>

      <div class="sidebar-spacer" />

      <div class="doctor-card">
        <div class="doctor-eyebrow"><span class="pulse" /> Anti-bot doctor</div>
        <p>Inspect TLS, HTTP/2, CDN and challenge signals before a scheduled run.</p>
        <router-link to="/doctor" class="doctor-link">
          Open doctor <el-icon><ArrowRight /></el-icon>
        </router-link>
      </div>

      <div class="sidebar-footer">
        <div class="user-chip">
          <div class="avatar">{{ auth.initials }}</div>
          <div class="user-copy">
            <strong>{{ auth.displayName }}</strong>
            <small>{{ auth.email }}</small>
          </div>
          <el-dropdown trigger="click" @command="handleCommand">
            <button class="icon-button" type="button" aria-label="Account menu">
              <el-icon><MoreFilled /></el-icon>
            </button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="logout">
                  <el-icon><SwitchButton /></el-icon> Sign out
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </div>
    </aside>

    <div class="main">
      <header class="topbar">
        <button class="menu-button" type="button" @click="drawer = true">
          <el-icon><Menu /></el-icon>
        </button>
        <div class="topbar-copy">
          <span class="topbar-eyebrow">ScrapeStudio</span>
          <h1>{{ pageTitle }}</h1>
          <p v-if="pageSubtitle">{{ pageSubtitle }}</p>
        </div>
        <div class="topbar-actions">
          <span class="version-pill">v0.1.0 · local</span>
          <el-button type="primary" @click="goNewProject">
            <el-icon><Plus /></el-icon>
            New project
          </el-button>
        </div>
      </header>

      <main class="content">
        <router-view v-slot="{ Component }">
          <transition name="page" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </main>
    </div>

    <el-drawer v-model="drawer" direction="ltr" size="272px" :with-header="false">
      <div class="drawer-inner">
        <router-link to="/" class="brand" @click="drawer = false">
          <span class="brand-mark compact">
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

        <nav class="nav">
          <router-link
            v-for="item in nav"
            :key="item.to"
            :to="item.to"
            class="nav-item"
            :class="{ active: isActive(item.to) }"
            @click="drawer = false"
          >
            <el-icon><component :is="item.icon" /></el-icon>
            <span>{{ item.label }}</span>
          </router-link>
        </nav>

        <button class="drawer-logout" type="button" @click="handleCommand('logout')">
          <el-icon><SwitchButton /></el-icon> Sign out
        </button>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.shell {
  display: flex;
  min-height: 100vh;
}

.sidebar {
  position: sticky;
  top: 0;
  display: flex;
  flex-direction: column;
  width: 254px;
  height: 100vh;
  padding: 24px 18px 18px;
  background:
    radial-gradient(circle at 15% 0%, rgba(99, 102, 241, 0.26), transparent 38%),
    radial-gradient(circle at 100% 70%, rgba(6, 182, 212, 0.08), transparent 42%),
    #0b1120;
  color: #cbd5e1;
  flex-shrink: 0;
  z-index: 20;
}

.brand {
  display: flex;
  align-items: center;
  gap: 11px;
  padding: 0 6px;
  color: #f8fafc;
}

.brand-mark {
  width: 40px;
  height: 40px;
  flex-shrink: 0;
  border-radius: 12px;
  box-shadow: 0 8px 22px -10px rgba(99, 102, 241, 0.9);
}

.brand-mark svg {
  width: 100%;
  height: 100%;
  display: block;
}

.brand-mark.compact {
  width: 36px;
  height: 36px;
}

.brand-copy {
  display: flex;
  flex-direction: column;
  line-height: 1.2;
}

.brand-copy strong {
  font-size: 15.5px;
  letter-spacing: -0.02em;
}

.brand-copy small {
  color: #7c8aa5;
  font-size: 10.5px;
  margin-top: 3px;
  letter-spacing: 0.01em;
}

.nav-label {
  margin: 30px 10px 10px;
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.13em;
  text-transform: uppercase;
  color: #5f6f8d;
}

.nav {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 11px;
  padding: 10px 12px;
  border-radius: 10px;
  color: #9fb0c9;
  font-size: 13.5px;
  font-weight: 500;
  transition: all 0.15s ease;
}

.nav-item .el-icon {
  font-size: 17px;
}

.nav-item:hover {
  background: rgba(148, 163, 184, 0.08);
  color: #e2e8f0;
}

.nav-item.active {
  background: linear-gradient(90deg, rgba(99, 102, 241, 0.28), rgba(99, 102, 241, 0.08));
  color: #ffffff;
  box-shadow: inset 2px 0 0 #a5b4fc;
}

.nav-item.active .el-icon {
  color: #c7d2fe;
}

.sidebar-spacer {
  flex: 1;
}

.doctor-card {
  margin: 18px 2px 16px;
  padding: 15px;
  border: 1px solid rgba(148, 163, 184, 0.16);
  border-radius: 13px;
  background: linear-gradient(160deg, rgba(99, 102, 241, 0.16), rgba(15, 23, 42, 0.2));
}

.doctor-eyebrow {
  display: flex;
  align-items: center;
  gap: 7px;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: #c7d2fe;
}

.pulse {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #34d399;
  box-shadow: 0 0 0 3px rgba(52, 211, 153, 0.16);
}

.doctor-card p {
  margin: 9px 0 12px;
  font-size: 11.5px;
  line-height: 1.5;
  color: #8497b3;
}

.doctor-link {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: #c7d2fe;
  font-size: 12px;
  font-weight: 600;
}

.doctor-link:hover {
  color: #ffffff;
}

.sidebar-footer {
  border-top: 1px solid rgba(148, 163, 184, 0.12);
  padding-top: 16px;
}

.user-chip {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 4px;
}

.avatar {
  width: 34px;
  height: 34px;
  border-radius: 10px;
  display: grid;
  place-items: center;
  background: linear-gradient(135deg, #6366f1, #06b6d4);
  color: #ffffff;
  font-size: 12px;
  font-weight: 700;
  flex-shrink: 0;
}

.user-copy {
  display: flex;
  flex-direction: column;
  min-width: 0;
  flex: 1;
}

.user-copy strong {
  font-size: 12.5px;
  color: #e2e8f0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.user-copy small {
  font-size: 10.5px;
  color: #6b7c99;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.icon-button {
  border: 0;
  background: transparent;
  color: #7c8aa5;
  cursor: pointer;
  padding: 4px;
  border-radius: 6px;
  display: grid;
  place-items: center;
}

.icon-button:hover {
  color: #e2e8f0;
  background: rgba(148, 163, 184, 0.1);
}

.main {
  flex: 1;
  min-width: 0;
}

.topbar {
  position: sticky;
  top: 0;
  z-index: 15;
  display: flex;
  align-items: center;
  gap: 14px;
  min-height: 78px;
  padding: 14px 34px;
  background: rgba(245, 246, 251, 0.86);
  backdrop-filter: blur(12px);
  border-bottom: 1px solid rgba(230, 232, 240, 0.7);
}

.menu-button {
  display: none;
  width: 38px;
  height: 38px;
  border: 1px solid var(--ss-border);
  border-radius: 10px;
  background: #ffffff;
  color: var(--ss-text);
  cursor: pointer;
  place-items: center;
}

.topbar-copy {
  min-width: 0;
}

.topbar-eyebrow {
  display: block;
  font-size: 10px;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: #94a3b8;
  font-weight: 700;
}

.topbar-copy h1 {
  margin: 2px 0 0;
  font-size: 17px;
  font-weight: 700;
  letter-spacing: -0.02em;
}

.topbar-copy p {
  margin: 3px 0 0;
  color: var(--ss-muted);
  font-size: 12px;
}

.topbar-actions {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 12px;
}

.version-pill {
  padding: 5px 10px;
  border-radius: 999px;
  border: 1px solid var(--ss-border);
  background: #ffffff;
  color: var(--ss-muted);
  font-size: 11px;
  font-weight: 600;
  white-space: nowrap;
}

.content {
  min-height: calc(100vh - 78px);
}

.drawer-inner {
  min-height: 100vh;
  margin: -20px;
  padding: 24px 18px;
  background: #0b1120;
  color: #cbd5e1;
}

.drawer-inner .nav {
  margin-top: 28px;
}

.drawer-logout {
  margin-top: 28px;
  display: flex;
  align-items: center;
  gap: 9px;
  width: 100%;
  padding: 11px 12px;
  border: 1px solid rgba(148, 163, 184, 0.16);
  border-radius: 10px;
  background: transparent;
  color: #9fb0c9;
  font-size: 13px;
  cursor: pointer;
}

.page-enter-active,
.page-leave-active {
  transition:
    opacity 0.16s ease,
    transform 0.16s ease;
}

.page-enter-from {
  opacity: 0;
  transform: translateY(6px);
}

.page-leave-to {
  opacity: 0;
}

@media (max-width: 980px) {
  .sidebar {
    display: none;
  }

  .menu-button {
    display: grid;
  }

  .topbar {
    padding: 12px 18px;
  }

  .topbar-actions .version-pill {
    display: none;
  }
}

@media (max-width: 640px) {
  .topbar-copy p {
    display: none;
  }

  .topbar-actions :deep(.el-button) {
    padding: 8px 11px;
  }
}
</style>
