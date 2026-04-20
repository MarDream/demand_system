import axios from 'axios'
import type { AxiosInstance, AxiosRequestConfig, AxiosResponse } from 'axios'
import { ElMessage } from 'element-plus'
import type { ApiResponse } from '@/types/api'
import { getToken, removeToken, getRefreshToken, removeRefreshToken } from '@/utils/auth'

const service: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  timeout: 15000,
})

// Token刷新状态管理
let isRefreshing = false
let refreshSubscribers: Array<(token: string) => void> = []

function subscribeTokenRefresh(callback: (token: string) => void) {
  refreshSubscribers.push(callback)
}

function onTokenRefreshed(token: string) {
  refreshSubscribers.forEach(callback => callback(token))
  refreshSubscribers = []
}

service.interceptors.request.use(
  (config) => {
    const token = getToken()
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  },
)

service.interceptors.response.use(
  (response: AxiosResponse<ApiResponse>) => {
    const res = response.data
    if (res.code !== 200) {
      ElMessage.error(res.message || '请求失败')
      if (res.code === 401) {
        removeToken()
        removeRefreshToken()
        window.location.href = '/login'
      }
      return Promise.reject(new Error(res.message || '请求失败'))
    }
    return res.data as unknown as AxiosResponse
  },
  async (error) => {
    const originalRequest = error.config as AxiosRequestConfig & { _retry?: boolean }

    // 401未授权，尝试刷新Token
    if (error.response?.status === 401 && !originalRequest._retry) {
      // 跳过刷新Token的请求，防止死循环
      if (originalRequest.url?.includes('/v1/auth/refresh')) {
        removeToken()
        removeRefreshToken()
        window.location.href = '/login'
        return Promise.reject(error)
      }

      if (isRefreshing) {
        // 等待Token刷新完成
        return new Promise(resolve => {
          subscribeTokenRefresh((token: string) => {
            originalRequest.headers = originalRequest.headers || {}
            originalRequest.headers.Authorization = `Bearer ${token}`
            resolve(service(originalRequest))
          })
        })
      }

      originalRequest._retry = true
      isRefreshing = true

      try {
        const refreshTokenValue = getRefreshToken()
        if (!refreshTokenValue) {
          removeToken()
          removeRefreshToken()
          window.location.href = '/login'
          return Promise.reject(error)
        }

        // 直接使用axios发送刷新请求，绕过拦截器
        const refreshResponse = await axios.post<ApiResponse<{ accessToken: string; refreshToken: string }>>(
          `${import.meta.env.VITE_API_BASE_URL}/v1/auth/refresh`,
          { refreshToken: refreshTokenValue }
        )
        const refreshData = refreshResponse.data

        if (refreshData.code !== 200) {
          throw new Error(refreshData.message || '刷新Token失败')
        }

        const newToken = refreshData.data.accessToken
        const newRefreshToken = refreshData.data.refreshToken

        // 更新Token
        import('@/utils/auth').then(({ setToken, setRefreshToken }) => {
          setToken(newToken)
          setRefreshToken(newRefreshToken)
        })

        onTokenRefreshed(newToken)
        isRefreshing = false

        originalRequest.headers = originalRequest.headers || {}
        originalRequest.headers.Authorization = `Bearer ${newToken}`
        return service(originalRequest)
      } catch (refreshError) {
        isRefreshing = false
        removeToken()
        removeRefreshToken()
        window.location.href = '/login'
        return Promise.reject(refreshError)
      }
    }

    ElMessage.error(error.message || '网络异常')
    return Promise.reject(error)
  },
)

export default service
