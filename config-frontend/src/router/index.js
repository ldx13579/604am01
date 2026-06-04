import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    name: 'ConfigList',
    component: () => import('../views/ConfigList.vue')
  },
  {
    path: '/history/:id',
    name: 'VersionHistory',
    component: () => import('../views/VersionHistory.vue')
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
