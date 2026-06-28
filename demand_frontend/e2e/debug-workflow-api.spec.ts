import { test, expect, request as apiRequest } from '@playwright/test'

const BACKEND = process.env.BACKEND_URL || 'http://localhost:8081'

async function getAdminToken() {
  const ctx = await apiRequest.newContext()
  
  console.log('🔐 尝试登录: POST /api/v1/auth/login')
  const res = await ctx.post(`${BACKEND}/api/v1/auth/login`, {
    data: { username: 'admin', password: 'admin123' }
  })
  
  console.log('  - 登录状态码:', res.status())
  const body = await res.text()
  console.log('  - 登录响应体:', body)
  
  try {
    const json = JSON.parse(body)
    console.log('  - 登录 code:', json.code)
    console.log('  - 登录 message:', json.message)
    console.log('  - token:', json.data?.accessToken ? '已获取' : '不存在')
    await ctx.dispose()
    return json.data?.accessToken
  } catch (e) {
    console.log('  - JSON 解析失败:', e)
    await ctx.dispose()
    return null
  }
}

test('诊断工作流模板列表接口', async () => {
  const token = await getAdminToken()
  
  if (!token) {
    console.log('❌ 登录失败，无法继续测试')
    expect(token).toBeTruthy()
    return
  }
  
  const ctx = await apiRequest.newContext()
  
  console.log('\n🔍 测试接口: GET /api/v1/workflows/templates')
  
  const res = await ctx.get(`${BACKEND}/api/v1/workflows/templates`, {
    headers: { Authorization: `Bearer ${token}` },
    params: { page: 1, pageSize: 20 }
  })
  
  console.log('📊 HTTP 状态码:', res.status())
  console.log('✅ res.ok():', res.ok())
  
  const body = await res.text()
  console.log('📦 响应体:', body.substring(0, 500))
  
  try {
    const json = JSON.parse(body)
    console.log('📋 JSON 解析成功:')
    console.log('  - code:', json.code)
    console.log('  - message:', json.message)
    console.log('  - data:', json.data ? '存在' : '不存在')
    if (json.data?.records) {
      console.log('  - records 数量:', json.data.records.length)
    }
  } catch (e) {
    console.log('❌ JSON 解析失败')
  }
  
  await ctx.dispose()
  
  // 不做断言，仅输出信息
  expect(true).toBeTruthy()
})
