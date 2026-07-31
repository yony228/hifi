import { get } from '@/utils/http'

// TODO: 定义 Knowledge 相关类型后替换 any
export function listKnowledgeBases() {
  return get('/knowledge')
}
