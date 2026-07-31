import { get } from '@/utils/http'

// TODO: 定义 Log 相关类型后替换 any
export function listLogs() {
  return get('/log')
}
