import { get } from '@/utils/http'

// TODO: 定义 Tool 相关类型后替换 any
export function listTools() {
  return get('/tool')
}
