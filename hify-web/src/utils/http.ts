import axios from 'axios'
import { ElMessage } from 'element-plus'

/** 后端统一响应格式 */
interface ApiResponse<T = unknown> {
  code: number
  message: string
  data: T
}

const http = axios.create({
  baseURL: '/api/v1',
  timeout: 30_000,
  headers: {
    'Content-Type': 'application/json',
  },
})

// 响应拦截：统一解包 + 错误提示
// 拦截器解包 ApiResponse.data，因此 AxiosInstance 的返回值不再是 AxiosResponse<T> 而是 T
// eslint-disable-next-line @typescript-eslint/no-explicit-any
;(http.interceptors.response as any).use(
  (res: any) => {
    const json = res.data as ApiResponse
    if (json.code !== 0) {
      ElMessage.error(json.message || '请求失败')
      return Promise.reject(new Error(json.message || '请求失败'))
    }
    return json.data
  },
  (err: any) => {
    const message = err.response?.data?.message || err.message || '网络错误'
    ElMessage.error(message)
    return Promise.reject(new Error(message))
  },
)

export function get<T>(url: string, params?: Record<string, unknown>): Promise<T> {
  return http.get(url, { params }) as Promise<T>
}

export function post<T>(url: string, data?: unknown): Promise<T> {
  return http.post(url, data) as Promise<T>
}

export function put<T>(url: string, data?: unknown): Promise<T> {
  return http.put(url, data) as Promise<T>
}

export function del<T>(url: string): Promise<T> {
  return http.delete(url) as Promise<T>
}
