/**
 * 头像地址解析（H5 精简版）。
 *
 * 后端 `user.avatar` 存的是原始串，可能是 PC 端的预设头像标识 `preset:xxx`。
 * H5 既没有对应的预设素材，也没有内联 SVG 定义，若直接绑到 `van-image` 的 `:src`
 * 上，浏览器会把它当 URL 去请求，控制台报
 * `GET preset:cartoon-9 net::ERR_UNKNOWN_URL_SCHEME`。
 *
 * 所以这里只做"安全化"：确实是图片地址的原样返回，其余（含 `preset:`）一律返回
 * null，由调用方回退到姓名首字。**H5 不渲染 PC 端的预设头像**，这是刻意的取舍。
 */
export function resolveAvatarUrl(avatar?: string | null): string | null {
  if (!avatar) return null
  if (
    avatar.startsWith('http://') ||
    avatar.startsWith('https://') ||
    avatar.startsWith('data:') ||
    avatar.startsWith('/')
  ) {
    return avatar
  }
  return null
}
