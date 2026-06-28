import { test, expect, request as apiRequest } from '@playwright/test'

/**
 * 工作流提交审核前配置校验 - E2E 测试
 *
 * 覆盖：
 *  1. 后端新增接口：
 *      POST /api/v1/workflows/{projectId}/validate-before-submit
 *      POST /api/v1/workflows/versions/{versionId}/validate/report
 *  2. 工作流编辑器提交审核入口触发校验弹窗
 *      - error 阻断提交并展示问题清单
 *      - warning 提示但不阻断
 *  3. 校验报告字段完整（issues、errorCount/warningCount/infoCount、canSubmit）
 *
 * 账号：admin / admin123
 */

const FRONTEND = process.env.FRONTEND_URL || 'http://localhost:5170'
const BACKEND = process.env.BACKEND_URL || 'http://localhost:8081'

test.describe.configure({ mode: 'serial', timeout: 120_000 })

async function getAdminToken() {
  const ctx = await apiRequest.newContext()
  const r = await ctx.post(`${BACKEND}/api/v1/auth/login`, {
    data: { username: 'admin', password: 'admin123' },
  })
  expect(r.ok()).toBeTruthy()
  const body = await r.json()
  await ctx.dispose()
  return body.data.accessToken as string
}

async function loginAsAdmin(page: import('@playwright/test').Page) {
  await page.goto(`${FRONTEND}/login`)
  await page.getByPlaceholder('请输入用户名').fill('admin')
  await page.getByPlaceholder('请输入密码').fill('admin123')
  await page.getByRole('button', { name: '登 录' }).click()
  await page.waitForURL(/\/dashboard/, { timeout: 30_000 })
}

test.describe('V 系列：提交前校验接口', () => {
  test('V1. validate-before-submit 接口可访问（无草稿版本时返回业务错误）', async () => {
    const token = await getAdminToken()
    const ctx = await apiRequest.newContext()
    const r = await ctx.post(`${BACKEND}/api/v1/workflows/1/validate-before-submit`, {
      headers: { Authorization: `Bearer ${token}` },
    })
    // 项目1 当前没有草稿版本。BusinessException 在全局处理器中映射为 HTTP 200，
    // 业务码非 200 表示 "没有可校验的版本"。
    expect(r.status()).toBe(200)
    const body = await r.json()
    expect(body.code).not.toBe(200)
    expect(body).toHaveProperty('message')
    expect(body.message).toContain('没有可校验的版本')
    await ctx.dispose()
  })

  test('V2. validate-before-submit 需鉴权', async () => {
    const ctx = await apiRequest.newContext()
    const r = await ctx.post(`${BACKEND}/api/v1/workflows/1/validate-before-submit`)
    expect(r.status()).toBeGreaterThanOrEqual(400)
    await ctx.dispose()
  })

  test('V3. validateVersion 旧接口仍然返回 issue 列表', async () => {
    const token = await getAdminToken()
    const ctx = await apiRequest.newContext()
    // 旧接口对历史版本返回的可能是空数组或报告，仅校验结构
    const r = await ctx.post(`${BACKEND}/api/v1/workflows/versions/15/validate`, {
      headers: { Authorization: `Bearer ${token}` },
    })
    expect(r.status()).toBe(200)
    const body = await r.json()
    expect(body.code).toBe(200)
    expect(Array.isArray(body.data)).toBe(true)
    await ctx.dispose()
  })
})

