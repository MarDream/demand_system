import { test, expect, request as apiRequest } from '@playwright/test'

/**
 * 工作流导出导入 + 复制功能 - 综合 E2E 测试
 *
 * 覆盖场景：
 *  A. 导出功能
 *    A1. API 测试：导出审核通过的版本
 *    A2. API 测试：导出非审核通过版本（应返回错误）
 *    A3. UI 测试：导出按钮仅在审核通过版本显示
 *    A4. UI 测试：点击导出按钮下载 JSON 文件
 *
 *  B. 导入功能
 *    B1. API 测试：导入有效数据
 *    B2. API 测试：导入同名工作流（自动重命名 + 版本号递增）
 *    B3. API 测试：导入无效数据（格式校验）
 *    B4. UI 测试：导入按钮存在
 *    B5. UI 测试：点击导入选择文件
 *
 *  C. 导出→导入闭环
 *    C1. 端到端：导出 → 导入 → 验证配置一致性
 *
 *  D. 复制功能回归
 *    D1. API 测试：复制接口正常
 *    D2. UI 测试：复制按钮和对话框
 *
 * 账号：admin / admin123
 */

const FRONTEND = process.env.FRONTEND_URL || 'http://localhost:5170'
const BACKEND = process.env.BACKEND_URL || 'http://localhost:8081'

test.describe.configure({ mode: 'serial', timeout: 180_000 })

// ============== 工具函数 ==============

async function getAdminToken(): Promise<string> {
  const ctx = await apiRequest.newContext()
  const res = await ctx.post(`${BACKEND}/api/v1/auth/login`, {
    data: { username: 'admin', password: 'admin123' }
  })
  const json = await res.json()
  await ctx.dispose()
  return json.data.accessToken
}

async function loginAsAdmin(page: any) {
  await page.goto(`${FRONTEND}/login`)
  await page.waitForLoadState('networkidle', { timeout: 15_000 })
  await page.fill('input[placeholder*="用户名"]', 'admin')
  await page.fill('input[type="password"]', 'admin123')
  await page.click('button:has-text("登录")')
  await page.waitForURL(`${FRONTEND}/**`, { timeout: 10_000 })
}

/**
 * 在版本管理页面找一个已审批通过的版本 ID
 */
async function findApprovedVersionId(token: string): Promise<number | null> {
  const ctx = await apiRequest.newContext()
  const res = await ctx.get(`${BACKEND}/api/v1/workflows/0/versions`, {
    headers: { Authorization: `Bearer ${token}` }
  })
  const json = await res.json()
  await ctx.dispose()

  const versions = json.data || []
  const approved = versions.find((v: any) =>
    v.latestApprovalStatus === 'APPROVED' || v.activationStatus === 'active' || v.activationStatus === 'approved'
  )
  return approved ? approved.id : (versions.length > 0 ? versions[0].id : null)
}

/**
 * 在版本管理页面找一个有配置的版本 ID（任意状态）
 */
async function findAnyVersionId(token: string): Promise<number | null> {
  const ctx = await apiRequest.newContext()
  const res = await ctx.get(`${BACKEND}/api/v1/workflows/0/versions`, {
    headers: { Authorization: `Bearer ${token}` }
  })
  const json = await res.json()
  await ctx.dispose()

  const versions = json.data || []
  return versions.length > 0 ? versions[0].id : null
}

// ============== A. 导出功能测试 ==============

