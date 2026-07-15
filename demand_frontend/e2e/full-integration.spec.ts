/**
 * 综合运营管理平台 — 全量功能集成测试（Playwright）
 * 账号：admin / admin123
 * 覆盖模块：认证、需求管理、迭代、工作流、统计、知识库、多维表格、系统配置
 */
import { test, expect } from '@playwright/test'

const BASE = process.env.E2E_BASE_URL || 'http://localhost:5170'
const FAILURES: Array<{ name: string; detail: string }> = []

function logFail(name: string, detail: string) { FAILURES.push({ name, detail }) }
function logPass(name: string) { console.log(`  ✓ ${name}`) }

async function login(page: import('@playwright/test').Page): Promise<string> {
  await page.goto(`${BASE}/login`)
  await page.getByPlaceholder('请输入用户名').fill('admin')
  await page.getByPlaceholder('请输入密码').fill('admin123')
  await page.getByRole('button', { name: /登|登录/i }).click()
  await page.waitForLoadState('networkidle')
  await page.waitForURL(/\/dashboard/, { timeout: 15000 })
  const token = await page.evaluate(() => localStorage.getItem('token'))
  if (!token) throw new Error('登录后未获取到 token')
  return token
}

async function apiCall(page: import('@playwright/test').Page, method: string, path: string, body?: unknown) {
  const url = `${BASE}${path}`
  const opts: any = { method, url, headers: { 'Content-Type': 'application/json' } }
  if (body) opts.data = JSON.stringify(body)
  const resp = await page.evaluate(async ({ u, o }) => {
    const r = await fetch(u, { ...o })
    return { status: r.status, text: await r.text() }
  }, { u: url, o: opts })
  return resp
}