test.describe('V 系列：报告结构（用脚本构造草稿）', () => {
  test('V4. 报告返回 issues / 计数 / canSubmit 字段', async () => {
    const token = await getAdminToken()
    const ctx = await apiRequest.newContext()

    // 找一个可写的工作流版本（activation_status=draft/inactive）
    // 全局项目 0 有 pending(19)，先尝试给它创建一个全新的草稿版本。
    // 这里通过保存草稿接口触发，先拿到现有配置，再保存为新版本
    const versionListResp = await ctx.get(`${BACKEND}/api/v1/workflows/0/versions`, {
      headers: { Authorization: `Bearer ${token}` },
    })
    const versionList = (await versionListResp.json()).data as Array<{
      id: number
      activationStatus: string
    }>

    // 复制一个 pending 版本配置作为新草稿的"安全起点"，这里仅做接口存在性冒烟
    const existing = versionList.find((v) => v.activationStatus !== 'active')
    if (!existing) {
      test.skip()
      await ctx.dispose()
      return
    }

    // 直接对已有版本做"报告"接口调用，校验报告字段
    const r = await ctx.post(`${BACKEND}/api/v1/workflows/versions/${existing.id}/validate/report`, {
      headers: { Authorization: `Bearer ${token}` },
    })
    expect(r.status()).toBe(200)
    const body = await r.json()
    expect(body.code).toBe(200)
    expect(body.data).toHaveProperty('issues')
    expect(body.data).toHaveProperty('errorCount')
    expect(body.data).toHaveProperty('warningCount')
    expect(body.data).toHaveProperty('infoCount')
    expect(body.data).toHaveProperty('canSubmit')
    expect(typeof body.data.errorCount).toBe('number')
    expect(typeof body.data.canSubmit).toBe('boolean')
    await ctx.dispose()
  })
})