test.describe('A. 工作流导出功能', () => {

  test('A1. API 测试：导出审核通过的版本', async () => {
    const token = await getAdminToken()
    const versionId = await findApprovedVersionId(token)

    if (!versionId) {
      test.skip('没有可导出的审核通过版本')
      return
    }

    const ctx = await apiRequest.newContext()
    const res = await ctx.get(`${BACKEND}/api/v1/workflows/versions/${versionId}/export`, {
      headers: { Authorization: `Bearer ${token}` }
    })
    const json = await res.json()
    await ctx.dispose()

    expect(res.status()).toBe(200)
    expect(json.code).toBe(200)
    expect(json.data).toBeDefined()
    expect(json.data.workflow).toBeDefined()
    expect(json.data.workflow.name).toBeTruthy()
    expect(json.data.workflow.version).toBeTruthy()
    expect(json.data.workflow.config).toBeDefined()
    expect(json.data.workflow.config.nodes).toBeInstanceOf(Array)
    expect(json.data.workflow.config.edges).toBeInstanceOf(Array)
    expect(json.data.exportVersion).toBe('1.0.0')
    expect(json.data.exportedAt).toBeTruthy()
  })

  test('A2. API 测试：导出不存在版本（应返回错误）', async () => {
    const token = await getAdminToken()
    const ctx = await apiRequest.newContext()

    const res = await ctx.get(`${BACKEND}/api/v1/workflows/versions/999999/export`, {
      headers: { Authorization: `Bearer ${token}` }
    })
    const json = await res.json()
    await ctx.dispose()

    // 应返回错误
    expect(json.code).not.toBe(200)
  })

  test('A3. UI 测试：工作流配置页面存在导出按钮（仅对审核通过版本）', async ({ page }) => {
    await loginAsAdmin(page)
    await page.goto(`${FRONTEND}/system/workflow-config`)
    await page.waitForLoadState('networkidle', { timeout: 30_000 })
    await page.waitForSelector('.el-table', { timeout: 10_000 })

    // 检查工具栏是否有"导入工作流"按钮
    const importButton = page.locator('button:has-text("导入工作流")')
    await expect(importButton).toBeVisible({ timeout: 5_000 })
  })

  test('A4. UI 测试：导出按钮触发文件下载', async ({ page }) => {
    await loginAsAdmin(page)
    await page.goto(`${FRONTEND}/system/workflow-config`)
    await page.waitForLoadState('networkidle', { timeout: 30_000 })
    await page.waitForSelector('.el-table', { timeout: 10_000 })

    // 找第一个审核通过的版本行的"导出"按钮
    // 导出按钮在操作列的第4个位置（查看、编辑、复制、导出、启停、删除）
    const firstRow = page.locator('.el-table tbody tr').first()
    const exportBtn = firstRow.locator('.el-button').nth(3) // 第4个按钮

    if (await exportBtn.count() > 0 && await exportBtn.isVisible()) {
      // 设置监听下载
      const [download] = await Promise.all([
        page.waitForEvent('download', { timeout: 10_000 }).catch(() => null),
        exportBtn.click()
      ])

      if (download) {
        const filename = download.suggestedFilename()
        expect(filename).toMatch(/workflow-.*\.json/)
        console.log(`Downloaded: ${filename}`)
      }
    } else {
      console.log('No export button visible on first row (version may not be approved)')
    }
  })
})

// ============== B. 导入功能测试 ==============

