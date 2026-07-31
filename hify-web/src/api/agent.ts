import { get } from '@/utils/http'

// TODO: 定义 Agent 相关类型后替换 any
export function listAgents() {
  return get('/agent')
}

export function getAgent(id: number) {
  return get(`/agent/${id}`)
}
