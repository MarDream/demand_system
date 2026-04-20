import request from '@/api/request'
import type { ApiResponse } from '@/types/api'

export function login(username: string, password: string) {
  return request.post<ApiResponse<{ accessToken: string; refreshToken: string; expiresIn: number }>>('/v1/auth/login', { username, password })
}

export function logout() {
  return request.post<ApiResponse>('/v1/auth/logout')
}

export function refreshToken(refreshToken: string) {
  return request.post<ApiResponse<{ accessToken: string; refreshToken: string }>>('/v1/auth/refresh', { refreshToken })
}

export function getMe() {
  return request.get<ApiResponse<{ id: number; username: string; realName: string; email: string; avatar: string; roles: string[] }>>('/v1/auth/me')
}