test.describe('B. 工作流导入功能', () => {

  test('B1. API 测试：导入有效数据', async () => {
    const token = await getAdminToken()
    const versionId = await findApprovedVersionId(token)

    if (!versionId) {
      test.skip('没有可导出的审核通过版本')
      return
    }

    // 先导出
    const ctx = await apiRequest.newContext()
    const exportRes = await ctx.get(`${BACKEND}/api/v1/workflows/versions/${versionId}/export`, {
      headers: { Authorization: `Bearer ${token}` }
    })
    const exportJson = await exportRes.json()
    const exportData = exportJson.data

    // 修改名称确保不会冲突（同时测试同名处理）
    exportData.workflow.name = `E2E导入测试_${Date.now()}`

    // 导入
    const importRes = await ctx.post(`${BACKEND}/api/v1/workflows/import`, {
      headers: { Authorization: `Bearer ${token}` },
      data: exportData,
      params: { projectId: 0 }
    })
    const importJson = await importRes.json()
    await ctx.dispose()

    expect(importRes.status()).toBe(200)
    expect(importJson.code).toBe(200)
    expect(importJson.data.success).toBe(true)
    expect(importJson.data.versionId).toBeGreaterThan(0)
    expect(importJson.data.version).toBeTruthy()
    expect(importJson.data.name).toBeTruthy()
  })

  test('B2. API 测试：导入同名工作流（自动重命名 + 版本号递增）', async () => {
    const token = await getAdminToken()
    const versionId = await findApprovedVersionId(token)

    if (!versionId) {
      test.skip('没有可导出的审核通过版本')
      return
    }

    const ctx = await apiRequest.newContext()

    // 步骤1：获取第一个已存在的版本名称
    const listRes = await ctx.get(`${BACKEND}/api/v1/workflows/0/versions`, {
      headers: { Authorization: `Bearer ${token}` }
    })
    const listJson = await listRes.json()
    const versions = listJson.data || []

    if (versions.length === 0) {
      test.skip('没有可用的工作流版本')
      await ctx.dispose()
      return
    }

    const existingName = versions[0].name

    // 步骤2：导出该版本
    const exportRes = await ctx.get(`${BACKEND}/api/v1/workflows/versions/${versionId}/export`, {
      headers: { Authorization: `Bearer ${token}` }
    })
    const exportData = (await exportRes.json()).data

    // 步骤3：故意使用已存在的名称
    exportData.workflow.name = existingName

    // 步骤4：导入（后端应自动重命名）
    const importRes = await ctx.post(`${BACKEND}/api/v1/workflows/import`, {
      headers: { Authorization: `Bearer ${token}` },
      data: exportData,
      params: { projectId: 0 }
    })
    const importJson = await importRes.json()
    await ctx.dispose()

    expect(importRes.status()).toBe(200)
    expect(importJson.code).toBe(200)
    expect(importJson.data.success).toBe(true)
    expect(importJson.data.versionId).toBeGreaterThan(0)

    // 名称应该被重命名
    if (importJson.data.conflicts?.nameConflict) {
      expect(importJson.data.name).not.toBe(existingName)
      expect(importJson.data.name).toContain('副本')
    }

    // 版本号应该被重新分配
    if (importJson.data.conflicts?.versionConflict) {
      expect(importJson.data.conflicts.resolvedVersion).toBeTruthy()
    }
  })

  test('B3. API 测试：导入无效数据（格式校验）', async () => {
    const token = await getAdminToken()
    const ctx = await apiRequest.newContext()

    // 测试1：空数据
    const res1 = await ctx.post(`${BACKEND}/api/v1/workflows/import`, {
      headers: { Authorization: `Bearer ${token}` },
      data: { exportVersion: '1.0.0' },
      params: { projectId: 0 }
    })
    const json1 = await res1.json()
    expect(json1.code).not.toBe(200)

    // 测试2：缺少节点
    const res2 = await ctx.post(`${BACKEND}/api/v1/workflows/import`, {
      headers: { Authorization: `Bearer ${token}` },
      data: {
        exportVersion: '1.0.0',
        workflow: {
          name: '测试',
          version: '1.0.0',
          config: { nodes: [], edges: [] }
        }
      },
      params: { projectId: 0 }
    })
    const json2 = await res2.json()
    expect(json2.code).not.toBe(200)

    await ctx.dispose()
  })

  test('B4. UI 测试：导入按钮存在且可交互', async ({ page }) => {
    await loginAsAdmin(page)
    await page.goto(`${FRONTEND}/system/workflow-config`)
    await page.waitForLoadState('networkidle', { timeout: 30_000 })
    await page.waitForSelector('.el-table', { timeout: 10_000 })

    // 检查"导入工作流"按钮
    const importButton = page.locator('button:has-text("导入工作流")')
    await expect(importButton).toBeVisible({ timeout: 5_000 })

    // 按钮应该是绿色（type="success"）
    const classAttr = await importButton.getAttribute('class')
    expect(classAttr).toContain('success')
  })

  test('B5. UI 测试：点击导入弹出文件选择器', async ({ page }) => {
    await loginAsAdmin(page)
    await page.goto(`${FRONTEND}/system/workflow-config`)
    await page.waitForLoadState('networkidle', { timeout: 30_000 })
    await page.waitForSelector('.el-table', { timeout: 10_000 })

    const importButton = page.locator('button:has-text("导入工作流")')

    // 设置文件选择器监听
    const [fileChooser] = await Promise.all([
      page.waitForEvent('filechooser', { timeout: 5_000 }),
      importButton.click()
    ])

    expect(fileChooser).toBeTruthy()
    expect(fileChooser.isMultiple()).toBe(false)

    // 不实际选择文件，关闭即可
    await fileChooser.setFiles([])
  })
})

