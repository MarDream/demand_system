import Cookies from 'js-cookie'

const TOKEN_KEY = 'access_token'
const REFRESH_TOKEN_KEY = 'refresh_token'

export function getToken(): string {
  return Cookies.get(TOKEN_KEY) || ''
}

export function setToken(token: string): void {
  Cookies.set(TOKEN_KEY, token)
}

export function removeToken(): void {
  Cookies.remove(TOKEN_KEY)
}

export function getRefreshToken(): string {
  return Cookies.get(REFRESH_TOKEN_KEY) || ''
}

export function setRefreshToken(token: string): void {
  Cookies.set(REFRESH_TOKEN_KEY, token)
}

export function removeRefreshToken(): void {
  Cookies.remove(REFRESH_TOKEN_KEY)
}
