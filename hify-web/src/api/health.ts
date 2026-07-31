import { get } from '@/utils/http'

export interface HealthResponse {
  status: string
  timestamp: string
}

export function getHealth() {
  return get<HealthResponse>('/health')
}
