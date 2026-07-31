import { get } from '@/utils/http'

// TODO: 定义 Workflow 相关类型后替换 any
export function listWorkflows() {
  return get('/workflow')
}
