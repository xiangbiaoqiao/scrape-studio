import { defineStore } from 'pinia';
import { computed, ref } from 'vue';
import {
  api,
  clearSession,
  getStoredToken,
  getStoredUser,
  storeSession,
} from '@/api/client';
import type { AuthResponse, User } from '@/api/types';

export const useAuthStore = defineStore('auth', () => {
  const token = ref(getStoredToken());
  const user = ref<User | null>(getStoredUser());

  const isAuthenticated = computed(() => Boolean(token.value));
  const displayName = computed(() => user.value?.displayName ?? 'Explorer');
  const email = computed(() => user.value?.email ?? '');
  const initials = computed(() => {
    const name = user.value?.displayName?.trim() || user.value?.email || 'S';
    return name
      .split(/\s+/)
      .map((part) => part[0])
      .join('')
      .slice(0, 2)
      .toUpperCase();
  });

  function applySession(response: AuthResponse): void {
    token.value = response.token;
    user.value = response.user;
    storeSession(response.token, response.user);
  }

  async function login(emailAddress: string, password: string): Promise<void> {
    applySession(await api.login(emailAddress, password));
  }

  async function register(
    emailAddress: string,
    password: string,
    displayName: string
  ): Promise<void> {
    applySession(await api.register(emailAddress, password, displayName));
  }

  async function refresh(): Promise<void> {
    if (!token.value) {
      return;
    }
    try {
      const profile = await api.me();
      user.value = profile;
      localStorage.setItem('scrape-studio.user', JSON.stringify(profile));
    } catch (error) {
      const status = (error as { status?: number }).status;
      if (status === 401 || status === 403) {
        logout();
      }
    }
  }

  function logout(): void {
    token.value = '';
    user.value = null;
    clearSession();
  }

  return {
    token,
    user,
    isAuthenticated,
    displayName,
    email,
    initials,
    login,
    register,
    refresh,
    logout,
  };
});
