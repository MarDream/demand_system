// 验证「高级权限」弹窗里**系统角色**也能直接增删成员
// （所有者/管理员/编辑者/评论者/只读 —— 成员存在 bitable_base_members，一人只有一个系统角色）
//
// 用法：NODE_PATH=<demand_frontend/node_modules> node scripts/verify-system-role-members.cjs
//
// 关键语义：往系统角色里"加人"其实是**调整**该用户的系统角色（不是并列新增），
// 所以本脚本会重点验证：已在本角色的人不出现在候选里；在别的角色的人会出现并标注"当前：X"。
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
const REAL_NAME = `角色测_${TS}`; // 首字「角」，用于断言缩略图
const EXPECTED_INITIAL = '角';

const log = (...a) => console.log(...a);

let pass = 0;
let fail = 0;
let noteShot = 0;
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
  return execSync(`"${DOCKER}" exec mysql mysql -uroot -padmin123 -N -e "USE demand_system; ${sql}"`, {
    encoding: 'utf8',
    stdio: ['pipe', 'pipe', 'ignore'],
  }).trim();
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

/** 注册一个临时用户，返回 { id, username } */
async function registerTempUser() {
  const email = `sysrole_${TS}@test.com`;
  await api('POST', '/auth/send-verification-code', { body: { email, type: 'register' } });
  const code = mysql(
    `SELECT code FROM verification_codes WHERE email='${email}' AND type='register' AND used=0 ORDER BY created_at DESC LIMIT 1;`,
  );
  const username = `sysrole_${TS}`;
  const reg = await api('POST', '/auth/register', {
    body: { username, password: PASSWORD, realName: REAL_NAME, email, verificationCode: code },
  });
  if (reg.status !== 200 || (reg.body?.code && reg.body.code !== 200)) {
    throw new Error(`注册临时用户失败: ${JSON.stringify(reg.body)}`);
  }
  const login = await api('POST', '/auth/login', { body: { username, password: PASSWORD } });
  const token = login.body?.data?.accessToken;
  const me = await api('GET', '/auth/me', { token });
  const id = me.body?.data?.id;
  if (!id) throw new Error(`取临时用户 id 失败: ${JSON.stringify(me.body)}`);

  // 注意：GET /v1/users/active 在超管视角下只返回"有 org_id / region_id / department_id"的活跃用户，
  // 而 /auth/register 建出来的用户这几个字段都是空 → 不会出现在选人下拉里（产品既定行为，不是 bug）。
  // 这里补上组织归属，让它成为"可选的人"。
  const adminOrg = mysql(`SELECT org_id FROM users WHERE username='admin' LIMIT 1;`);
  mysql(`UPDATE users SET org_id=${adminOrg}, region_id=${adminOrg} WHERE id=${id};`);
  return { id, username };
}

