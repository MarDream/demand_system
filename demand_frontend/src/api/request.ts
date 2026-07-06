import axios from 'axios'
import type { AxiosInstance, AxiosRequestConfig, AxiosResponse } from 'axios'
import { ElMessage } from 'element-plus'
import type { ApiResponse } from '@/types/api'
import {
  getToken,
  setToken,
  getRefreshToken,
  setRefreshToken,
  clearAuth,
  redirectToLogin,
} from '@/utils/auth'

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

function handleAuthExpired(redirect?: string) {
  clearAuth()
  redirectToLogin(redirect)
}

/**
 * 仅当业务码为 401（认证失败/token 过期）时才视为登录态失效。
 * 业务码 403（无权访问 / 越权操作）属于业务级权限不足，保留登录态，
 * 由调用方/弹窗提示用户处理，避免误踢出导致页面"闪退"。
 */
function isAuthExpired(code?: number) {
  return code === 401
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
    if (response.config.responseType === 'blob' || response.config.responseType === 'arraybuffer') {
      return response.data as unknown as AxiosResponse
    }
    const res = response.data
    if (res.code !== 200) {
      ElMessage.error(res.message || '请求失败')
      if (isAuthExpired(res.code)) {
        handleAuthExpired(window.location.pathname + window.location.search + window.location.hash)
      }
      // 在 Error 上附加业务码，便于业务 catch 区分（如业务 403 已弹过提示则静默）
      return Promise.reject(Object.assign(new Error(res.message || '请求失败'), { code: res.code }))
    }
    return res.data as unknown as AxiosResponse
  },
  async (error) => {
    const originalRequest = error.config as AxiosRequestConfig & { _retry?: boolean }
    const currentPath = window.location.pathname + window.location.search + window.location.hash

    // 401未授权，尝试刷新Token
    if (error.response?.status === 401 && !originalRequest._retry) {
      // 跳过刷新Token的请求，防止死循环
      if (originalRequest.url?.includes('/v1/auth/refresh')) {
        handleAuthExpired(currentPath)
        return Promise.reject(error)
      }

      // 登录接口的401是"账号或密码错误"，不是"token过期"，不走refresh逻辑
      // 错误消息由 login.vue 的 catch 统一弹，避免重复
      if (originalRequest.url?.includes('/v1/auth/login')) {
        return Promise.reject(new Error(error.response?.data?.message || '用户名或密码错误'))
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
          handleAuthExpired(currentPath)
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
        setToken(newToken)
        setRefreshToken(newRefreshToken)

        onTokenRefreshed(newToken)

        originalRequest.headers = originalRequest.headers || {}
        originalRequest.headers.Authorization = `Bearer ${newToken}`
        return service(originalRequest)
      } catch (refreshError) {
        handleAuthExpired(currentPath)
        return Promise.reject(refreshError)
      } finally {
        // 确保所有路径（成功/失败/提前return）都重置刷新状态，避免后续请求永久卡在等待队列
        isRefreshing = false
      }
    }

    if (error.response?.status === 403) {
      const msg = error.response?.data?.message || error.message

      // 后端返回 "Invalid CORS request" 表示 CORS 白名单拦截，这不是登录态问题。
      // 应明确提示跨域配置错误，不踢回登录页。
      if (msg === 'Invalid CORS request') {
        const baseURL = import.meta.env.VITE_API_BASE_URL || '/api'
        ElMessage.error(`跨域请求被拒绝，请检查前后端 CORS 配置（前端请求地址：${baseURL}）`)
        return Promise.reject(error)
      }

      // 真正的 403 业务权限不足
      ElMessage.error(msg || '请求被拒绝，无操作权限')
      // 已登录用户的业务 403（如越权操作）只是拒绝操作，不踢出。
      // 登录页面的 403（Invalid CORS）在上面已拦截，到这里的 403 即使是未登录用户
      // 也由登录页的 catch 处理，不在此处调用 handleAuthExpired 以免误跳转。
      return Promise.reject(error)
    }

    // 网络错误（包括 CORS 被浏览器拦截无法获取响应的情况）
    if (!error.response) {
      ElMessage.error('网络连接异常，请检查网络或后端服务是否正常')
      return Promise.reject(error)
    }

    ElMessage.error(error.message || '网络异常')
    return Promise.reject(error)
  },
)

export default service
