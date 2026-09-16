#!/usr/bin/env node
/**
 * 多维表格「自定义角色」管理接口的授权回归测试
 *
 * 背景：BitableBaseRoleServiceImpl 里 updateCustomRole / deleteCustomRole / removeRoleMember
 * 三个方法原先**没有任何授权校验**（只有 addRoleMember 有）。它们此前因为一个恒真的
 * getDeletedAt() != null 守卫而 100% 抛「角色不存在」，等于把漏洞遮住了；守卫修好后
 * 必须补上 checkManagePermission，否则任何登录用户都能改/删别人的自定义角色、踢掉成员。
 *
 * 本脚本验证：非 ADMIN 成员对这 4 个接口全部 403，且数据未被改动；ADMIN 全部成功。
 *
 * 运行前提：后端 localhost:8081，MySQL 容器名 mysql（root/admin123，库 demand_system）
 * 用法：node scripts/verify-role-authz.mjs
 */
import { execSync } from 'node:child_process'

const BASE = 'http://localhost:8081/api/v1'
const TS = Date.now().toString(36)
const PASSWORD = 'Test@123456'
const BASE_ID = 1
const DOCKER = 'C:\\Program Files\\Docker\\Docker\\resources\\bin\\docker.exe'

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

function mysql(sql) {
  return execSync(
    `"${DOCKER}" exec mysql mysql -uroot -padmin123 -N -e "USE demand_system; ${sql}"`,
    { encoding: 'utf8', stdio: ['pipe', 'pipe', 'ignore'] },
  ).trim()
}

async function http(method, path, { token, body } = {}) {
  const headers = { 'Content-Type': 'application/json' }
  if (token) headers.Authorization = `Bearer ${token}`
  const res = await fetch(BASE + path, {
    method,
    headers,
    body: body !== undefined ? JSON.stringify(body) : undefined,
  })
  let json = null
  try {
    json = await res.json()
  } catch {
    /* ignore */
  }
  return { status: res.status, body: json }
}

const isOk = (r) => r.status === 200 && (r.body?.code === undefined || r.body?.code === 200)
const isForbidden = (r) => r.status === 403 || r.body?.code === 403
const getData = (r) => r.body?.data

async function registerViewer() {
  const email = `authz_${TS}@test.com`
  await http('POST', '/auth/send-verification-code', { body: { email, type: 'register' } })
  const code = mysql(
    `SELECT code FROM verification_codes WHERE email='${email}' AND type='register' AND used=0 ORDER BY created_at DESC LIMIT 1;`,
  )
  const username = `authz_${TS}`
  const reg = await http('POST', '/auth/register', {
    body: { username, password: PASSWORD, realName: `授权测试_${TS}`, email, verificationCode: code },
  })
  if (!isOk(reg)) throw new Error(`注册 viewer 失败: ${JSON.stringify(reg.body)}`)
  const login = await http('POST', '/auth/login', { body: { username, password: PASSWORD } })
  if (!isOk(login)) throw new Error(`登录 viewer 失败: ${JSON.stringify(login.body)}`)
  const token = getData(login).accessToken
  // 注意：登录响应只有 accessToken/refreshToken/expiresIn/tokenType/needOrgBind，**不含 user**，
  // 想拿 userId 必须再调 /auth/me。
  const me = await http('GET', '/auth/me', { token })
  const id = getData(me)?.id
  if (!id) throw new Error(`取 viewer userId 失败: ${JSON.stringify(me.body)}`)
  return { id, token, username }
}

const roleList = async (token) =>
  getData(await http('GET', `/bitable/bases/${BASE_ID}/roles?permissionType=data`, { token })) || []

