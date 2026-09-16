// 验证「高级权限」里每个角色的**成员维护页**（参考设计稿「X包含的成员」）：
//   - 从成员条进入独立成员页（标题「X包含的成员」/ 搜索 / 成员列表 / 返回）
//   - 每行展示「属于N个角色」，悬停浮层列出「成员所在角色」
//   - 一次勾选多个用户加入同一角色（自定义角色可并存，系统角色是"调整"）
//   - 成员增删对全局生效（写的是同一份角色成员表）
//
// 用法：NODE_PATH=<demand_frontend/node_modules> node scripts/verify-role-member-page.cjs
const path = require('node:path');
const fs = require('node:fs');
const { execSync } = require('node:child_process');

const BASE = 'http://127.0.0.1:5170';
const API = 'http://127.0.0.1:8081/api/v1';
const BASE_ID = 1;
const OUT = path.join(__dirname, 'out');
const DOCKER = 'C:\\Program Files\\Docker\\Docker\\resources\\bin\\docker.exe';
const TS = Date.now().toString(36);
const PASSWORD = 'Test@123456';
const ROLE_NAME = `验收成员页_${TS}`;

const log = (...a) => console.log(...a);

let pass = 0;
let fail = 0;
function check(label, ok, extra) {
  if (ok) {
    pass++;
    log('  PASS  ' + label);
  } else {
    fail++;
    log('  FAIL  ' + label + (extra ? '  ' + extra : ''));
  }
}

function mysql(sql) {
  return execSync(
    `"${DOCKER}" exec mysql mysql -uroot -padmin123 --default-character-set=utf8mb4 -N -e "USE demand_system; ${sql}"`,
    { encoding: 'utf8', stdio: ['pipe', 'pipe', 'ignore'] },
  ).trim();
}

async function api(method, p, { token, body } = {}) {
  const headers = { 'Content-Type': 'application/json' };
  if (token) headers.Authorization = `Bearer ${token}`;
  const res = await fetch(API + p, {
    method,
    headers,
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });
  let json = null;
  try {
    json = await res.json();
  } catch {
    /* ignore */
  }
  return { status: res.status, body: json };
}

/** 注册一个临时用户并补上组织归属，否则不会出现在选人下拉里 */
async function registerTempUser(tag) {
  const email = `rmp_${tag}_${TS}@test.com`;
  await api('POST', '/auth/send-verification-code', { body: { email, type: 'register' } });
  const code = mysql(
    `SELECT code FROM verification_codes WHERE email='${email}' AND type='register' AND used=0 ORDER BY created_at DESC LIMIT 1;`,
  );
  const username = `rmp_${tag}_${TS}`;
  const realName = `成员页${tag}_${TS}`;
  const reg = await api('POST', '/auth/register', {
    body: { username, password: PASSWORD, realName, email, verificationCode: code },
  });
  if (reg.status !== 200 || (reg.body?.code && reg.body.code !== 200)) {
    throw new Error(`注册临时用户失败: ${JSON.stringify(reg.body)}`);
  }
  const login = await api('POST', '/auth/login', { body: { username, password: PASSWORD } });
  const token = login.body?.data?.accessToken;
  const me = await api('GET', '/auth/me', { token });
  const id = me.body?.data?.id;
  if (!id) throw new Error(`取临时用户 id 失败: ${JSON.stringify(me.body)}`);

  const adminOrg = mysql(`SELECT org_id FROM users WHERE username='admin' LIMIT 1;`);
  mysql(`UPDATE users SET org_id=${adminOrg}, region_id=${adminOrg} WHERE id=${id};`);
  return { id, username, realName };
}

