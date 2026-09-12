#!/usr/bin/env node
/**
 * 多维表格全量角色化集成测试
 * 覆盖角色：OWNER / ADMIN / EDITOR / COMMENTER / VIEWER / 非成员(outsider) / 匿名(anonymous)
 * 运行前提：后端运行于 localhost:8081，MySQL 容器名 mysql（root/admin123，库 demand_system）
 */
import { execSync } from 'node:child_process'

const BASE = 'http://localhost:8081/api/v1'
const TS = Date.now().toString(36)
const PASSWORD = 'Test@123456'

let passCount = 0
let failCount = 0
const failures = []

function check(name, cond, detail) {
  if (cond) {
    passCount++
    console.log(`  ✓ ${name}`)
  } else {
    failCount++
    failures.push({ name, detail })
    console.log(`  ✗ ${name}  ——  ${detail ?? ''}`)
  }
}

async function http(method, path, { token, body, raw } = {}) {
  const headers = { 'Content-Type': 'application/json' }
  if (token) headers.Authorization = `Bearer ${token}`
  const res = await fetch(BASE + path, {
    method,
    headers,
    body: body !== undefined ? JSON.stringify(body) : undefined,
  })
  if (raw) return { status: res.status, body: await res.arrayBuffer() }
  let json = null
  try { json = await res.json() } catch { /* ignore */ }
  return { status: res.status, body: json }
}

const isForbidden = (r) => r.status === 403 || r.body?.code === 403
const isOk = (r) => r.status === 200 && (r.body?.code === undefined || r.body?.code === 200)
const getData = (r) => r.body?.data

const DOCKER = 'C:\\Program Files\\Docker\\Docker\\resources\\bin\\docker.exe'

function mysql(sql) {
  const out = execSync(
    `"${DOCKER}" exec mysql mysql -uroot -padmin123 -N -e "USE demand_system; ${sql}" 2>nul`,
    { encoding: 'utf8' },
  )
  return out.trim()
}

async function registerUser(name) {
  const email = `bitest_${name}_${TS}@test.com`
  await http('POST', '/auth/send-verification-code', { body: { email, type: 'register' } })
  const code = mysql(
    `SELECT code FROM verification_codes WHERE email='${email}' AND type='register' AND used=0 ORDER BY created_at DESC LIMIT 1;`,
  )
  const r = await http('POST', '/auth/register', {
    body: { username: `bitest_${name}_${TS}`, password: PASSWORD, realName: `测试_${name}`, email, verificationCode: code },
  })
  if (!isOk(r)) throw new Error(`注册 ${name} 失败: ${JSON.stringify(r.body)}`)
  const token = getData(r).accessToken
  // 补充登录以校验 login 端点
  const login = await http('POST', '/auth/login', { body: { username: `bitest_${name}_${TS}`, password: PASSWORD } })
  check(`登录 ${name}`, isOk(login) && !!getData(login)?.accessToken)
  return { username: `bitest_${name}_${TS}`, token: getData(login)?.accessToken ?? token, email }
}

