// 后台可疑接口复现脚本（使用 admin token）
const BASE = 'http://127.0.0.1:5170'

async function login() {
  const r = await fetch(`${BASE}/api/v1/auth/login`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: 'admin', password: 'admin123' }),
  })
  const j = await r.json()
  return j?.data?.accessToken || ''
}

const token = await login()
console.log('token:', token ? 'OK' : 'FAIL')

const endpoints = [
  'GET /api/v1/assistant/sessions',
  'GET /api/v1/bitable/bases',
  'GET /api/v1/statistics/rating',
  'GET /v1/statistics/rating',
  'GET /api/v1/statistics/rating/trend',
]

for (const e of endpoints) {
  const [method, path] = e.split(' ')
  try {
    const res = await fetch(`${BASE}${path}`, {
      method, headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    })
    const text = await res.text()
    let short = text.slice(0, 360).replace(/\n/g, ' ')
    let parsed = null
    try { parsed = JSON.parse(text) } catch {}
    const code = parsed && parsed.code !== undefined ? `code=${parsed.code}` : ''
    const msg = parsed && parsed.message ? `msg=${parsed.message}` : ''
    console.log(`\n[${res.status}] ${method} ${path} ${code} ${msg}`)
    if (res.status >= 400 || (parsed && parsed.code && parsed.code >= 400)) {
      console.log('  body:', short)
    }
  } catch (err) {
    console.log(`\n[ERR] ${method} ${path}: ${err.message}`)
  }
}
