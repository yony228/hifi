import { get, post } from '@/utils/http'

// TODO: 定义 Chat 相关类型后替换 any
export function createSession(agentId: number) {
  return post('/chat/sessions', { agentId })
}

export function listSessions() {
  return get('/chat/sessions')
}
