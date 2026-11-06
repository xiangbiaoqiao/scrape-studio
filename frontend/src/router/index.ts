import { createRouter, createWebHistory } from 'vue-router';
import { useAuthStore } from '@/stores/auth';

const router = createRouter({
  history: createWebHistory(),
  scrollBehavior: () => ({ top: 0 }),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
      meta: { title: 'Sign in', public: true },
    },
    {
      path: '/',
      component: () => import('@/layouts/AppLayout.vue'),
      children: [
        {
          path: '',
          name: 'dashboard',
          component: () => import('@/views/DashboardView.vue'),
          meta: { title: 'Dashboard', subtitle: 'Your scraping workspace at a glance' },
        },
        {
          path: 'projects',
          name: 'projects',
          component: () => import('@/views/ProjectsView.vue'),
          meta: { title: 'Projects', subtitle: 'Every target, selector and schedule in one place' },
        },
        {
          path: 'projects/:id',
          name: 'project-detail',
          component: () => import('@/views/ProjectDetailView.vue'),
          meta: { title: 'Project builder' },
        },
        {
          path: 'runs',
          name: 'runs',
          component: () => import('@/views/RunsView.vue'),
          meta: { title: 'Run history', subtitle: 'Inspect every extraction, export and diagnosis' },
        },
        {
          path: 'runs/:id',
          name: 'run-detail',
          component: () => import('@/views/RunDetailView.vue'),
          meta: { title: 'Run detail' },
        },
        {
          path: 'doctor',
          name: 'doctor',
          component: () => import('@/views/DoctorView.vue'),
          meta: { title: 'Anti-bot doctor', subtitle: 'Check TLS, HTTP/2, CDN and challenge signals' },
        },
      ],
    },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
});

router.beforeEach((to) => {
  const auth = useAuthStore();
  const isPublic = Boolean(to.meta.public);

  if (!isPublic && !auth.isAuthenticated) {
    return { name: 'login', query: { redirect: to.fullPath } };
  }
  if (to.name === 'login' && auth.isAuthenticated) {
    return { name: 'dashboard' };
  }
  return true;
});

router.afterEach((to) => {
  const title = typeof to.meta.title === 'string' ? to.meta.title : '';
  document.title = title ? `${title} · ScrapeStudio` : 'ScrapeStudio';
});

export default router;
