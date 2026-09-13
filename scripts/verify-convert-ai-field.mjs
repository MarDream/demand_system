#!/usr/bin/env node
// 端到端验证：数字字段"转为AI字段"（boolean isAiField 请求体，与前端一致）
import { execSync } from 'node:child_process'

const BASE = 'http://localhost:8081/api/v1'
const TS = Date.now().toString(36)
const DOCKER = 'C:\\Program Files\\Docker\\Docker\\resources\\bin\\docker.exe'

async function http(method, path, { token, body } = {}) {
  const res = await fetch(BASE + path, {
    method,
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: body !== undefined ? JSON.stringify(body) : undefined,
  })
  let json = null
  try { json = await res.json() } catch {}
  return { status: res.status, body: json }
}

function mysql(sql) {
  return execSync(`"${DOCKER}" exec mysql mysql -uroot -padmin123 -N -e "USE demand_system; ${sql}" 2>nul`, { encoding: 'utf8' }).trim()
}

async function register(name) {
  const email = `fix_${name}_${TS}@test.com`
  await http('POST', '/auth/send-verification-code', { body: { email, type: 'register' } })
  const code = mysql(`SELECT code FROM verification_codes WHERE email='${email}' AND used=0 ORDER BY created_at DESC LIMIT 1;`)
  const r = await http('POST', '/auth/register', { body: { username: `fix_${name}_${TS}`, password: 'Test@123456', realName: name, email, verificationCode: code } })
  return r.body?.data?.accessToken
}

const token = await register('owner')
const baseId = (await http('POST', '/bitable/bases', { token, body: { name: '转AI字段验证' } })).body?.data
const tableId = (await http('POST', `/bitable/bases/${baseId}/tables`, { token, body: { name: '工时表' } })).body?.data
const f = (await http('POST', `/bitable/tables/${tableId}/fields`, { token, body: { name: '工时', fieldType: 'number' } })).body?.data
console.log('number field created:', f)

// 与前端 handleConvertToAiField 完全一致的请求体（boolean isAiField: true）
const conv = await http('PUT', `/bitable/fields/${f}`, { token, body: { fieldType: 'ai_text', isAiField: true } })
console.log('convert with boolean isAiField ->', conv.status, JSON.stringify(conv.body)?.slice(0, 120))
console.log(conv.status === 200 && conv.body?.code === 200 ? 'PASS: 转AI字段成功' : 'FAIL: 转AI字段仍报错')

// required 传 boolean 也应通过
const f2 = (await http('POST', `/bitable/tables/${tableId}/fields`, { token, body: { name: '备注', fieldType: 'text' } })).body?.data
const req = await http('PUT', `/bitable/fields/${f2}`, { token, body: { required: true } })
console.log('update required with boolean ->', req.status, req.body?.code)
console.log(req.status === 200 && req.body?.code === 200 ? 'PASS: required boolean 兼容' : 'FAIL: required boolean 仍报错')