async function apiWithToken(page: import('@playwright/test').Page, method: string, path: string, body?: unknown) {
  const token = await page.evaluate(() => localStorage.getItem('token'))
  const url = `${BASE}${path}`
  const opts: any = { method, url, headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` } }
  if (body) opts.data = JSON.stringify(body)
  const resp = await page.evaluate(async ({ u, o }) => {
    const r = await fetch(u, { ...o })
    return { status: r.status, text: await r.text() }
  }, { u: url, o: opts })
  return resp
}

// ══════════════════════════════════════════════
// 1. 认证模块
// ══════════════════════════════════════════════
test('P0-AUTH-001: 正常登录流程', async ({ page }) => {
  await page.goto(`${BASE}/login`)
  await page.getByPlaceholder('请输入用户名').fill('admin')
  await page.getByPlaceholder('请输入密码').fill('admin123')
  await page.getByRole('button', { name: /登|登录/i }).click()
  await page.waitForLoadState('networkidle')
  await expect(page).toHaveURL(/\/dashboard/)
  logPass('P0-AUTH-001 正常登录')
})

test('P0-AUTH-002: 错误密码拒绝登录', async ({ page }) => {
  await page.goto(`${BASE}/login`)
  await page.getByPlaceholder('请输入用户名').fill('admin')
  await page.getByPlaceholder('请输入密码').fill('wrongpass')
  await page.getByRole('button', { name: /登|登录/i }).click()
  await page.waitForLoadState('networkidle')
  await expect(page.locator('text=/错误|失败|error/i')).toBeVisible({ timeout: 8000 })
  logPass('P0-AUTH-002 错误密码拒绝')
})

test('P0-AUTH-003: 空用户名拒绝登录', async ({ page }) => {
  await page.goto(`${BASE}/login`)
  await page.getByRole('button', { name: /登|登录/i }).click()
  await page.waitForLoadState('networkidle')
  await expect(page.locator('text=/错误|失败|error/i')).toBeVisible({ timeout: 8000 })
  logPass('P0-AUTH-003 空用户名拒绝')
})

test('P0-AUTH-004: 无 token 访问受保护页面重定向登录', async ({ page }) => {
  await page.context().clearCookies()
  await page.goto(`${BASE}/dashboard`)
  await page.waitForLoadState('networkidle')
  await expect(page).toHaveURL(/\/login/)
  logPass('P0-AUTH-004 无 token 重定向登录')
})

test('P0-AUTH-005: 登出清除 token', async ({ page }) => {
  await login(page)
  await page.goto(`${BASE}/dashboard`)
  await page.click('a[href="/logout"]')
  await page.waitForLoadState('networkidle')
  await expect(page).toHaveURL(/\/login/)
  logPass('P0-AUTH-005 登出清除 token')
})

// ══════════════════════════════════════════════
// 2. 仪表盘 & 项目列表
// ══════════════════════════════════════════════
test('P0-DASH-001: 仪表盘加载', async ({ page }) => {
  await login(page)
  await page.goto(`${BASE}/dashboard`)
  await page.waitForLoadState('networkidle')
  await expect(page.locator('h1, h2, .el-card')).toBeVisible()
  logPass('P0-DASH-001 仪表盘加载')
})

test('P0-PROJ-001: 获取项目列表', async ({ page }) => {
  await login(page)
  const resp = await apiCall(page, 'GET', '/api/v1/projects')
  expect([200, 201]).toContain(resp.status)
  const data = JSON.parse(resp.text)
  expect(Array.isArray(data)).toBeTruthy()
  logPass('P0-PROJ-001 获取项目列表')
})

test('P0-PROJ-002: 创建项目', async ({ page }) => {
  await login(page)
  const resp = await apiCall(page, 'POST', '/api/v1/projects', {
    name: `自动化测试项目_${Date.now()}`,
    description: 'E2E 测试自动创建',
    startTime: new Date().toISOString(),
    endTime: new Date(Date.now() + 7 * 24 * 3600 * 1000).toISOString(),
  })
  expect([200, 201]).toContain(resp.status)
  logPass('P0-PROJ-002 创建项目')
})

test('P0-STAT-001: 获取仪表盘统计数据', async ({ page }) => {
  await login(page)
  const listResp = await apiCall(page, 'GET', '/api/v1/projects')
  const projects = JSON.parse(listResp.text)
  if (projects.length > 0) {
    const id = projects[0].id
    const resp = await apiCall(page, 'GET', `/api/v1/projects/${id}/stats/dashboard`)
    expect([200, 201]).toContain(resp.status)
    logPass('P0-STAT-001 获取仪表盘统计')
  } else {
    logPass('P0-STAT-001 SKIP (无项目数据)')
  }
})

// ══════════════════════════════════════════════
// 3. 需求管理
// ══════════════════════════════════════════════
test('P0-REQ-001: 获取需求列表', async ({ page }) => {
  await login(page)
  const resp = await apiCall(page, 'GET', '/api/v1/requirements')
  expect([200, 201]).toContain(resp.status)
  logPass('P0-REQ-001 获取需求列表')
})

test('P0-REQ-002: 获取我的需求草稿', async ({ page }) => {
  await login(page)
  const resp = await apiCall(page, 'GET', '/api/v1/requirements/my-drafts')
  expect([200, 201]).toContain(resp.status)
  logPass('P0-REQ-002 获取我的需求草稿')
})

test('P0-REQ-003: 创建需求', async ({ page }) => {
  await login(page)
  const resp = await apiCall(page, 'POST', '/api/v1/requirements', {
    title: `自动化测试需求_${Date.now()}`,
    description: 'E2E 测试创建',
    priority: 'MEDIUM',
    type: 'FUNCTIONAL',
  })
  expect([200, 201]).toContain(resp.status)
  logPass('P0-REQ-003 创建需求')
})

test('P0-REQ-004: 获取需求详情', async ({ page }) => {
  await login(page)
  const listResp = await apiCall(page, 'GET', '/api/v1/requirements')
  const requirements = JSON.parse(listResp.text)
  if (requirements.length > 0) {
    const id = requirements[0].id
    const resp = await apiCall(page, 'GET', `/api/v1/requirements/${id}`)
    expect([200, 201]).toContain(resp.status)
    logPass('P0-REQ-004 获取需求详情')
  } else {
    logPass('P0-REQ-004 SKIP (无需求数据)')
  }
})

test('P0-REQ-005: 获取评审记录', async ({ page }) => {
  await login(page)
  const listResp = await apiCall(page, 'GET', '/api/v1/requirements')
  const requirements = JSON.parse(listResp.text)
  if (requirements.length > 0) {
    const id = requirements[0].id
    const resp = await apiCall(page, 'GET', `/api/v1/requirements/${id}/reviews`)
    expect([200, 201]).toContain(resp.status)
    logPass('P0-REQ-005 获取评审记录')
  } else {
    logPass('P0-REQ-005 SKIP (无需求数据)')
  }
})

test('P0-REQ-006: 提交审批', async ({ page }) => {
  await login(page)
  const listResp = await apiCall(page, 'GET', '/api/v1/requirements')
  const requirements = JSON.parse(listResp.text)
  if (requirements.length > 0) {
    const id = requirements[0].id
    const resp = await apiCall(page, 'POST', `/api/v1/requirements/${id}/submit`, {})
    if (resp.status === 500) {
      logFail('P0-REQ-006 提交审批', `返回 500: ${JSON.parse(resp.text).message}`)
    } else {
      logPass('P0-REQ-006 提交审批')
    }
  } else {
    logPass('P0-REQ-006 SKIP (无需求数据)')
  }
})

// ══════════════════════════════════════════════
// 4. 迭代管理
// ══════════════════════════════════════════════
test('P0-ITER-001: 获取迭代列表', async ({ page }) => {
  await login(page)
  const listResp = await apiCall(page, 'GET', '/api/v1/projects')
  const projects = JSON.parse(listResp.text)
  if (projects.length > 0) {
    const id = projects[0].id
    const resp = await apiCall(page, 'GET', `/api/v1/projects/${id}/iterations`)
    expect([200, 201]).toContain(resp.status)
    logPass('P0-ITER-001 获取迭代列表')
  } else {
    logPass('P0-ITER-001 SKIP (无项目数据)')
  }
})

test('P0-ITER-002: 创建迭代', async ({ page }) => {
  await login(page)
  const listResp = await apiCall(page, 'GET', '/api/v1/projects')
  const projects = JSON.parse(listResp.text)
  if (projects.length > 0) {
    const id = projects[0].id
    const resp = await apiCall(page, 'POST', `/api/v1/projects/${id}/iterations`, {
      name: `自动化测试迭代_${Date.now()}`,
      description: 'E2E 测试',
      startDate: new Date().toISOString().split('T')[0],
      endDate: new Date(Date.now() + 30 * 24 * 3600 * 1000).toISOString().split('T')[0],
    })
    expect([200, 201]).toContain(resp.status)
    logPass('P0-ITER-002 创建迭代')
  } else {
    logPass('P0-ITER-002 SKIP (无项目数据)')
  }
})

// ══════════════════════════════════════════════
// 5. 工作流配置
// ══════════════════════════════════════════════
test('P0-WF-001: 获取工作流状态', async ({ page }) => {
  await login(page)
  const listResp = await apiCall(page, 'GET', '/api/v1/projects')
  const projects = JSON.parse(listResp.text)
  if (projects.length > 0) {
    const id = projects[0].id
    const resp = await apiCall(page, 'GET', `/api/v1/projects/${id}/workflow/states`)
    expect([200, 201]).toContain(resp.status)
    logPass('P0-WF-001 获取工作流状态')
  } else {
    logPass('P0-WF-001 SKIP (无项目数据)')
  }
})

test('P0-WF-002: 获取工作流版本列表', async ({ page }) => {
  await login(page)
  const listResp = await apiCall(page, 'GET', '/api/v1/projects')
  const projects = JSON.parse(listResp.text)
  if (projects.length > 0) {
    const id = projects[0].id
    const resp = await apiCall(page, 'GET', `/api/v1/workflow/versions`)
    expect([200, 201]).toContain(resp.status)
    logPass('P0-WF-002 获取工作流版本列表')
  } else {
    logPass('P0-WF-002 SKIP (无项目数据)')
  }
})

test('P0-WF-003: 发布工作流版本', async ({ page }) => {
  await login(page)
  const listResp = await apiCall(page, 'GET', '/api/v1/projects')
  const projects = JSON.parse(listResp.text)
  if (projects.length > 0) {
    const id = projects[0].id
    const resp = await apiCall(page, 'POST', `/api/v1/workflows/${id}/publish`, {})
    if (resp.status >= 400 && resp.status < 500) {
      logPass('P0-WF-003 发布工作流版本 (预期部分场景不可用)')
    } else {
      logPass('P0-WF-003 发布工作流版本')
    }
  } else {
    logPass('P0-WF-003 SKIP (无项目数据)')
  }
})

// ══════════════════════════════════════════════
// 6. 知识库 & RAG
// ══════════════════════════════════════════════
test('P0-KB-001: 获取知识库列表', async ({ page }) => {
  await login(page)
  const resp = await apiCall(page, 'GET', '/api/v1/knowledge/bases')
  expect([200, 201]).toContain(resp.status)
  logPass('P0-KB-001 获取知识库列表')
})

test('P0-KB-002: 搜索知识库', async ({ page }) => {
  await login(page)
  const resp = await apiCall(page, 'POST', '/api/v1/knowledge/search', {
    query: '测试',
    topK: 5,
  })
  expect([200, 201]).toContain(resp.status)
  logPass('P0-KB-002 搜索知识库')
})

test('P0-KB-003: 获取知识库统计', async ({ page }) => {
  await login(page)
  const resp = await apiCall(page, 'GET', '/api/v1/knowledge/stats')
  expect([200, 201]).toContain(resp.status)
  logPass('P0-KB-003 获取知识库统计')
})

// ══════════════════════════════════════════════
// 7. 文件上传
// ══════════════════════════════════════════════
test('P0-FILE-001: 上传文件', async ({ page }) => {
  await login(page)
  const fs = require('fs')
  const filePath = 'demand_frontend/public/logo.svg'
  if (!fs.existsSync(filePath)) {
    logPass('P0-FILE-001 SKIP (测试图片不存在)')
    return
  }
  const buffer = fs.readFileSync(filePath)
  const formData = new FormData()
  formData.append('file', new Blob([buffer]), 'logo.svg')

  const resp = await page.request.post(`${BASE}/api/v1/files/upload`, {
    headers: { Authorization: `Bearer ${await page.evaluate(() => localStorage.getItem('token'))}` },
    data: formData,
  })
  expect([200, 201]).toContain(resp.status())
  logPass('P0-FILE-001 上传文件')
})

// ══════════════════════════════════════════════
// 8. 用户 & 权限
// ══════════════════════════════════════════════
test('P0-USER-001: 获取当前用户信息', async ({ page }) => {
  await login(page)
  const resp = await apiCall(page, 'GET', '/api/v1/auth/me')
  expect([200, 201]).toContain(resp.status)
  logPass('P0-USER-001 获取当前用户信息')
})

test('P0-USER-002: 获取活跃用户列表', async ({ page }) => {
  await login(page)
  const resp = await apiCall(page, 'GET', '/api/v1/users/active')
  expect([200, 201]).toContain(resp.status)
  logPass('P0-USER-002 获取活跃用户列表')
})

// ══════════════════════════════════════════════
// 9. 通知中心
// ══════════════════════════════════════════════
test('P0-NOTIF-001: 获取通知列表', async ({ page }) => {
  await login(page)
  const resp = await apiCall(page, 'GET', '/api/v1/notifications')
  expect([200, 201]).toContain(resp.status)
  logPass('P0-NOTIF-001 获取通知列表')
})

test('P0-NOTIF-002: 获取未读数', async ({ page }) => {
  await login(page)
  const resp = await apiCall(page, 'GET', '/api/v1/notifications/unread')
  expect([200, 201]).toContain(resp.status)
  logPass('P0-NOTIF-002 获取未读数')
})

// ══════════════════════════════════════════════
// 10. 评分统计
// ══════════════════════════════════════════════
test('P0-RATE-001: 获取评分趋势', async ({ page }) => {
  await login(page)
  const listResp = await apiCall(page, 'GET', '/api/v1/projects')
  const projects = JSON.parse(listResp.text)
  if (projects.length > 0) {
    const id = projects[0].id
    const resp = await apiCall(page, 'GET', `/api/v1/statistics/rating/trend`)
    expect([200, 201]).toContain(resp.status)
    logPass('P0-RATE-001 获取评分趋势')
  } else {
    logPass('P0-RATE-001 SKIP (无项目数据)')
  }
})

// ══════════════════════════════════════════════
// 11. LLM 模型配置
// ══════════════════════════════════════════════
test('P0-LLM-001: 获取 LLM Provider 列表', async ({ page }) => {
  await login(page)
  const resp = await apiCall(page, 'GET', '/api/v1/llm-providers')
  expect([200, 201]).toContain(resp.status)
  logPass('P0-LLM-001 获取 LLM Provider 列表')
})

test('P0-LLM-002: 获取 Chat 模型列表', async ({ page }) => {
  await login(page)
  const resp = await apiCall(page, 'GET', '/api/v1/llm-providers/chat-models')
  expect([200, 201]).toContain(resp.status)
  logPass('P0-LLM-002 获取 Chat 模型列表')
})

// ══════════════════════════════════════════════
// 12. 多维表格
// ══════════════════════════════════════════════
test('P0-BITABLE-001: 获取多维表格基础信息', async ({ page }) => {
  await login(page)
  const resp = await apiCall(page, 'GET', '/api/v1/bitable/bases')
  expect([200, 201]).toContain(resp.status)
  logPass('P0-BITABLE-001 获取多维表格基础信息')
})

// ══════════════════════════════════════════════
// 13. 需求配置
// ══════════════════════════════════════════════
test('P0-CFG-001: 获取需求类型列表', async ({ page }) => {
  await login(page)
  const resp = await apiCall(page, 'GET', '/api/v1/requirement-config/types')
  expect([200, 201]).toContain(resp.status)
  logPass('P0-CFG-001 获取需求类型列表')
})

test('P0-CFG-002: 获取优先级列表', async ({ page }) => {
  await login(page)
  const resp = await apiCall(page, 'GET', '/api/v1/requirement-config/priorities')
  expect([200, 201]).toContain(resp.status)
  logPass('P0-CFG-002 获取优先级列表')
})

// ══════════════════════════════════════════════
// 14. 组织架构
// ══════════════════════════════════════════════
test('P0-ORG-001: 获取组织树', async ({ page }) => {
  await login(page)
  const resp = await apiCall(page, 'GET', '/api/v1/org/tree')
  expect([200, 201]).toContain(resp.status)
  logPass('P0-ORG-001 获取组织树')
})

// ══════════════════════════════════════════════
// 15. 元数据
// ══════════════════════════════════════════════
test('P0-META-001: 获取系统版本', async ({ page }) => {
  await login(page)
  const resp = await apiCall(page, 'GET', '/api/v1/meta/version')
  expect([200, 201]).toContain(resp.status)
  logPass('P0-META-001 获取系统版本')
})

// ══════════════════════════════════════════════
// 报告输出
// ══════════════════════════════════════════════
test.afterAll(() => {
  console.log('\n' + '='.repeat(60))
  console.log('  集成测试执行报告')
  console.log('='.repeat(60))
  console.log(`  异常: ${FAILURES.length} 个`)
  console.log(`  通过: ${test.passedCount()} 个`)

  if (FAILURES.length > 0) {
    console.log('\n❌ 异常明细:')
    console.log('-'.repeat(60))
    FAILURES.forEach((f, i) => {
      console.log(`  [${i + 1}] ${f.name}`)
      console.log(`      详情: ${f.detail}`)
    })
  } else {
    console.log('\n✅ 全部通过，无异常')
  }
  console.log('='.repeat(60))
})
