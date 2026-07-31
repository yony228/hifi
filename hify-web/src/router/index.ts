import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      redirect: '/chat',
    },
    {
      path: '/chat',
      name: 'chat',
      component: () => import('@/views/chat/ChatView.vue'),
    },
    {
      path: '/agent',
      name: 'agent',
      component: () => import('@/views/agent/AgentView.vue'),
    },
    {
      path: '/workflow',
      name: 'workflow',
      component: () => import('@/views/workflow/WorkflowView.vue'),
    },
    {
      path: '/knowledge',
      name: 'knowledge',
      component: () => import('@/views/knowledge/KnowledgeView.vue'),
    },
    {
      path: '/tool',
      name: 'tool',
      component: () => import('@/views/tool/ToolView.vue'),
    },
    {
      path: '/model',
      name: 'model',
      component: () => import('@/views/model/ModelView.vue'),
    },
    {
      path: '/log',
      name: 'log',
      component: () => import('@/views/log/LogView.vue'),
    },
    {
      path: '/settings',
      name: 'settings',
      component: () => import('@/views/settings/SettingsView.vue'),
    },
  ],
})

export default router