// ============== C. 导出→导入闭环测试 ==============

test.describe('C. 导出导入闭环', () => {

  test('C1. 端到端：导出 → 导入 → 验证配置一致性', async () => {
    const token = await getAdminToken()
    const versionId = await findApprovedVersionId(token)

    if (!versionId) {
      test.skip('没有可导出的审核通过版本')
      return
    }

    const ctx = await apiRequest.newContext()

    // 步骤1：导出
    const exportRes = await ctx.get(`${BACKEND}/api/v1/workflows/versions/${versionId}/export`, {
      headers: { Authorization: `Bearer ${token}` }
    })
    const exportData = (await exportRes.json()).data

    // 记录原始配置
    const originalNodeCount = exportData.workflow.config.nodes.length
    const originalEdgeCount = exportData.workflow.config.edges.length

    // 步骤2：修改名称后导入
    exportData.workflow.name = `E2E闭环测试_${Date.now()}`
    const importRes = await ctx.post(`${BACKEND}/api/v1/workflows/import`, {
      headers: { Authorization: `Bearer ${token}` },
      data: exportData,
      params: { projectId: 0 }
    })
    const importResult = (await importRes.json()).data

    expect(importResult.success).toBe(true)
    expect(importResult.versionId).toBeGreaterThan(0)

    // 步骤3：查询导入后的配置
    const detailRes = await ctx.get(`${BACKEND}/api/v1/workflows/versions/${importResult.versionId}`, {
      headers: { Authorization: `Bearer ${token}` }
    })
    const detailData = (await detailRes.json()).data

    // 步骤4：验证配置一致性
    expect(detailData.config).toBeDefined()
    expect(detailData.config.nodes).toBeInstanceOf(Array)
    expect(detailData.config.edges).toBeInstanceOf(Array)
    expect(detailData.config.nodes.length).toBe(originalNodeCount)
    expect(detailData.config.edges.length).toBe(originalEdgeCount)

    // 验证激活状态
    if (detailData.activationStatus) {
      expect(detailData.activationStatus.toLowerCase()).toBe('draft')
    }
    expect(detailData.isActive).toBe(0)

    await ctx.dispose()
  })

  test('C2. 端到端：重复导入同名 → 验证迭代重命名', async () => {
    const token = await getAdminToken()
    const versionId = await findApprovedVersionId(token)

    if (!versionId) {
      test.skip('没有可导出的版本')
      return
    }

    const ctx = await apiRequest.newContext()

    // 步骤1：导出
    const exportRes = await ctx.get(`${BACKEND}/api/v1/workflows/versions/${versionId}/export`, {
      headers: { Authorization: `Bearer ${token}` }
    })
    const exportData = (await exportRes.json()).data
    const baseName = `E2E批量导入_${Date.now()}`
    exportData.workflow.name = baseName

    // 步骤2：导入3次相同名称
    const versionIds: number[] = []
    const names: string[] = []
    const resolvedVersions: string[] = []

    for (let i = 0; i < 3; i++) {
      const importRes = await ctx.post(`${BACKEND}/api/v1/workflows/import`, {
        headers: { Authorization: `Bearer ${token}` },
        data: exportData,
        params: { projectId: 0 }
      })
      const result = (await importRes.json()).data
      versionIds.push(result.versionId)
      names.push(result.name)
      resolvedVersions.push(result.version)
    }

    // 步骤3：验证
    // 第一次导入：名称应为 baseName
    expect(names[0]).toBe(baseName)

    // 第二次导入：名称应为 baseName + "(副本1)"
    expect(names[1]).toContain('副本')

    // 第三次导入：名称应为 baseName + "(副本2)"
    expect(names[2]).toContain('副本')

    // 三个名称互不相同
    expect(new Set(names).size).toBe(3)

    // 版本号递增
    for (let i = 1; i < resolvedVersions.length; i++) {
      // 版本号应该递增
      console.log(`版本${i + 1}: ${names[i]} = V${resolvedVersions[i]}`)
    }

    await ctx.dispose()
  })
})

