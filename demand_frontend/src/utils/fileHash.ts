/**
 * 文件内容哈希(去重校验用)。
 * 使用 Web Crypto SubtleCrypto(安全上下文可用);不可用时返回 null,调用方跳过预检。
 */

/** SHA-256 hex(64 位小写)。环境不支持 SubtleCrypto 时返回 null。 */
export async function computeFileSha256(file: File): Promise<string | null> {
  try {
    if (!globalThis.crypto?.subtle) return null
    const digest = await crypto.subtle.digest('SHA-256', await file.arrayBuffer())
    return Array.from(new Uint8Array(digest))
      .map((b) => b.toString(16).padStart(2, '0'))
      .join('')
  } catch {
    return null
  }
}
