// 前端 Console / 网络 / 异常实时审计脚本（注入 token 后遍历核心页面）
import { chromium } from 'playwright'
import { writeFileSync } from 'fs'

const BASE = process.env.AUDIT_BASE || 'http://127.0.0.1:5170'
const OUT = process.env.AUDIT_OUT || 'console-audit-result.json'
const API_BASE = BASE // 经 Vite 代理到 8081

const consoleMsgs = []
const pageErrors = []
const failedRequests = []
const badResponses = []

const browser = await chromium.launch({ headless: true })
const context = await browser.newContext({ viewport: { width: 1440, height: 900 } })
const page = await context.newPage()

// ---- 1) 拿 token ----
let token = ''
try {
  const resp = await fetch(`${API_BASE}/api/v1/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: 'admin', password: 'admin123' }),
  })
  const json = await resp.json()
  token = json?.data?.accessToken || ''
  console.log('[token] got:', token ? 'YES' : 'NO')
} catch (e) {
  console.log('[token] fetch failed:', e.message)
}

if (token) {
  await context.addCookies([{ name: 'access_token', value: token, domain: '127.0.0.1', path: '/' }])
}

page.on('console', (msg) => {
  const t = msg.type()
  if (t === 'error' || t === 'warning' || t === 'debug') {
    consoleMsgs.push({
      type: t,
      text: msg.text(),
      location: (msg.location() && msg.location().url) || null,
      line: (msg.location() && msg.location().lineNumber) || null,
    })
  }
})
page.on('pageerror', (err) => {
  pageErrors.push({ message: err.message, stack: (err.stack || '').split('\n').slice(0, 6) })
})
page.on('requestfailed', (req) => {
  failedRequests.push({ url: req.url(), method: req.method(), failure: req.failure() ? req.failure().errorText : null })
})
page.on('response', async (res) => {
  if (res.status() >= 400) {
    let body = ''
    try { body = (await res.text()).slice(0, 500) } catch {}
    badResponses.push({ url: res.url(), status: res.status(), statusText: res.statusText, body })
  }
})

async function visit(path, waitMs = 3000) {
  try {
    await page.goto(`${BASE}${path}`, { waitUntil: 'domcontentloaded', timeout: 25000 })
    await page.waitForTimeout(waitMs)
    console.log(`[visit] ${path} -> url=${page.url()} title=${(await page.title()).slice(0,30)}`)
  } catch (e) {
    console.log(`[visit-fail] ${path}: ${e.message}`)
  }
}

// 先访问 dashboard 触发 userInfo 拉取
await visit('/dashboard', 4000)
// 核心业务页面（使用真实路由路径）
const pages = [
  '/requirements', '/bitable', '/settings/documents', '/settings/knowledge',
  '/settings/users', '/settings/llm', '/system/workflow-config', '/iterations', '/notifications',
]
for (const p of pages) await visit(p, 4000)

await browser.close()

const result = {
  base: BASE,
  tokenInjected: !!token,
  capturedAt: new Date().toISOString(),
  summary: {
    consoleErrorCount: consoleMsgs.filter((m) => m.type === 'error').length,
    consoleWarningCount: consoleMsgs.filter((m) => m.type === 'warning').length,
    consoleDebugCount: consoleMsgs.filter((m) => m.type === 'debug').length,
    pageErrorCount: pageErrors.length,
    failedRequestCount: failedRequests.length,
    badResponseCount: badResponses.length,
  },
  consoleErrors: consoleMsgs.filter((m) => m.type === 'error'),
  consoleWarnings: consoleMsgs.filter((m) => m.type === 'warning'),
  pageErrors,
  failedRequests,
  badResponses,
}
writeFileSync(OUT, JSON.stringify(result, null, 2))
console.log('=== AUDIT SUMMARY ===')
console.log(JSON.stringify(result.summary, null, 2))
console.log(`Written to ${OUT}`)
