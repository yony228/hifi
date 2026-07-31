import { get } from '@/utils/http'

// TODO: 定义 Model 相关类型后替换 any
export function listModels() {
  return get('/model')
}
