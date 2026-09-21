import axios, { type AxiosInstance, type InternalAxiosRequestConfig } from 'axios'
import { showToast } from 'vant'
import { getToken, getRefreshToken, setToken, setRefreshToken, clearAuth, getActiveRole } from '@/utils/auth'

export interface ApiResponse<T = unknown> {
  code: number
  message: string
  data: T
}

export interface PageResult<T> {
  list: T[]
  total: number
  pageNum: number
  pageSize: number
  nextCursor?: number | null
  hasMore?: boolean
}

const service: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 15000,
})

service.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  // 角色切换：与 PC 端同口径，待办/已办按当前生效角色过滤
  const activeRole = getActiveRole()
  if (activeRole) {
    config.headers['X-Active-Role'] = activeRole
  }
  return config
})

let refreshing: Promise<string | null> | null = null

async function doRefresh(): Promise<string | null> {
  const refreshToken = getRefreshToken()
  if (!refreshToken) return null
  try {
    const res = await axios.post<ApiResponse<{ accessToken: string; refreshToken: string }>>(
      `${service.defaults.baseURL}/v1/auth/refresh`,
      { refreshToken },
      { timeout: 10000 },
    )
    if (res.data.code === 200 && res.data.data?.accessToken) {
      setToken(res.data.data.accessToken)
      setRefreshToken(res.data.data.refreshToken)
      return res.data.data.accessToken
    }
    return null
  } catch {
    return null
  }
}

service.interceptors.response.use(
  (response) => {
    const res = response.data as ApiResponse
    if (res.code !== undefined && res.code !== 200) {
      if (res.code === 401) {
        clearAuth()
        showToast('登录已过期，请重新登录')
        const redirect = encodeURIComponent(location.pathname + location.search)
        if (!location.pathname.startsWith('/login')) {
          location.href = `/login?redirect=${redirect}`
        }
        return Promise.reject(new Error(res.message || '未授权'))
      }
      showToast(res.message || '请求失败')
      return Promise.reject(new Error(res.message || '请求失败'))
    }
    return response
  },
  async (error) => {
    const config = error.config as (InternalAxiosRequestConfig & { _retried?: boolean }) | undefined
    // HTTP 401 → 尝试无感刷新后重试一次
    if (error.response?.status === 401 && config && !config._retried && !config.url?.includes('/auth/refresh')) {
      config._retried = true
      refreshing = refreshing || doRefresh()
      const newToken = await refreshing
      refreshing = null
      if (newToken) {
        config.headers.Authorization = `Bearer ${newToken}`
        return service(config)
      }
      clearAuth()
      if (!location.pathname.startsWith('/login')) {
        location.href = '/login'
      }
    }
    showToast(error.response?.data?.message || error.message || '网络异常')
    return Promise.reject(error)
  },
)

/** 请求并解包 Result.data */
export async function request<T>(config: Parameters<AxiosInstance['request']>[0]): Promise<T> {
  const response = await service.request<ApiResponse<T>>(config)
  return response.data.data
}

export function get<T>(url: string, params?: Record<string, unknown>): Promise<T> {
  return request<T>({ method: 'GET', url, params })
}

export function post<T>(url: string, data?: unknown): Promise<T> {
  return request<T>({ method: 'POST', url, data })
}

export function put<T>(url: string, data?: unknown): Promise<T> {
  return request<T>({ method: 'PUT', url, data })
}

export function del<T>(url: string): Promise<T> {
  return request<T>({ method: 'DELETE', url })
}

export default service
