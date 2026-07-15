/**
 * 从错误对象中提取后端返回的 message。
 *
 * 提取优先级：
 * 1. axios response.data.message（最常见，后端统一响应 { code, message, data }）
 * 2. response.data 为字符串时尝试 JSON.parse
 * 3. Error.message（拦截器已把 res.message 挂到这里）
 * 4. 字符串 error
 * 5. fallback
 */
export function resolveErrorMessage(error: unknown, fallback: string): string {
  if (!error) return fallback

  // 1. 尝试读取 axios response.data.message
  const axiosData = (error as any)?.response?.data
  if (axiosData) {
    // data 可能是字符串（部分 500 响应体）
    if (typeof axiosData === 'string') {
      try {
        const parsed = JSON.parse(axiosData)
        if (parsed?.message) return parsed.message
      } catch {
        return axiosData
      }
    }
    if (axiosData.message) return axiosData.message
  }

  // 2. Error.message（拦截器业务码非 200 时已把 res.message 挂到 Error.message）
  if (error instanceof Error && error.message) return error.message

  // 3. 字符串 error
  if (typeof error === 'string') return error

  return fallback
}