async function main() {
  console.log(`\n=== 自定义角色管理接口授权回归  (ts=${TS}) ===\n`)

  const adminLogin = await http('POST', '/auth/login', {
    body: { username: 'admin', password: 'admin123' },
  })
  if (!isOk(adminLogin)) throw new Error('admin 登录失败')
  const adminToken = getData(adminLogin).accessToken
  const adminMe = await http('GET', '/auth/me', { token: adminToken })
  const adminId = getData(adminMe)?.id
  if (!adminId) throw new Error(`取 admin userId 失败: ${JSON.stringify(adminMe.body)}`)

  // ---------- 0. 造一个 viewer 成员 ----------
  console.log('【0. 准备非管理员成员】')
  const viewer = await registerViewer()
  check('viewer 注册并登录成功', !!viewer.token)

  const addViewer = await http('POST', `/bitable/bases/${BASE_ID}/members`, {
    token: adminToken,
    body: { userId: viewer.id, role: 'viewer' },
  })
  check('admin 把 viewer 加入 Base(1)', isOk(addViewer), JSON.stringify(addViewer.body))

  // 关键前提：必须确认 viewer 的 403 是"角色不够"，而不是"根本不是成员"——
  // 后者会让下面的越权断言变得毫无意义（没成员身份本来就会被拒）。
  // 读角色列表本身也要求 ADMIN，viewer 必然 403，但报错文案里会带上已解析出的当前角色。
  const viewerProbe = await http('GET', `/bitable/bases/${BASE_ID}/roles?permissionType=data`, {
    token: viewer.token,
  })
  const probeMsg = viewerProbe.body?.message ?? ''
  check(
    'viewer 已解析出成员角色（403 文案含「当前角色为 只读」，证明是权限不足而非非成员）',
    isForbidden(viewerProbe) && probeMsg.includes('当前角色为'),
    `status=${viewerProbe.status} msg=${probeMsg}`,
  )

  // ---------- 1. admin 建一个自定义角色 ----------
  console.log('\n【1. admin 建角色并加成员】')
  const ROLE_NAME = `授权测试角色_${TS}`
  const created = await http('POST', `/bitable/bases/${BASE_ID}/custom-roles`, {
    token: adminToken,
    // baseId 必须放进 body：DTO 上有 @NotNull，@Valid 在 controller 赋值之前就跑了
    body: { baseId: BASE_ID, name: ROLE_NAME },
  })
  check('admin 创建自定义角色', isOk(created), JSON.stringify(created.body))

  let roles = await roleList(adminToken)
  const role = roles.find((r) => r.roleType === 'custom' && r.name === ROLE_NAME)
  check('新角色出现在列表中并拿到 id', !!role?.customRoleId, JSON.stringify(roles.map((r) => r.name)))
  if (!role) throw new Error('未找到新建角色，后续断言无法进行')
  const roleId = role.customRoleId

  const adminAddMember = await http('POST', `/bitable/custom-roles/${roleId}/members`, {
    token: adminToken,
    body: { memberType: 'user', memberId: adminId },
  })
  check('admin 给角色加成员', isOk(adminAddMember), JSON.stringify(adminAddMember.body))

  // ---------- 2. viewer 的四个写操作必须全部 403 ----------
  console.log('\n【2. viewer（VIEWER 角色）越权尝试 —— 期望全部 403】')

  const renameByViewer = await http('PUT', `/bitable/custom-roles/${roleId}`, {
    token: viewer.token,
    body: { name: '被越权改掉的名字' },
  })
  check('viewer 重命名角色被拒绝 (403)', isForbidden(renameByViewer), JSON.stringify(renameByViewer.body))

  const deleteByViewer = await http('DELETE', `/bitable/custom-roles/${roleId}`, {
    token: viewer.token,
  })
  check('viewer 删除角色被拒绝 (403)', isForbidden(deleteByViewer), JSON.stringify(deleteByViewer.body))

  const addMemberByViewer = await http('POST', `/bitable/custom-roles/${roleId}/members`, {
    token: viewer.token,
    body: { memberType: 'user', memberId: viewer.id },
  })
  check('viewer 往角色里加成员被拒绝 (403)', isForbidden(addMemberByViewer), JSON.stringify(addMemberByViewer.body))

  const removeMemberByViewer = await http(
    'DELETE',
    `/bitable/custom-roles/${roleId}/members?memberType=user&memberId=${adminId}`,
    { token: viewer.token },
  )
  check('viewer 移除角色成员被拒绝 (403)', isForbidden(removeMemberByViewer), JSON.stringify(removeMemberByViewer.body))

  // ---------- 3. 数据必须原封不动 ----------
  console.log('\n【3. 越权尝试后数据未被改动】')
  roles = await roleList(adminToken)
  const after = roles.find((r) => r.customRoleId === roleId)
  check('角色名未被 viewer 改动', after?.name === ROLE_NAME, `实际=${after?.name}`)
  check(
    '角色成员未被 viewer 改动（仍为 1 人且是 admin）',
    after?.members?.length === 1 && after.members[0].memberId === adminId,
    JSON.stringify(after?.members),
  )

  // ---------- 4. admin 的四个写操作必须成功 ----------
  console.log('\n【4. admin 同四个操作 —— 期望全部成功】')
  const RENAMED = `${ROLE_NAME}_改`
  check(
    'admin 重命名成功',
    isOk(await http('PUT', `/bitable/custom-roles/${roleId}`, { token: adminToken, body: { name: RENAMED } })),
  )
  check(
    'admin 加成员成功',
    isOk(
      await http('POST', `/bitable/custom-roles/${roleId}/members`, {
        token: adminToken,
        body: { memberType: 'user', memberId: viewer.id },
      }),
    ),
  )
  roles = await roleList(adminToken)
  check(
    '成员数变为 2',
    roles.find((r) => r.customRoleId === roleId)?.members?.length === 2,
    JSON.stringify(roles.find((r) => r.customRoleId === roleId)?.members),
  )
  check(
    'admin 移除成员成功',
    isOk(
      await http('DELETE', `/bitable/custom-roles/${roleId}/members?memberType=user&memberId=${viewer.id}`, {
        token: adminToken,
      }),
    ),
  )
  check('admin 删除角色成功', isOk(await http('DELETE', `/bitable/custom-roles/${roleId}`, { token: adminToken })))

  // ---------- 5. 清理 ----------
  console.log('\n【5. 清理测试数据】')
  const removed = await http('DELETE', `/bitable/bases/${BASE_ID}/members/${viewer.id}`, { token: adminToken })
  check('viewer 已移出 Base(1)', isOk(removed), JSON.stringify(removed.body))
  // 注册出来的测试账号软删掉，避免 users 表里堆垃圾（users 有 deleted_at + status）
  try {
    mysql(`UPDATE users SET deleted_at=1, status='inactive' WHERE id=${viewer.id};`)
    check('测试账号已软删除', mysql(`SELECT deleted_at FROM users WHERE id=${viewer.id};`) === '1')
  } catch (e) {
    check('测试账号已软删除', false, String(e.message))
  }
  const finalRoles = await roleList(adminToken)
  check(
    '测试角色已从列表消失',
    !finalRoles.some((r) => r.customRoleId === roleId),
    JSON.stringify(finalRoles.filter((r) => r.roleType === 'custom').map((r) => r.name)),
  )

  // ---------- 汇总 ----------
  console.log('\n========================================')
  console.log(`总计: ${passCount + failCount} 项 | 通过: ${passCount} | 失败: ${failCount}`)
  if (failures.length) {
    console.log('\n失败项明细:')
    for (const f of failures) console.log(`  ✗ ${f.name}${f.detail ? ` —— ${f.detail}` : ''}`)
  }
  console.log('========================================\n')
  process.exit(failCount > 0 ? 1 : 0)
}

main().catch((e) => {
  console.error('测试执行异常:', e)
  process.exit(2)
})