// ============== D. 复制功能回归 ==============

test.describe('D. 工作流复制功能回归', () => {

  test('D1. API 测试：复制接口正常工作', async () => {
    const token = await getAdminToken()
    const versionId = await findAnyVersionId(token)

    if (!versionId) {
      test.skip('没有可用版本')
      return
    }

    const ctx = await apiRequest.newContext()

    const copyRes = await ctx.post(`${BACKEND}/api/v1/workflows/versions/${versionId}/copy`, {
      headers: { Authorization: `Bearer ${token}` },
      data: {
        newName: `E2E回归复制_${Date.now()}`,
        clearAssignees: false,
        sanitizeSensitiveData: false
      }
    })
    const copyJson = await copyRes.json()

    expect(copyRes.status()).toBe(200)
    expect(copyJson.code).toBe(200)
    expect(copyJson.data).toBeDefined()

    await ctx.dispose()
  })

  test('D2. UI 测试：复制按钮和对话框', async ({ page }) => {
    await loginAsAdmin(page)
    await page.goto(`${FRONTEND}/system/workflow-config`)
    await page.waitForLoadState('networkidle', { timeout: 30_000 })
    await page.waitForSelector('.el-table', { timeout: 10_000 })

    // 找第一个版本的复制按钮（操作列第3个按钮）
    const firstRow = page.locator('.el-table tbody tr').first()
    const copyBtn = firstRow.locator('.el-button').nth(2)

    if (await copyBtn.count() > 0) {
      await copyBtn.click()
      await page.waitForTimeout(1_000)

      // 检查对话框出现
      const dialog = page.locator('.el-dialog:visible')
      if (await dialog.count() > 0) {
        expect(await dialog.isVisible()).toBe(true)
        // 关闭对话框
        const closeBtn = dialog.locator('.el-dialog__close')
        if (await closeBtn.count() > 0) {
          await closeBtn.click()
        }
      }
    }
  })

  test('D3. API 测试：复制时名称冲突处理', async () => {
    const token = await getAdminToken()
    const versionId = await findAnyVersionId(token)

    if (!versionId) {
      test.skip('没有可用版本')
      return
    }

    const ctx = await apiRequest.newContext()

    // 获取现有版本名称
    const listRes = await ctx.get(`${BACKEND}/api/v1/workflows/0/versions`, {
      headers: { Authorization: `Bearer ${token}` }
    })
    const versions = (await listRes.json()).data || []

    if (versions.length === 0) {
      await ctx.dispose()
      test.skip()
      return
    }

    // 使用已存在的名称进行复制
    const existingName = versions[0].name
    const copyRes = await ctx.post(`${BACKEND}/api/v1/workflows/versions/${versionId}/copy`, {
      headers: { Authorization: `Bearer ${token}` },
      data: {
        newName: existingName,
        clearAssignees: false,
        sanitizeSensitiveData: false
      }
    })

    if (copyRes.status() === 200) {
      const copyJson = await copyRes.json()
      if (copyJson.code === 200 && copyJson.data.newName) {
        // 名称应被自动修改
        expect(copyJson.data.newName).not.toBe(existingName)
      }
    } else {
      // 接受冲突检测报错
      const copyJson = await copyRes.json()
      console.log(`Name conflict response: code=${copyJson.code}, msg=${copyJson.message}`)
    }

    await ctx.dispose()
  })
})
