import { createRouter, createWebHistory } from 'vue-router'
import auth from '../store/auth'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/Login.vue'),
    meta: { public: true }
  },
  {
    path: '/',
    name: 'ConfigList',
    component: () => import('../views/ConfigList.vue')
  },
  {
    path: '/history/:id',
    name: 'VersionHistory',
    component: () => import('../views/VersionHistory.vue')
  },
  {
    path: '/grayscale',
    name: 'GrayscaleManage',
    component: () => import('../views/GrayscaleManage.vue')
  },
  {
    path: '/users',
    name: 'UserManage',
    component: () => import('../views/UserManage.vue')
  },
  {
    path: '/audit',
    name: 'AuditLog',
    component: () => import('../views/AuditLog.vue')
  },
  {
    path: '/validation',
    name: 'ValidationScripts',
    component: () => import('../views/ValidationScripts.vue')
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach(async (to, from, next) => {
  if (to.meta.public) {
    next()
    return
  }
  if (!auth.state.isAuthenticated) {
    const ok = await auth.fetchMe()
    if (!ok) {
      next('/login')
      return
    }
  }
  if (auth.state.forcePasswordChange && to.path !== '/login') {
    next('/login')
    return
  }
  next()
})

export default router