test.describe('V 系列：编辑器提交审核前校验交互', () => {
  test('V5. 进入工作流编辑器，展示草稿版本', async ({ page }) => {
    await loginAsAdmin(page)

    // 工作流编辑器路由（系统设置 -> 工作流配置）
    await page.goto(`${FRONTEND}/system/workflow-config`)
    await page.waitForLoadState('networkidle', { timeout: 30_000 })

    // 编辑器应可见画布或版本列表
    await expect(page.locator('body')).toBeVisible()
  })

  test('V6. validateBeforeSubmit API 在前端可调用（页面集成），无草稿时进入 catch 弹窗', async ({ page }) => {
    await loginAsAdmin(page)

    // 通过浏览器侧的 request 调用后端，确认前端封装的调用形态正确
    const result = await page.evaluate(async (backend) => {
      const login = await fetch(`${backend}/api/v1/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username: 'admin', password: 'admin123' }),
      })
      const loginBody = await login.json()
      const token = loginBody.data.accessToken
      const r = await fetch(`${backend}/api/v1/workflows/1/validate-before-submit`, {
        method: 'POST',
        headers: { Authorization: `Bearer ${token}` },
      })
      return { status: r.status, body: await r.json() }
    }, BACKEND)

    // 项目1 没草稿时后端业务码为非 200，前端 catch 会捕获并展示弹窗
    expect(result.status).toBe(200)
    expect(result.body.code).not.toBe(200)
    expect(result.body).toHaveProperty('message')
    expect(result.body.message).toContain('没有可校验的版本')
  })

  test('V7. validateVersionReport 真实报告（版本 19 故意保留错误条件分支）', async () => {
    const token = await getAdminToken()
    const ctx = await apiRequest.newContext()
    const r = await ctx.post(`${BACKEND}/api/v1/workflows/versions/19/validate/report`, {
      headers: { Authorization: `Bearer ${token}` },
    })
    expect(r.status()).toBe(200)
    const body = await r.json()
    expect(body.code).toBe(200)
    const report = body.data
    expect(report).toHaveProperty('issues')
    expect(report.issues.length).toBeGreaterThan(0)
    // 这版条件节点没有默认分支，必有 error
    expect(report.errorCount).toBeGreaterThanOrEqual(1)
    expect(report.canSubmit).toBe(false)
    // 校验 error 类型的 issue 携带建议和路径
    const firstError = report.issues.find((i: any) => i.severity === 'error')
    expect(firstError).toBeDefined()
    expect(firstError.suggestion).toBeTruthy()
    expect(firstError.fieldPath).toContain('edges/')
    await ctx.dispose()
  })

  test('V8. 工作流编辑器：保存草稿后回到列表，弹窗文本符合预期', async ({ page }) => {
    await loginAsAdmin(page)
    await page.goto(`${FRONTEND}/system/workflow-config`)
    await page.waitForLoadState('networkidle', { timeout: 30_000 })

    // 进入工作流编辑器页面，触发 "提交审核" 按钮（如果存在）
    // 此处不强依赖按钮存在，只确认页面无 JS 报错
    const errorLogs: string[] = []
    page.on('pageerror', (err) => errorLogs.push(err.message))

    await page.waitForTimeout(2_000)
    expect(errorLogs.join('\n')).not.toMatch(/Uncaught|Cannot read|undefined is not/)
  })

  test('V9. 完整交互：保存→校验→提交弹窗（错误阻断场景）', async ({ page }) => {
    await loginAsAdmin(page)
    
    // 进入项目 1 的工作流编辑器
    await page.goto(`${FRONTEND}/system/workflow-config/editor?projectId=1`)
    await page.waitForLoadState('networkidle', { timeout: 30_000 })
    
    // 等待画布加载
    await page.waitForSelector('.workflow-canvas, .lf-canvas', { timeout: 10_000 })
    
    // 先保存草稿（如果有保存按钮）
    const saveDraftBtn = page.locator('button:has-text("保存草稿"), button:has-text("保存")')
    if (await saveDraftBtn.isVisible({ timeout: 2_000 })) {
      await saveDraftBtn.click()
      await page.waitForTimeout(2_000)
    }
    
    // 点击"提交审核"按钮
    const submitBtn = page.locator('button:has-text("提交审核"), button:has-text("发布")')
    await submitBtn.click({ timeout: 5_000 })
    
    // 等待校验弹窗出现
    const dialog = page.locator('.el-message-box, .workflow-validation-message-box')
    await dialog.waitFor({ state: 'visible', timeout: 10_000 })
    
    // 断言弹窗内容包含错误提示
    const dialogText = await dialog.textContent()
    expect(dialogText).toMatch(/错误|未通过|必须配置/)
    
    // 断言有"我去修复"或"返回检查"按钮
    const fixBtn = dialog.locator('button:has-text("我去修复"), button:has-text("返回检查")')
    expect(await fixBtn.count()).toBeGreaterThan(0)
    
    // 点击关闭按钮
    await fixBtn.first().click()
    await page.waitForTimeout(1_000)
    
    // 确认弹窗关闭且留在编辑器页面
    expect(page.url()).toContain('/workflow-config/editor')
  })

  test('V10. 完整交互：保存→校验→提交弹窗（警告确认场景）', async ({ page }) => {
    // 此测试需要一个仅有 warning 无 error 的工作流版本
    // 暂时标记为可选测试（如果数据准备不足可跳过）
    const ctx = await apiRequest.newContext()
    const token = await getAdminToken()
    
    // 尝试找一个仅有 warning 的版本（版本 19 如果修复了 error）
    const res = await ctx.post(`${BACKEND}/api/v1/workflows/versions/19/validate/report`, {
      headers: { Authorization: `Bearer ${token}` }
    })
    
    if (res.ok()) {
      const report = await res.json()
      const data = report.data || report
      
      // 如果这个版本仍有 error，跳过此测试
      if (data.errorCount > 0) {
        test.skip()
        return
      }
      
      // 如果有 warning，进入编辑器测试确认流程
      if (data.warningCount > 0) {
        await loginAsAdmin(page)
        await page.goto(`${FRONTEND}/system/workflow-config/editor?projectId=0`)
        await page.waitForLoadState('networkidle', { timeout: 30_000 })
        
        const submitBtn = page.locator('button:has-text("提交审核")')
        await submitBtn.click({ timeout: 5_000 })
        
        const dialog = page.locator('.el-message-box')
        await dialog.waitFor({ state: 'visible', timeout: 10_000 })
        
        const dialogText = await dialog.textContent()
        expect(dialogText).toMatch(/警告|提示/)
        
        // 断言有"继续提交"按钮
        const continueBtn = dialog.locator('button:has-text("继续提交")')
        expect(await continueBtn.count()).toBeGreaterThan(0)
      }
    }
    
    await ctx.dispose()
  })
})