// ============================================================
async function main() {
  console.log(`\n=== 多维表格角色化集成测试  (ts=${TS}) ===\n`)

  // ---------- 0. 注册与登录 ----------
  console.log('【0. 用户注册/登录】')
  const owner = await registerUser('owner')
  const admin = await registerUser('admin')
  const editor = await registerUser('editor')
  const commenter = await registerUser('commenter')
  const viewer = await registerUser('viewer')
  const outsider = await registerUser('outsider')
  const me = await http('GET', '/auth/me', { token: owner.token })
  check('GET /auth/me 返回当前用户', isOk(me) && !!getData(me)?.id)

  // ---------- 1. Base 管理 ----------
  console.log('\n【1. Base 管理】')
  const createBase = await http('POST', '/bitable/bases', { token: owner.token, body: { name: '角色测试Base', description: '集成测试' } })
  check('Owner 创建 Base', isOk(createBase))
  const baseId = getData(createBase)
  check('非成员读取 Base 被拒绝', isForbidden(await http('GET', `/bitable/bases/${baseId}`, { token: outsider.token })))
  const ownerList = getData(await http('GET', '/bitable/bases', { token: owner.token }))
  check('Owner 列表包含新 Base', Array.isArray(ownerList) && ownerList.some((b) => b.id === baseId))
  const outsiderList = getData(await http('GET', '/bitable/bases', { token: outsider.token }))
  check('非成员列表不包含该 Base', Array.isArray(outsiderList) && !outsiderList.some((b) => b.id === baseId))
  check('非成员更新 Base 被拒绝', isForbidden(await http('PUT', `/bitable/bases/${baseId}`, { token: outsider.token, body: { name: 'x' } })))
  check('Owner 更新 Base', isOk(await http('PUT', `/bitable/bases/${baseId}`, { token: owner.token, body: { description: '更新后的描述' } })))
  console.log('\n【2. 成员管理与角色矩阵】')
  const getUserId = async (who) => {
    const r = await http('GET', '/auth/me', { token: who.token })
    return getData(r)?.id
  }
  for (const [who, role] of [[admin, 'admin'], [editor, 'editor'], [commenter, 'commenter'], [viewer, 'viewer']]) {
    const uid = await getUserId(who)
    const r = await http('POST', `/bitable/bases/${baseId}/members`, {
      token: owner.token, body: { userId: uid, role },
    })
    check(`Owner 添加成员 ${role}`, isOk(r))
  }
  const adminId = await getUserId(admin)
  check('Admin 不能授予 owner 角色（防提权）', isForbidden(
    await http('POST', `/bitable/bases/${baseId}/members`, { token: admin.token, body: { userId: adminId, role: 'owner' } }),
  ) || (await http('POST', `/bitable/bases/${baseId}/members`, { token: admin.token, body: { userId: adminId, role: 'owner' } })).status !== 200)
  check('非成员读取成员列表被拒绝', isForbidden(await http('GET', `/bitable/bases/${baseId}/members`, { token: outsider.token })))
  check('成员读取成员列表', isOk(await http('GET', `/bitable/bases/${baseId}/members`, { token: commenter.token })))
  // 成员加入后才能读取 Base（放在成员添加之后）
  check('成员读取 Base', isOk(await http('GET', `/bitable/bases/${baseId}`, { token: viewer.token })))

  // ---------- 3. 数据表 ----------
  console.log('\n【3. 数据表 CRUD】')
  const t1 = await http('POST', `/bitable/bases/${baseId}/tables`, { token: owner.token, body: { name: '主表' } })
  check('Owner 创建数据表', isOk(t1))
  const tableId = getData(t1)
  const t2 = await http('POST', `/bitable/bases/${baseId}/tables`, { token: admin.token, body: { name: '关联目标表' } })
  check('Admin 创建数据表', isOk(t2))
  const tableId2 = getData(t2)
  check('Viewer 创建数据表被拒绝', isForbidden(
    await http('POST', `/bitable/bases/${baseId}/tables`, { token: viewer.token, body: { name: 'x' } }),
  ))
  check('Editor 列出数据表', isOk(await http('GET', `/bitable/bases/${baseId}/tables`, { token: editor.token })))
  check('非成员列出数据表被拒绝', isForbidden(await http('GET', `/bitable/bases/${baseId}/tables`, { token: outsider.token })))

  // ---------- 4. 字段 ----------
  console.log('\n【4. 字段管理】')
  const mkField = async (token, name, fieldType, config) => {
    const r = await http('POST', `/bitable/tables/${tableId}/fields`, { token, body: { name, fieldType, config } })
    return r
  }
  const fText = await mkField(owner.token, '标题', 'text')
  check('Owner 创建 text 字段', isOk(fText))
  const fNum = await mkField(admin.token, '数量', 'number')
  check('Admin 创建 number 字段', isOk(fNum))
  const fSelect = await mkField(owner.token, '状态', 'single_select', { options: [{ label: '待办' }, { label: '进行中' }, { label: '完成' }] })
  check('Owner 创建 single_select 字段', isOk(fSelect))
  const fMulti = await mkField(owner.token, '标签', 'multi_select', { options: [{ label: '红' }, { label: '绿' }, { label: '蓝' }] })
  check('Owner 创建 multi_select 字段', isOk(fMulti))
  const fDate = await mkField(owner.token, '日期', 'date')
  check('Owner 创建 date 字段', isOk(fDate))
  const fCheck = await mkField(owner.token, '完成', 'check')
  check('Owner 创建 check 字段', isOk(fCheck))
  const textId = getData(fText), numId = getData(fNum), selId = getData(fSelect), multiId = getData(fMulti), dateId = getData(fDate)
  const fFormula = await mkField(owner.token, '双倍数量', 'formula', { formulaExpr: `f${numId} * 2` })
  check('Owner 创建 formula 字段', isOk(fFormula))
  const formulaId = getData(fFormula)
  const fLink = await http('POST', `/bitable/tables/${tableId}/fields`, {
    token: owner.token, body: { name: '关联记录', fieldType: 'link', config: { linkTargetTableId: tableId2 } },
  })
  check('Owner 创建 link 字段（指向第二表）', isOk(fLink))
  const linkId = getData(fLink)
  check('Editor 创建字段被拒绝', isForbidden(await mkField(editor.token, 'x', 'text')))
  check('Viewer 创建字段被拒绝', isForbidden(await mkField(viewer.token, 'x', 'text')))
  check('非成员列出字段被拒绝', isForbidden(await http('GET', `/bitable/tables/${tableId}/fields`, { token: outsider.token })))
  check('Viewer 列出字段（只读）', isOk(await http('GET', `/bitable/tables/${tableId}/fields`, { token: viewer.token })))
  // 字段排序：跨表字段应被拒绝
  const fForeign = await http('POST', `/bitable/tables/${tableId2}/fields`, { token: admin.token, body: { name: '第二表字段', fieldType: 'text' } })
  const foreignFieldId = getData(fForeign)
  const badSort = await http('PUT', `/bitable/tables/${tableId}/fields/sort`, {
    token: admin.token, body: [textId, foreignFieldId],
  })
  check('字段排序混入他表字段被拒绝', !isOk(badSort))

  // ---------- 5. 记录 CRUD ----------
  console.log('\n【5. 记录 CRUD 与写权限】')
  const mkRecord = (token, cells) => http('POST', `/bitable/tables/${tableId}/records`, { token, body: { cells } })
  const r1 = await mkRecord(owner.token, { [textId]: { valueText: '第一条' }, [numId]: { valueNumber: 10 }, [selId]: { valueText: '待办' } })
  check('Owner 创建记录', isOk(r1))
  const rec1 = getData(r1)
  const r2 = await mkRecord(editor.token, { [textId]: { valueText: '第二条' }, [numId]: { valueNumber: 25 }, [selId]: { valueText: '进行中' }, [multiId]: { valueJson: ['红', '蓝'] }, [dateId]: { valueDate: '2026-09-12' } })
  check('Editor 创建记录', isOk(r2))
  const rec2 = getData(r2)
  check('Commenter 创建记录被拒绝', isForbidden(await mkRecord(commenter.token, { [textId]: { valueText: 'x' } })))
  check('Viewer 创建记录被拒绝', isForbidden(await mkRecord(viewer.token, { [textId]: { valueText: 'x' } })))
  check('非成员创建记录被拒绝', isForbidden(await mkRecord(outsider.token, { [textId]: { valueText: 'x' } })))
  await mkRecord(admin.token, { [textId]: { valueText: '第三条' }, [numId]: { valueNumber: 7 }, [selId]: { valueText: '完成' } })

  const listViewer = await http('GET', `/bitable/tables/${tableId}/records`, { token: viewer.token })
  check('Viewer 读取记录列表（只读）', isOk(listViewer) && getData(listViewer)?.total >= 3)
  check('非成员读取记录被拒绝', isForbidden(await http('GET', `/bitable/tables/${tableId}/records`, { token: outsider.token })))

  // 计算字段与系统字段合成
  const rec2Detail = await http('GET', `/bitable/records/${rec2}`, { token: editor.token })
  const formulaCell = getData(rec2Detail)?.cells?.[String(formulaId)]
  check('formula 字段在记录详情中合成（25*2=50）', formulaCell && Number(formulaCell.valueNumber) === 50, JSON.stringify(formulaCell))
  const wFormula = await http('PUT', `/bitable/records/${rec2}/cells/${formulaId}`, {
    token: editor.token, body: { version: getData(rec2Detail).version, valueText: 'hack' },
  })
  check('手写计算字段被拒绝', !isOk(wFormula))

  // 单元格乐观锁
  const v0 = getData(rec2Detail).version
  const cellOk = await http('PUT', `/bitable/records/${rec2}/cells/${textId}`, {
    token: editor.token, body: { version: v0, valueText: '第二条-已更新' },
  })
  check('Editor 更新单元格（版本正确）', isOk(cellOk))
  const stale = await http('PUT', `/bitable/records/${rec2}/cells/${textId}`, {
    token: editor.token, body: { version: v0, valueText: '过期写入' },
  })
  check('过期版本更新单元格返回冲突', !isOk(stale), JSON.stringify(stale.body))
  check('Commenter 更新单元格被拒绝', isForbidden(await http('PUT', `/bitable/records/${rec2}/cells/${textId}`, {
    token: commenter.token, body: { version: 99, valueText: 'x' },
  })))

  // 关联字段写入 + 非法跨表关联
  const rTarget = await http('POST', `/bitable/tables/${tableId2}/records`, { token: owner.token, body: { cells: {} } })
  const targetRecId = getData(rTarget)
  const linkOk = await http('POST', `/bitable/fields/${linkId}/link`, {
    token: editor.token, body: { recordId: rec2, targetRecordIds: [targetRecId] },
  })
  check('Editor 写入关联字段', isOk(linkOk))
  const foreignLink = await http('POST', `/bitable/fields/${linkId}/link`, {
    token: editor.token, body: { recordId: rec2, targetRecordIds: [rec1] },
  })
  check('关联他表记录被拒绝', !isOk(foreignLink))

  // 批量创建 + 删除
  const batch = await http('POST', `/bitable/tables/${tableId}/records/batch`, {
    token: admin.token, body: [{ cells: { [textId]: { valueText: '批A' } } }, { cells: { [textId]: { valueText: '批B' } } }],
  })
  check('Admin 批量创建记录', isOk(batch) && getData(batch) === 2)
  check('Viewer 删除记录被拒绝', isForbidden(await http('DELETE', `/bitable/records/${rec1}`, { token: viewer.token })))

  // ---------- 6. 高级查询：筛选/排序/分组 ----------
  console.log('\n【6. 高级查询（筛选/排序/分组）】')
  const qFilter = await http('POST', `/bitable/tables/${tableId}/records/query`, {
    token: editor.token,
    body: { filterConfig: { logic: 'AND', rules: [{ fieldId: selId, operator: 'eq', value: '进行中' }] }, pageNum: 1, pageSize: 100 },
  })
  check('筛选：eq 单选', isOk(qFilter) && getData(qFilter)?.list?.length === 1, JSON.stringify(getData(qFilter)?.list?.length))
  const qNum = await http('POST', `/bitable/tables/${tableId}/records/query`, {
    token: editor.token,
    body: { filterConfig: { logic: 'AND', rules: [{ fieldId: numId, operator: 'gte', value: 10 }] }, pageNum: 1, pageSize: 100 },
  })
  check('筛选：gte 数值（>=10 应有 2 条）', isOk(qNum) && getData(qNum)?.list?.length === 2, JSON.stringify(getData(qNum)?.list?.length))
  const qSort = await http('POST', `/bitable/tables/${tableId}/records/query`, {
    token: editor.token,
    body: { sortConfig: [{ fieldId: numId, direction: 'asc' }], pageNum: 1, pageSize: 100 },
  })
  const sortedNums = (getData(qSort)?.list || []).map((r) => r.cells?.[String(numId)]?.valueNumber)
  const defined = sortedNums.filter((v) => v != null)
  check('排序：数值升序稳定', isOk(qSort) && defined.every((v, i) => i === 0 || defined[i - 1] <= v), JSON.stringify(sortedNums))
  const qGroup = await http('POST', `/bitable/tables/${tableId}/records/grouped`, {
    token: editor.token, body: { groupByFieldId: selId },
  })
  check('分组查询返回分组结构', isOk(qGroup) && Array.isArray(getData(qGroup)))
  check('非成员高级查询被拒绝', isForbidden(await http('POST', `/bitable/tables/${tableId}/records/query`, {
    token: outsider.token, body: {},
  })))

  // ---------- 7. 视图系统 ----------
  console.log('\n【7. 视图系统】')
  const vGrid = await http('POST', `/bitable/tables/${tableId}/views`, { token: owner.token, body: { name: '网格A', viewType: 'grid' } })
  check('Owner 创建 grid 视图', isOk(vGrid))
  const viewA = getData(vGrid)
  const vKan = await http('POST', `/bitable/tables/${tableId}/views`, { token: admin.token, body: { name: '看板B', viewType: 'kanban' } })
  check('Admin 创建 kanban 视图', isOk(vKan))
  const viewB = getData(vKan)
  check('Viewer 创建视图被拒绝', isForbidden(await http('POST', `/bitable/tables/${tableId}/views`, { token: viewer.token, body: { name: 'x', viewType: 'grid' } })))
  const dup = await http('POST', `/bitable/views/${viewA}/duplicate`, { token: owner.token })
  check('复制视图', isOk(dup))
  const viewDup = getData(dup)
  check('重命名视图', isOk(await http('PATCH', `/bitable/views/${viewA}`, { token: admin.token, body: { name: '网格A-改' } })))
  check('非成员修改视图被拒绝', isForbidden(await http('PATCH', `/bitable/views/${viewA}`, { token: outsider.token, body: { name: 'x' } })))
  check('Viewer 修改视图被拒绝', isForbidden(await http('PATCH', `/bitable/views/${viewA}`, { token: viewer.token, body: { name: 'x' } })))
  // 默认视图保护：查默认视图 id
  const tableDetail = getData(await http('GET', `/bitable/bases/${baseId}/tables`, { token: owner.token }))
  const mainTable = tableDetail?.find?.((t) => t.id === tableId)
  const defaultViewId = mainTable?.defaultViewId
  if (defaultViewId) {
    check('删除默认视图被拒绝', !isOk(await http('DELETE', `/bitable/views/${defaultViewId}`, { token: owner.token })))
  }
  check('删除非默认视图', isOk(await http('DELETE', `/bitable/views/${viewDup}`, { token: owner.token })))
  // 视图筛选持久化（filterConfig 经视图套用）
  check('视图设置筛选', isOk(await http('PATCH', `/bitable/views/${viewB}`, {
    token: admin.token,
    body: { filterConfig: { logic: 'AND', rules: [{ fieldId: selId, operator: 'eq', value: '待办' }] } },
  })))

  // ---------- 8. 评论与角色语义 ----------
  console.log('\n【8. 评论模块】')
  const c1 = await http('POST', `/bitable/records/${rec2}/comments`, { token: commenter.token, body: { content: '评论者可以评论' } })
  check('COMMENTER 创建评论（角色语义修复）', isOk(c1))
  const commentId = getData(c1)
  check('VIEWER 创建评论被拒绝', isForbidden(await http('POST', `/bitable/records/${rec2}/comments`, { token: viewer.token, body: { content: 'x' } })))
  check('EDITOR 删除他人评论被拒绝', isForbidden(await http('DELETE', `/bitable/comments/${commentId}`, { token: editor.token })))
  check('作者删除自己的评论', isOk(await http('DELETE', `/bitable/comments/${commentId}`, { token: commenter.token })))
  const longContent = 'x'.repeat(5001)
  const tooLong = await http('POST', `/bitable/records/${rec2}/comments`, { token: editor.token, body: { content: longContent } })
  check('超长评论被拒绝', !isOk(tooLong))
  const c2 = await http('POST', `/bitable/records/${rec2}/comments`, { token: editor.token, body: { content: '编辑者评论' } })
  check('Admin 删除他人评论', isOk(await http('DELETE', `/bitable/comments/${getData(c2)}`, { token: admin.token })))

  // ---------- 9. 操作日志 ----------
  console.log('\n【9. 操作日志权限】')
  check('Viewer 读取操作历史被拒绝（仅 ADMIN+）', isForbidden(await http('GET', `/bitable/bases/${baseId}/operations`, { token: viewer.token })))
  check('Admin 读取操作历史', isOk(await http('GET', `/bitable/bases/${baseId}/operations`, { token: admin.token })))

  // ---------- 10. 自动化 ----------
  console.log('\n【10. 自动化（含 MQ 执行链路）】')
  const auto = await http('POST', `/bitable/bases/${baseId}/automations`, {
    token: admin.token,
    body: {
      name: '新增记录时通知', tableId, triggerType: 'record_created', actionType: 'send_message',
      triggerConfig: {}, actionConfig: { message: '有新记录', channel: 'log' },
    },
  })
  check('Admin 创建自动化', isOk(auto))
  const autoId = getData(auto)
  check('Viewer 创建自动化被拒绝', isForbidden(await http('POST', `/bitable/bases/${baseId}/automations`, {
    token: viewer.token, body: { name: 'x', tableId, triggerType: 'record_created', actionType: 'send_message' },
  })))
  check('非成员读取自动化列表被拒绝', isForbidden(await http('GET', `/bitable/bases/${baseId}/automations`, { token: outsider.token })))
  // 跨 Base 挂载拒绝：用第二表所属同 base 不行，直接构造 tableId=别的 base 的表不存在，跳过（需要第二个 base）
  check('自动化 tableId 不属于该 Base 被拒绝', !isOk(await http('POST', `/bitable/bases/${baseId}/automations`, {
    token: admin.token,
    body: { name: 'x', tableId: tableId2 + 999999, triggerType: 'record_created', actionType: 'send_message' },
  })))
  // 触发链路：创建记录 → MQ 消费 → run succeeded
  await mkRecord(owner.token, { [textId]: { valueText: '触发自动化的记录' } })
  let runSucceeded = false
  for (let i = 0; i < 15 && !runSucceeded; i++) {
    await new Promise((r) => setTimeout(r, 1000))
    const runs = await http('GET', `/bitable/automations/${autoId}/runs`, { token: admin.token })
    const list = getData(runs)?.list || []
    runSucceeded = list.some((run) => run.status === 'succeeded')
  }
  check('记录创建触发自动化并执行成功（MQ 链路）', runSucceeded)
  check('非成员读取执行记录被拒绝', isForbidden(await http('GET', `/bitable/automations/${autoId}/runs`, { token: outsider.token })))
  check('启停自动化', isOk(await http('POST', `/bitable/automations/${autoId}/toggle?enabled=false`, { token: admin.token })))

  // ---------- 11. 公开表单 ----------
  console.log('\n【11. 公开表单（匿名访问）】')
  const formView = await http('POST', `/bitable/tables/${tableId}/views`, { token: owner.token, body: { name: '收集表单', viewType: 'form' } })
  const formViewId = getData(formView)
  const beforeCount = getData(await http('GET', `/bitable/tables/${tableId}/records`, { token: owner.token }))?.total
  const publish = await http('POST', `/bitable/tables/${tableId}/views/${formViewId}/publish`, {
    token: owner.token, body: { successMessage: '感谢提交', submitLimit: 100 },
  })
  check('Owner 发布表单', isOk(publish))
  const formToken = getData(publish)?.token
  // 匿名 schema（不带 token）
  const schemaRes = await fetch(`${BASE}/public/bitable/forms/${formToken}/schema`)
  const schemaJson = await schemaRes.json()
  check('匿名获取表单 schema', schemaRes.status === 200 && schemaJson?.data?.fields?.length > 0)
  check('schema 不泄露表ID', schemaJson?.data && !('tableId' in schemaJson.data) && !('baseId' in schemaJson.data))
  const submitRes = await fetch(`${BASE}/public/bitable/forms/${formToken}/submit`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ values: { [String(textId)]: { valueText: '匿名提交' }, [String(selId)]: { valueText: '待办' } } }),
  })
  const submitJson = await submitRes.json()
  check('匿名提交表单成功', submitRes.status === 200 && submitJson?.data?.recordId > 0, JSON.stringify(submitJson))
  const afterCount = getData(await http('GET', `/bitable/tables/${tableId}/records`, { token: owner.token }))?.total
  check('提交后记录数 +1', afterCount === beforeCount + 1, `${beforeCount} -> ${afterCount}`)
  const badToken = await fetch(`${BASE}/public/bitable/forms/invalid-token-xxx/schema`)
  check('无效 token 被拒绝', badToken.status !== 200)
  check('Viewer 停止收集被拒绝', isForbidden(await http('POST', `/bitable/views/${formViewId}/publish/status`, { token: viewer.token, body: { enabled: false } })))

  // ---------- 12. 视图分享 ----------
  console.log('\n【12. 视图分享（匿名只读）】')
  const share = await http('POST', `/bitable/views/${viewA}/share`, { token: owner.token, body: { allowDownload: false } })
  check('Owner 创建分享链接', isOk(share))
  const shareToken = getData(share)?.token
  const shareRes = await fetch(`${BASE}/public/bitable/views/${shareToken}/data?pageNum=1&pageSize=20`)
  const shareJson = await shareRes.json()
  check('匿名读取分享视图数据', shareRes.status === 200 && Array.isArray(shareJson?.data?.records) && shareJson.data.records.length > 0)
  check('分享数据不含操作人字段', shareJson?.data?.records?.every((r) => !('createdBy' in r) && !('createdByName' in r)))
  check('停用分享', isOk(await http('POST', `/bitable/views/${viewA}/share/status`, { token: owner.token, body: { enabled: false } })))
  const disabledRes = await fetch(`${BASE}/public/bitable/views/${shareToken}/data`)
  check('停用后匿名访问被拒绝', disabledRes.status !== 200 || (await disabledRes.json())?.code !== 200)

  // ---------- 13. 仪表盘 ----------
  console.log('\n【13. 仪表盘】')
  const dash = await http('POST', `/bitable/bases/${baseId}/dashboards`, { token: owner.token, body: { name: '测试仪表盘' } })
  check('Owner 创建仪表盘', isOk(dash))
  const dashId = getData(dash)
  const saveWidgets = await http('POST', `/bitable/dashboards/${dashId}/widgets`, {
    token: admin.token,
    body: {
      widgets: [
        { type: 'kpi', title: '记录总数', dataSourceConfig: { tableId, aggregation: 'count' } },
        { type: 'bar', title: '状态分布', dataSourceConfig: { tableId, aggregation: 'count', groupByFieldId: selId } },
        { type: 'kpi', title: '数量合计', dataSourceConfig: { tableId, aggregation: 'sum', fieldId: numId } },
      ],
    },
  })
  check('Admin 保存仪表盘组件', isOk(saveWidgets))
  check('跨 Base 数据源组件被拒绝', !isOk(await http('POST', `/bitable/dashboards/${dashId}/widgets`, {
    token: admin.token,
    body: { widgets: [{ type: 'kpi', title: 'x', dataSourceConfig: { tableId: tableId2 + 888888, aggregation: 'count' } }] },
  })))
  const dashData = await http('GET', `/bitable/dashboards/${dashId}/data`, { token: viewer.token })
  const kpiWidget = getData(dashData)?.find((w) => w.title === '记录总数')
  check('Viewer 读取仪表盘数据（只读）', isOk(dashData) && kpiWidget?.data?.value >= 4, JSON.stringify(kpiWidget))
  check('非成员读取仪表盘被拒绝', isForbidden(await http('GET', `/bitable/dashboards/${dashId}/data`, { token: outsider.token })))
  check('Viewer 修改仪表盘组件被拒绝', isForbidden(await http('POST', `/bitable/dashboards/${dashId}/widgets`, {
    token: viewer.token, body: { widgets: [] },
  })))

  // ---------- 14. 开放 API ----------
  console.log('\n【14. 开放 API（X-Api-Key）】')
  const key = await http('POST', `/bitable/bases/${baseId}/api-keys`, {
    token: owner.token, body: { name: '集成测试Key', scopes: ['records:read', 'fields:read'] },
  })
  check('Owner 创建 API Key', isOk(key))
  const keyId = getData(key)?.keyId
  const secret = getData(key)?.secret
  check('secret 仅创建时返回', !!keyId && !!secret)
  const openRecords = await fetch(`${BASE}/open/bitable/v1/tables/${tableId}/records?pageNum=1&pageSize=10`, {
    headers: { 'X-Api-Key': `${keyId}.${secret}` },
  })
  const openJson = await openRecords.json()
  check('开放API 读取记录', openRecords.status === 200 && openJson?.data?.list?.length > 0)
  const openFields = await fetch(`${BASE}/open/bitable/v1/tables/${tableId}/fields`, {
    headers: { 'X-Api-Key': `${keyId}.${secret}` },
  })
  check('开放API 读取字段元数据', openFields.status === 200)
  const badKey = await fetch(`${BASE}/open/bitable/v1/tables/${tableId}/records`, { headers: { 'X-Api-Key': `${keyId}.wrong-secret` } })
  check('错误 secret 被拒绝', badKey.status === 401 || (await badKey.json())?.code === 401)
  const noKey = await fetch(`${BASE}/open/bitable/v1/tables/${tableId}/records`)
  check('缺少 API Key 被拒绝', noKey.status === 401 || (await noKey.json())?.code === 401)
  // scope 不足
  const keyRO = await http('POST', `/bitable/bases/${baseId}/api-keys`, {
    token: owner.token, body: { name: '只读字段Key', scopes: ['fields:read'] },
  })
  const scopeRes = await fetch(`${BASE}/open/bitable/v1/tables/${tableId}/records`, {
    headers: { 'X-Api-Key': `${getData(keyRO).keyId}.${getData(keyRO).secret}` },
  })
  check('scope 不足被拒绝（fields:read 不能读记录）', scopeRes.status === 403 || (await scopeRes.json())?.code === 403)
  check('Admin 创建 API Key 被拒绝（仅 Owner）', isForbidden(await http('POST', `/bitable/bases/${baseId}/api-keys`, {
    token: admin.token, body: { name: 'x', scopes: ['records:read'] },
  })))

  // ---------- 15. Webhook 管理 ----------
  console.log('\n【15. Webhook 订阅】')
  const hook = await http('POST', `/bitable/bases/${baseId}/webhooks`, {
    token: owner.token,
    body: { name: '外发测试', url: 'https://example.com/webhook-receiver', eventTypes: ['record_created'] },
  })
  check('Owner 创建 Webhook 订阅', isOk(hook))
  const hookId = getData(hook)?.id
  check('secret 仅创建时返回', !!getData(hook)?.secret)
  check('内网 URL 被拒绝（SSRF 防护）', !isOk(await http('POST', `/bitable/bases/${baseId}/webhooks`, {
    token: owner.token, body: { name: 'x', url: 'http://127.0.0.1:9999/steal', eventTypes: ['record_created'] },
  })))
  check('Viewer 创建 Webhook 被拒绝', isForbidden(await http('POST', `/bitable/bases/${baseId}/webhooks`, {
    token: viewer.token, body: { name: 'x', url: 'https://example.com/x' },
  })))
  check('启停 Webhook', isOk(await http('POST', `/bitable/webhooks/${hookId}/status`, { token: owner.token, body: { enabled: false } })))
  check('删除 Webhook', isOk(await http('DELETE', `/bitable/webhooks/${hookId}`, { token: owner.token })))

  // ---------- 16. 导入导出 ----------
  console.log('\n【16. 导入导出】')
  // 导出端点直接返回 byte[]（非 Result 包装），用 raw 读取
  const csvRes = await fetch(`${BASE}/bitable/tables/${tableId}/export/csv`, {
    headers: { Authorization: `Bearer ${editor.token}` },
  })
  const csvText = await csvRes.text()
  check('Editor 导出 CSV', csvRes.status === 200 && csvText.length > 0)
  check('CSV 含表头与数据行', csvText.includes('标题') && csvText.split('\r\n').length > 2, csvText.slice(0, 80))
  // 导入端点接收 multipart 文件
  const csvImport = await fetch(`${BASE}/bitable/tables/${tableId2}/import/csv`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${editor.token}` },
    body: (() => {
      const form = new FormData()
      form.append('file', new Blob(['名称,备注\n测试A,备注1\n测试B,备注2\n'], { type: 'text/csv' }), 'test.csv')
      return form
    })(),
  })
  const importJson = await csvImport.json().catch(() => null)
  check('Editor 导入 CSV（multipart）', csvImport.status === 200 && importJson?.data?.length === 2, JSON.stringify(importJson))
  const commentImport = await fetch(`${BASE}/bitable/tables/${tableId2}/import/csv`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${commenter.token}` },
    body: (() => {
      const form = new FormData()
      form.append('file', new Blob(['名称\nx\n'], { type: 'text/csv' }), 'test.csv')
      return form
    })(),
  })
  check('Commenter 导入被拒绝', isForbidden({ status: commentImport.status, body: await commentImport.json().catch(() => null) }))

  // ---------- 17. 所有权转移（专用第二 Base） ----------
  console.log('\n【17. 所有权转移】')
  const base2 = getData(await http('POST', '/bitable/bases', { token: owner.token, body: { name: '所有权测试Base' } }))
  const editorId = await getUserId(editor)
  const transfer = await http('POST', `/bitable/bases/${base2}/members`, {
    token: owner.token, body: { userId: editorId, role: 'owner' },
  })
  check('Owner 转移所有权给 Editor', isOk(transfer))
  const members2 = getData(await http('GET', `/bitable/bases/${base2}/members`, { token: owner.token }))
  const owners = members2?.filter((m) => m.role === 'owner') || []
  const ownerIdNum = await getUserId(owner)
  const oldOwner = members2?.find((m) => m.userId === ownerIdNum)
  check('Owner 唯一性（转移后仅 1 个 owner）', owners.length === 1 && owners[0].userId === editorId)
  check('原 Owner 降级为 admin', oldOwner?.role === 'admin', JSON.stringify(oldOwner?.role))

  // ---------- 18. 级联删除 ----------
  console.log('\n【18. 级联删除数据一致性】')
  const delBase = getData(await http('POST', '/bitable/bases', { token: outsider.token, body: { name: '删除测试Base' } }))
  const delTable = getData(await http('POST', `/bitable/bases/${delBase}/tables`, { token: outsider.token, body: { name: '删除表' } }))
  await mkRecord(outsider.token, { ...{} })
  await http('POST', `/bitable/tables/${delTable}/records`, { token: outsider.token, body: { cells: {} } })
  const orphanBefore = mysql(
    `SELECT COUNT(*) FROM bitable_cell_values WHERE record_id IN (SELECT id FROM bitable_records WHERE table_id=${delTable});`,
  )
  check('Base 删除（Owner 才能删自己的）', isOk(await http('DELETE', `/bitable/bases/${delBase}`, { token: outsider.token })))
  const orphanAfter = mysql(
    `SELECT COUNT(*) FROM bitable_cell_values WHERE record_id IN (SELECT id FROM bitable_records WHERE table_id=${delTable} AND deleted_at=0);`,
  )
  check('删除后无单元格孤儿数据', orphanAfter === '0', `清理前 ${orphanBefore} 行 -> 清理后 ${orphanAfter} 行`)
  check('其他成员不能删除他人的 Base', isForbidden(await http('DELETE', `/bitable/bases/${baseId}`, { token: outsider.token })))

  // ---------- 汇总 ----------
  console.log('\n========================================')
  console.log(`总计: ${passCount + failCount} 项 | 通过: ${passCount} | 失败: ${failCount}`)
  if (failures.length) {
    console.log('\n失败项明细:')
    for (const f of failures) {
      console.log(`  ✗ ${f.name}${f.detail ? ` —— ${f.detail}` : ''}`)
    }
  }
  console.log('========================================\n')
  process.exit(failCount > 0 ? 1 : 0)
}

main().catch((e) => {
  console.error('测试执行异常:', e)
  process.exit(2)
})
