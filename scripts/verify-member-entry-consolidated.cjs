// 验证「成员管理」入口已从编辑器 Toolbar 的「更多」菜单移除，
// 且「高级权限」仍是成员管理的唯一入口（用户要求：同一功能只留一个入口）。
//
// 背景：MemberManager.vue（协作成员管理）与 PermissionManageDialog.vue（高级权限）都写同一份
// bitable_base_members（addBaseMember / updateBaseMemberRole / removeBaseMember），属于重复入口。
// 已删除 MemberManager.vue 及其全部接线，本脚本防止它被无意中加回来。
//
// 用法：NODE_PATH=<demand_frontend/node_modules> node scripts/verify-member-entry-consolidated.cjs
const path = require('node:path');
const fs = require('node:fs');

const BASE = 'http://127.0.0.1:5170';
const BASE_ID = 1;
const OUT = path.join(__dirname, 'out');

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

async function main() {
  const { chromium } = require('playwright');
  fs.mkdirSync(OUT, { recursive: true });

  const browser = await chromium.launch({ headless: true });
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const page = await ctx.newPage();
  const errs = [];
  page.on('pageerror', (e) => errs.push('pageerror: ' + e.message));
  page.on('console', (m) => {
    if (m.type() === 'error') errs.push('console: ' + m.text());
  });

  const moreItems = () =>
    page
      .locator('.el-dropdown-menu__item:visible')
      .evaluateAll((els) => els.map((el) => el.textContent.replace(/\s+/g, ' ').trim()));

  try {
    // ---------- 登录 + 进编辑器 ----------
    await page.goto(BASE + '/login', { waitUntil: 'domcontentloaded' });
    await page.locator('input[placeholder="请输入用户名"]').fill('admin');
    await page.locator('input[placeholder="请输入密码"]').fill('admin123');
    await page.locator('.login-btn').first().click();
    await page.waitForURL((u) => !u.pathname.includes('/login'), { timeout: 30000 });

    await page.goto(`${BASE}/bitable/${BASE_ID}`, { waitUntil: 'domcontentloaded' });
    await page.locator('.editor-sidebar').first().waitFor({ timeout: 30000 });
    await page.waitForTimeout(1500);
    log('[1] 编辑器已加载');

    // ---------- 「更多」菜单里不应再有「成员管理」 ----------
    const moreBtn = page.locator('button').filter({ hasText: '更多' }).first();
    await moreBtn.waitFor({ timeout: 15000 });
    await moreBtn.click();
    await page.locator('.el-dropdown-menu__item:visible').first().waitFor({ timeout: 8000 });
    const items = await moreItems();
    log('[2] 「更多」菜单项:', JSON.stringify(items));
    check('「更多」菜单不再包含「成员管理」', !items.some((t) => t.includes('成员管理')), JSON.stringify(items));
    check('其余菜单项未受影响（评论）', items.some((t) => t.includes('评论')));
    check('其余菜单项未受影响（操作记录）', items.some((t) => t.includes('操作记录')));
    check('其余菜单项未受影响（字段配置）', items.some((t) => t.includes('字段配置')));
    await page.screenshot({ path: path.join(OUT, 'mc-01-more-menu.png') });
    await page.keyboard.press('Escape');
    await page.waitForTimeout(400);

    // ---------- 「高级权限」仍是成员管理唯一入口 ----------
    const permBtn = page.locator('button').filter({ hasText: '高级权限' }).first();
    check('「高级权限」入口仍存在', (await permBtn.count()) === 1);
    await permBtn.click();
    await page.locator('.perm-body').waitFor({ timeout: 15000 });
    await page.waitForTimeout(600);
    log('[3] 高级权限弹窗已打开');
    check('高级权限弹窗正常渲染角色栏', (await page.locator('.perm-role-item').count()) >= 5);

    // 系统角色仍可维护成员（上一轮刚做的能力，别被这次收敛误伤）
    await page.locator('.perm-role-item').filter({ hasText: '只读' }).first().click();
    await page.waitForTimeout(700);
    check('系统角色仍有「添加成员」入口', (await page.locator('.perm-members__add').count()) === 1);
    await page.screenshot({ path: path.join(OUT, 'mc-02-permission.png') });

    check('页面无 JS 报错', errs.length === 0, errs.join(' | '));
  } catch (e) {
    fail++;
    log('!! 执行异常: ' + e.message);
    await page.screenshot({ path: path.join(OUT, 'mc-error.png') }).catch(() => {});
  } finally {
    log(`\n===== 结果：PASS ${pass} / FAIL ${fail} =====`);
    await browser.close();
    if (fail > 0) process.exitCode = 1;
  }
}

main();
