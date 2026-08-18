import { createRouter, createWebHistory } from 'vue-router'
import { hasRole } from '../auth/keycloak'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'home', component: () => import('../views/HomeView.vue') },
    { path: '/products', name: 'products', component: () => import('../views/ProductsView.vue') },
    { path: '/orders', name: 'orders', component: () => import('../views/OrdersView.vue') },
    { path: '/profile', name: 'profile', component: () => import('../views/ProfileView.vue') },
    { path: '/messages', name: 'messages', component: () => import('../views/MessagesView.vue') },
    {
      path: '/admin',
      name: 'admin',
      component: () => import('../views/AdminView.vue'),
      meta: { requiresAdmin: true },
    },
    {
      path: '/queues',
      name: 'queues',
      component: () => import('../views/ActionQueuesView.vue'),
      meta: { requiresAnyRole: ['admin', 'finance', 'warehouse', 'delivery_agent'] },
    },
  ],
})

router.beforeEach((to) => {
  if (to.meta.requiresAdmin && !hasRole('admin')) {
    return { name: 'home' }
  }
  const anyRole = to.meta.requiresAnyRole as string[] | undefined
  if (anyRole && !anyRole.some((role) => hasRole(role))) {
    return { name: 'home' }
  }
  return true
})

export default router