async function main() {
  const { chromium } = require('playwright');
  fs.mkdirSync(OUT, { recursive: true });

  const temp = await registerTempUser();
  log(`[0] 临时用户已注册 id=${temp.id} username=${temp.username} realName=${REAL_NAME}`);

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
  const badgeOf = (name) =>
    roleItem(name)
      .locator('.perm-role-item__badge')
      .evaluate((el) => el.textContent.trim());
  const memberAvatars = () =>
    page.locator('.perm-members__avatar').evaluateAll((els) =>
      els.map((el) => ({
        hasImg: !!el.querySelector('img'),
        text: (el.textContent || '').trim(),
      })),
    );

  /** 打开选人下拉，返回可见候选项的文本 */
  const openPickerAndRead = async () => {
    await page.locator('.perm-members__add').click();
    await page.locator('.member-add-dialog').waitFor({ timeout: 10000 });
    // 候选项是内联勾选列表（不是下拉浮层），不会有浮层遮挡说明文案
    await page.locator('.member-add-dialog__note').waitFor({ state: 'visible', timeout: 8000 }).catch(() => {});
    await page.waitForTimeout(600);
    await page.screenshot({ path: path.join(OUT, `srm-note-${++noteShot}.png`) });
    await page.locator('.member-pick').first().waitFor({ timeout: 10000 });
    return page
      .locator('.member-pick')
      .evaluateAll((els) => els.map((el) => el.textContent.replace(/\s+/g, ' ').trim()));
  };

  /** 关掉选人弹窗：点该弹窗自己的「取消」（别用 .locator('..') 猜父节点） */
  const closeAddMemberDialog = async () => {
    const dlg = page.locator('.el-dialog').filter({ has: page.locator('.member-add-dialog') });
    await dlg.locator('.el-button').first().click({ timeout: 5000 }).catch(() => {});
    await page
      .locator('.member-add-dialog')
      .waitFor({ state: 'hidden', timeout: 8000 })
      .catch(() => {});
    await settle();
  };

  try {
    // ---------- 登录 + 打开弹窗 ----------
    await page.goto(BASE + '/login', { waitUntil: 'domcontentloaded' });
    await page.locator('input[placeholder="请输入用户名"]').fill('admin');
    await page.locator('input[placeholder="请输入密码"]').fill('admin123');
    await page.locator('.login-btn').first().click();
    await page.waitForURL((u) => !u.pathname.includes('/login'), { timeout: 30000 });

    await page.goto(`${BASE}/bitable/${BASE_ID}`, { waitUntil: 'domcontentloaded' });
    await page.locator('.editor-sidebar').first().waitFor({ timeout: 30000 });
    await page.waitForTimeout(1500);
    const permBtn = page.locator('button').filter({ hasText: '高级权限' }).first();
    await permBtn.waitFor({ timeout: 15000 });
    await permBtn.click();
    await page.locator('.perm-body').waitFor({ timeout: 15000 });
    await page.waitForTimeout(600);
    log('[1] 高级权限弹窗已打开');

    // ---------- 系统角色：选中「只读」 ----------
    await roleItem('只读').click();
    await page.waitForTimeout(700);
    check('系统角色显示成员区与「添加成员」入口', (await page.locator('.perm-members__add').count()) === 1);
    check('系统角色初始成员数为 0', (await badgeOf('只读')) === '0', `badge=${await badgeOf('只读')}`);

    // ---------- 打开选人弹窗：应有"一人只有一个角色"的说明 ----------
    const candidates = await openPickerAndRead();
    log('[2] 「只读」候选人数:', candidates.length);
    const noteText = await page.locator('.member-add-dialog__note').innerText();
    check(
      '选人弹窗提示「系统角色一人只有一个」',
      noteText.includes('一人只有一个') && noteText.includes('调整'),
      noteText.replace(/\s+/g, ' '),
    );
    check('临时用户出现在「只读」候选中', candidates.some((t) => t.includes(temp.username)), JSON.stringify(candidates.slice(0, 3)));
    check(
      '临时用户尚未有系统角色（不显示"当前："）',
      !candidates.some((t) => t.includes(temp.username) && t.includes('当前：')),
    );
    await page.screenshot({ path: path.join(OUT, 'srm-01-picker.png') });

    // ---------- 加入「只读」 ----------
    await page.locator('.member-add-dialog input[placeholder="搜索用户"]').fill(REAL_NAME);
    await page.waitForTimeout(500);
    await page.locator('.member-pick').first().click();
    await page.waitForTimeout(300);
    await page
      .locator('.el-dialog')
      .filter({ has: page.locator('.member-add-dialog') })
      .locator('.el-button--primary')
      .click();
    await settle();

    check('「只读」成员徽标变为 1', (await badgeOf('只读')) === '1', `badge=${await badgeOf('只读')}`);
    const avatars = await memberAvatars();
    log('[3] 「只读」成员头像:', JSON.stringify(avatars));
    check('成员渲染缩略图', avatars.length === 1, `count=${avatars.length}`);
    check(
      `缩略图为姓名首字「${EXPECTED_INITIAL}」`,
      avatars[0] && avatars[0].text === EXPECTED_INITIAL,
      JSON.stringify(avatars[0]),
    );
    check('后端已落库为 base 成员（viewer）',
      mysql(`SELECT role FROM bitable_base_members WHERE base_id=${BASE_ID} AND user_id=${temp.id};`) === 'viewer');
    await page.screenshot({ path: path.join(OUT, 'srm-02-added.png') });

    // ---------- 已在本角色的人不应再出现在候选里 ----------
    const candidates2 = await openPickerAndRead();
    check(
      '已在「只读」的用户不再出现在候选中',
      !candidates2.some((t) => t.includes(temp.username)),
      JSON.stringify(candidates2.slice(0, 3)),
    );
    await closeAddMemberDialog();

    // ---------- 切到「编辑者」：应看到该用户且标注「当前：只读」 ----------
    await roleItem('编辑者').click();
    await page.waitForTimeout(700);
    const candidates3 = await openPickerAndRead();
    const tempEntry = candidates3.find((t) => t.includes(temp.username));
    log('[4] 「编辑者」里该用户的候选行:', tempEntry);
    check('已是 base 成员的用户出现在其它系统角色候选中', !!tempEntry, JSON.stringify(candidates3.slice(0, 3)));
    check(
      '候选行标注其当前角色「当前：只读」',
      !!tempEntry && tempEntry.includes('当前：只读'),
      tempEntry,
    );
    await page.screenshot({ path: path.join(OUT, 'srm-03-move-hint.png') });
    await closeAddMemberDialog();
    check(
      '取消后角色未被改动（仍是 viewer）',
      mysql(`SELECT role FROM bitable_base_members WHERE base_id=${BASE_ID} AND user_id=${temp.id};`) === 'viewer',
    );

    // ---------- 从「只读」移除 ----------
    await roleItem('只读').click();
    await page.waitForTimeout(700);
    check('成员可被移除（有 is-removable）', (await page.locator('.perm-members__item.is-removable').count()) === 1);
    await page.locator('.perm-members__item.is-removable').first().click();
    const confirmMsg = await page.locator('.el-message-box__message').innerText();
    check(
      '移除提示说明会失去全部访问权限',
      confirmMsg.includes('全部访问权限'),
      confirmMsg.replace(/\s+/g, ' '),
    );
    await page.locator('.el-message-box__btns .el-button--primary').click();
    await settle();

    check('「只读」成员徽标回落为 0', (await badgeOf('只读')) === '0', `badge=${await badgeOf('只读')}`);
    check(
      '后端成员记录已删除',
      mysql(`SELECT COUNT(*) FROM bitable_base_members WHERE base_id=${BASE_ID} AND user_id=${temp.id};`) === '0',
    );

    // ---------- 所有者不可移除 ----------
    await roleItem('所有者').click();
    await page.waitForTimeout(700);
    check(
      '所有者成员不可移除',
      (await page.locator('.perm-members__item.is-removable').count()) === 0 &&
        (await page.locator('.perm-members__item').count()) > 0,
    );

    await page.screenshot({ path: path.join(OUT, 'srm-04-final.png') });
    check('页面无 JS 报错', errs.length === 0, errs.join(' | '));
  } catch (e) {
    fail++;
    log('!! 执行异常: ' + e.message);
    await page.screenshot({ path: path.join(OUT, 'srm-error.png') }).catch(() => {});
  } finally {
    // 清理：把临时用户从 base 摘掉并软删
    try {
      mysql(`DELETE FROM bitable_base_members WHERE base_id=${BASE_ID} AND user_id=${temp.id};`);
      mysql(`UPDATE users SET deleted_at=1, status='inactive' WHERE id=${temp.id};`);
      log(`[5] 清理完成（base 成员已删、临时用户已软删）`);
    } catch (e) {
      log('[5] 清理异常: ' + e.message);
    }
    log(`\n===== 结果：PASS ${pass} / FAIL ${fail} =====`);
    await browser.close();
    if (fail > 0) process.exitCode = 1;
  }
}

main();