async function main() {
  const { chromium } = require('playwright');
  fs.mkdirSync(OUT, { recursive: true });

  const userA = await registerTempUser('A');
  const userB = await registerTempUser('B');
  log(`[0] 临时用户 A=${userA.id}(${userA.realName}) B=${userB.id}(${userB.realName})`);

  const browser = await chromium.launch({ headless: true });
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const page = await ctx.newPage();
  const errs = [];
  page.on('pageerror', (e) => errs.push('pageerror: ' + e.message));
  page.on('console', (m) => {
    if (m.type() === 'error') errs.push('console: ' + m.text());
  });

  const settle = async () => {
    await page
      .waitForFunction(() => !document.querySelector('.el-message'), { timeout: 8000 })
      .catch(() => {});
    await page.waitForTimeout(400);
  };

  const roleItem = (name) => page.locator('.perm-role-item').filter({ hasText: name }).first();
  const rowNames = () =>
    page.locator('.perm-member-row__name').evaluateAll((els) => els.map((e) => e.textContent.trim()));
  const rowRolesText = () =>
    page.locator('.perm-member-row__roles').evaluateAll((els) => els.map((e) => e.textContent.trim()));

  /** 在选人弹窗里勾选多个用户（内联勾选列表：搜索 → 点候选行） */
  const pickUsers = async (names) => {
    for (const n of names) {
      await page.locator('.member-add-dialog input[placeholder="搜索用户"]').fill(n);
      await page.waitForTimeout(450);
      await page.locator('.member-pick').first().click();
      await page.waitForTimeout(200);
    }
  };

  const confirmAddMember = async () => {
    await page
      .locator('.el-dialog')
      .filter({ has: page.locator('.member-add-dialog') })
      .locator('.el-button--primary')
      .click();
    await settle();
  };

  try {
    // ---------- 登录 + 打开高级权限 ----------
    await page.goto(BASE + '/login', { waitUntil: 'domcontentloaded' });
    await page.locator('input[placeholder="请输入用户名"]').fill('admin');
    await page.locator('input[placeholder="请输入密码"]').fill('admin123');
    await page.locator('.login-btn').first().click();
    await page.waitForURL((u) => !u.pathname.includes('/login'), { timeout: 30000 });

    await page.goto(`${BASE}/bitable/${BASE_ID}`, { waitUntil: 'domcontentloaded' });
    await page.locator('.editor-sidebar').first().waitFor({ timeout: 30000 });
    await page.waitForTimeout(1500);
    await page.locator('button').filter({ hasText: '高级权限' }).first().click();
    await page.locator('.perm-body').waitFor({ timeout: 15000 });
    await page.waitForTimeout(600);
    log('[1] 高级权限弹窗已打开');

    // ---------- 新建自定义角色 ----------
    await page.locator('.perm-role-add').click();
    await page.locator('input[placeholder="请输入角色名称"]').fill(ROLE_NAME);
    await page.locator('.el-dialog').filter({ hasText: '添加自定义角色' }).locator('.el-button--primary').click();
    await settle();
    check('自定义角色已创建', (await roleItem(ROLE_NAME).count()) === 1);

    // ---------- 权限设计视图：成员条上有「成员管理」入口 ----------
    await roleItem(ROLE_NAME).click();
    await page.waitForTimeout(700);
    check('权限设计视图显示「成员管理」入口', (await page.locator('.perm-members__manage').count()) === 1);
    check('权限设计视图下没有成员页', (await page.locator('.perm-member-page').count()) === 0);

    // ---------- 进入成员维护页 ----------
    await page.locator('.perm-members__manage').click();
    await page.locator('.perm-member-page').waitFor({ timeout: 10000 });
    await page.waitForTimeout(500);
    const title = (await page.locator('.perm-member-page__title').innerText()).trim();
    check('成员页标题为「X包含的成员」', title === `${ROLE_NAME}包含的成员`, `title=${title}`);
    check('成员页有搜索框', (await page.locator('.perm-member-page__search input').count()) === 1);
    check('成员页有添加成员按钮', (await page.locator('.perm-member-page__add').count()) === 1);
    check('新角色成员页为空', (await page.locator('.perm-member-row').count()) === 0);
    check(
      '空状态文案正确',
      (await page.locator('.perm-member-page__empty').innerText()).includes('暂无成员'),
    );
    check('成员页隐藏了权限设置面板', (await page.locator('.perm-settings').count()) === 0);
    await page.screenshot({ path: path.join(OUT, 'rmp-01-member-page-empty.png') });

    // ---------- 一次勾选两个用户加入 ----------
    await page.locator('.perm-member-page__add').click();
    await page.locator('.member-add-dialog').waitFor({ timeout: 10000 });
    await page.waitForTimeout(500);
    check(
      '未勾选时不显示多选提示',
      (await page.locator('.member-add-dialog__note').count()) === 0,
    );
    await pickUsers([userA.realName, userB.realName]);
    // 自定义角色勾多人是显而易见的操作，按「文案极简」原则不加提示
    check(
      '自定义角色勾选多人不显示冗余提示',
      (await page.locator('.member-add-dialog__note').count()) === 0,
    );
    await page.screenshot({ path: path.join(OUT, 'rmp-02-multi-select.png') });
    await confirmAddMember();

    const names = await rowNames();
    log('[2] 成员页成员:', JSON.stringify(names));
    check('两名用户一次加入成功', names.length === 2, JSON.stringify(names));
    check('成员页显示用户 A', names.some((n) => n.includes(userA.realName)));
    check('成员页显示用户 B', names.some((n) => n.includes(userB.realName)));

    // ---------- 属于N个角色 ----------
    const roleTexts = await rowRolesText();
    log('[3] 角色计数:', JSON.stringify(roleTexts));
    check(
      '每人此时「属于1个角色」',
      roleTexts.length === 2 && roleTexts.every((t) => t === '属于1个角色'),
      JSON.stringify(roleTexts),
    );

    // 悬停展开角色清单
    await page.locator('.perm-member-row__roles').first().hover();
    await page.waitForTimeout(700);
    // 每行一个 el-popover，未展开的那些也留在 DOM 里 → 必须限定 :visible
    const popTitle = await page
      .locator('.perm-member-roles-popover:visible .member-roles-pop__title')
      .first()
      .innerText();
    const popItems = await page
      .locator('.perm-member-roles-popover:visible .member-roles-pop__item')
      .evaluateAll((els) => els.map((e) => e.textContent.trim()));
    log('[4] 悬停浮层:', popTitle.replace(/\s+/g, ' '), JSON.stringify(popItems));
    check('浮层标题为「成员所在角色」', popTitle.includes('成员所在角色'));
    check('浮层列出该角色', popItems.some((t) => t.includes(ROLE_NAME)), JSON.stringify(popItems));
    await page.screenshot({ path: path.join(OUT, 'rmp-03-roles-popover.png') });

    // ---------- 把 A 也加入「只读」系统角色 → A 变成 2 个角色 ----------
    await page.locator('.perm-member-page__back').click();
    await page.waitForTimeout(400);
    check('返回后回到权限设计视图', (await page.locator('.perm-member-page').count()) === 0);
    await roleItem('只读').click();
    await page.waitForTimeout(700);
    await page.locator('.perm-members__add').click();
    await page.locator('.member-add-dialog').waitFor({ timeout: 10000 });
    await page.waitForTimeout(500);
    await pickUsers([userA.realName]);
    await confirmAddMember();
    check(
      'A 已加入「只读」系统角色',
      mysql(`SELECT role FROM bitable_base_members WHERE base_id=${BASE_ID} AND user_id=${userA.id};`) === 'viewer',
    );

    // 回到自定义角色的成员页，A 应显示「属于2个角色」
    await roleItem(ROLE_NAME).click();
    await page.waitForTimeout(700);
    await page.locator('.perm-members__manage').click();
    await page.locator('.perm-member-page').waitFor({ timeout: 10000 });
    await page.waitForTimeout(600);
    const roleTexts2 = await rowRolesText();
    log('[5] 跨角色后的计数:', JSON.stringify(roleTexts2));
    check(
      'A 显示「属于2个角色」',
      roleTexts2.includes('属于2个角色'),
      JSON.stringify(roleTexts2),
    );

    await page.locator('.perm-member-row__roles').filter({ hasText: '属于2个角色' }).first().hover();
    await page.waitForTimeout(700);
    const popItems2 = await page
      .locator('.perm-member-roles-popover:visible .member-roles-pop__item')
      .evaluateAll((els) => els.map((e) => e.textContent.trim()));
    log('[6] A 的角色清单:', JSON.stringify(popItems2));
    check('浮层同时列出「只读」与自定义角色', popItems2.some((t) => t.includes('只读')) && popItems2.some((t) => t.includes(ROLE_NAME)), JSON.stringify(popItems2));
    await page.screenshot({ path: path.join(OUT, 'rmp-04-two-roles.png') });

    // ---------- 搜索过滤 ----------
    await page.locator('.perm-member-page__search input').fill(userB.realName);
    await page.waitForTimeout(600);
    const searched = await rowNames();
    check('搜索只剩 B', searched.length === 1 && searched[0].includes(userB.realName), JSON.stringify(searched));
    await page.locator('.perm-member-page__search input').fill('不存在的成员xyz');
    await page.waitForTimeout(600);
    check('无匹配时显示空状态', (await page.locator('.perm-member-page__empty').count()) === 1);
    await page.locator('.perm-member-page__search input').fill('');
    await page.waitForTimeout(600);
    check('清空搜索后恢复 2 人', (await page.locator('.perm-member-row').count()) === 2);

    // ---------- 从成员页移除 B ----------
    await page
      .locator('.perm-member-row')
      .filter({ hasText: userB.realName })
      .locator('.perm-member-row__remove')
      .click();
    await page.locator('.el-message-box__btns .el-button--primary').click();
    await settle();
    const afterRemove = await rowNames();
    check('移除后只剩 A', afterRemove.length === 1 && afterRemove[0].includes(userA.realName), JSON.stringify(afterRemove));
    check(
      '移除是全局生效（DB 里已无 B）',
      mysql(
        `SELECT COUNT(*) FROM bitable_base_custom_role_members m JOIN bitable_base_custom_roles r ON r.id=m.role_id WHERE r.base_id=${BASE_ID} AND r.name='${ROLE_NAME}' AND m.member_id=${userB.id};`,
      ) === '0',
    );

    // ---------- 所有者不可移除 ----------
    await page.locator('.perm-member-page__back').click();
    await page.waitForTimeout(400);
    await roleItem('所有者').click();
    await page.waitForTimeout(700);
    await page.locator('.perm-members__manage').click();
    await page.locator('.perm-member-page').waitFor({ timeout: 10000 });
    await page.waitForTimeout(600);
    check(
      '所有者的成员页里移除按钮被禁用',
      (await page.locator('.perm-member-row__remove:disabled').count()) >= 1,
    );
    await page.screenshot({ path: path.join(OUT, 'rmp-05-owner.png') });

    check('页面无 JS 报错', errs.length === 0, errs.join(' | '));
  } catch (e) {
    fail++;
    log('!! 执行异常: ' + e.message);
    await page.screenshot({ path: path.join(OUT, 'rmp-error.png') }).catch(() => {});
  } finally {
    // 清理：自定义角色（成员 + 权限 + 软删）、系统角色成员、临时用户
    // 逐条独立 try，避免某一步失败导致后面的清理被跳过
    const trySql = (sql, label) => {
      try {
        mysql(sql);
      } catch (e) {
        log(`[7] 清理步骤失败(${label}): ` + String(e.message).split('\n')[0]);
      }
    };
    // 注意：bitable_base_role_permissions 关联自定义角色的列叫 custom_role_id（不是 role_id）
    trySql(
      `DELETE m FROM bitable_base_custom_role_members m JOIN bitable_base_custom_roles r ON r.id=m.role_id WHERE r.base_id=${BASE_ID} AND r.name='${ROLE_NAME}';`,
      '角色成员',
    );
    trySql(
      `DELETE FROM bitable_base_role_permissions WHERE base_id=${BASE_ID} AND custom_role_id IN (SELECT id FROM bitable_base_custom_roles WHERE base_id=${BASE_ID} AND name='${ROLE_NAME}');`,
      '角色权限',
    );
    trySql(
      `UPDATE bitable_base_custom_roles SET deleted_at=1 WHERE base_id=${BASE_ID} AND name='${ROLE_NAME}';`,
      '自定义角色',
    );
    trySql(
      `DELETE FROM bitable_base_members WHERE base_id=${BASE_ID} AND user_id IN (${userA.id},${userB.id});`,
      'base 成员',
    );
    trySql(
      `UPDATE users SET deleted_at=1, status='inactive' WHERE id IN (${userA.id},${userB.id});`,
      '临时用户',
    );
    log('[7] 清理完成（角色/成员/临时用户）');
    log(`\n===== 结果：PASS ${pass} / FAIL ${fail} =====`);
    await browser.close();
    if (fail > 0) process.exitCode = 1;
  }
}

main();
