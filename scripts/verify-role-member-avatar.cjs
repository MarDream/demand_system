// 验证多维表格「高级权限」弹窗的角色重命名 + 角色成员（头像/首字缩略图）链路
// 用法：NODE_PATH=<demand_frontend/node_modules> node scripts/verify-role-member-avatar.cjs
const path = require('node:path');
const fs = require('node:fs');

const BASE = 'http://127.0.0.1:5170';
const BASE_ID = 1;
const OUT = path.join(__dirname, 'out');

const ROLE_A = '验收角色A';
const ROLE_B = '验收角色B';

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

  // ElMessage 会堆叠，断言前先等它清空
  const settle = async () => {
    await page
      .waitForFunction(() => !document.querySelector('.el-message'), { timeout: 8000 })
      .catch(() => {});
    await page.waitForTimeout(400);
  };

  const customRoles = () =>
    page.locator('.perm-role-item--custom').evaluateAll((els) =>
      els.map((el) => ({
        name: el.querySelector('.perm-role-item__name')?.textContent?.trim(),
        // badge 在 hover 时被 display:none 隐藏，innerText 会返回空，必须用 textContent
        badge: el.querySelector('.perm-role-item__badge')?.textContent?.trim() || '',
        active: el.classList.contains('active'),
      })),
    );

  const roleItem = (name) =>
    page.locator('.perm-role-item--custom').filter({ hasText: name }).first();

  const memberAvatars = () =>
    page.locator('.perm-members__avatar').evaluateAll((els) =>
      els.map((el) => {
        const img = el.querySelector('img');
        return {
          hasImg: !!img,
          src: img ? img.getAttribute('src') : null,
          text: (el.textContent || '').trim(),
          bg: el.style.backgroundColor || '',
        };
      }),
    );

  try {
    // ---------- 登录 ----------
    await page.goto(BASE + '/login', { waitUntil: 'domcontentloaded' });
    await page.locator('input[placeholder="请输入用户名"]').fill('admin');
    await page.locator('input[placeholder="请输入密码"]').fill('admin123');
    await page.locator('.login-btn').first().click();
    await page.waitForURL((u) => !u.pathname.includes('/login'), { timeout: 30000 });
    log('[1] 登录成功');

    // ---------- 打开编辑器并唤出「高级权限」 ----------
    await page.goto(`${BASE}/bitable/${BASE_ID}`, { waitUntil: 'domcontentloaded' });
    await page.locator('.editor-sidebar').first().waitFor({ timeout: 30000 });
    await page.waitForTimeout(1500);

    const permBtn = page.locator('button').filter({ hasText: '高级权限' }).first();
    await permBtn.waitFor({ timeout: 15000 });
    await permBtn.click();
    await page.locator('.perm-body').waitFor({ timeout: 15000 });
    await page.waitForTimeout(600);
    log('[2] 高级权限弹窗已打开');
    await page.screenshot({ path: path.join(OUT, 'rm-01-dialog.png') });

    const sysCount = await page.locator('.perm-role-item:not(.perm-role-item--custom)').count();
    check('系统角色区块正常渲染', sysCount > 0, `count=${sysCount}`);

    // ---------- 清理历史遗留的同名测试角色 ----------
    for (const stale of [ROLE_A, ROLE_B]) {
      let guard = 0;
      while ((await roleItem(stale).count()) && guard++ < 5) {
        const item = roleItem(stale);
        await item.hover();
        await item.locator('.perm-role-item__more-btn').click();
        await page.locator('.el-dropdown-menu__item:visible').filter({ hasText: '删除角色' }).first().click();
        await page.locator('.el-message-box__btns .el-button--primary').click();
        await settle();
      }
    }

    // ---------- 新建自定义角色 ----------
    await page.locator('.perm-role-add').click();
    // 注意：外层弹窗侧栏里也有「添加角色」文案，不能用 hasText 过滤，
    // 必须锚定占位符唯一的输入框（该子弹窗 append-to-body，渲染在 body 下）。
    const addRoleInput = page.locator('input[placeholder="请输入角色名称"]');
    await addRoleInput.waitFor({ timeout: 8000 });
    await addRoleInput.fill(ROLE_A);
    await page
      .locator('.el-dialog')
      .filter({ has: page.locator('input[placeholder="请输入角色名称"]') })
      .locator('.el-button--primary')
      .click();
    await settle();

    let roles = await customRoles();
    log('[3] 新建后自定义角色:', JSON.stringify(roles));
    check(`自定义角色「${ROLE_A}」创建成功`, roles.some((r) => r.name === ROLE_A));
    check('新角色初始成员数为 0', roles.find((r) => r.name === ROLE_A)?.badge === '0');

    // ---------- 重命名角色 ----------
    const target = roleItem(ROLE_A);
    await target.hover();
    await target.locator('.perm-role-item__more-btn').click();
    await page.locator('.el-dropdown-menu__item:visible').first().waitFor({ timeout: 8000 });
    const menuItems = await page
      .locator('.el-dropdown-menu__item:visible')
      .evaluateAll((els) => els.map((el) => el.textContent.trim()));
    log('[3.5] 角色更多操作菜单:', JSON.stringify(menuItems));
    check('自定义角色提供「重命名」入口', menuItems.includes('重命名'), JSON.stringify(menuItems));
    check('自定义角色提供「删除角色」入口', menuItems.includes('删除角色'), JSON.stringify(menuItems));
    await page.screenshot({ path: path.join(OUT, 'rm-02-role-menu.png') });
    await page.locator('.el-dropdown-menu__item:visible').filter({ hasText: '重命名' }).first().click();
    const promptInput = page.locator('.el-message-box__input input');
    await promptInput.waitFor({ timeout: 8000 });
    const prefilled = await promptInput.inputValue();
    check('重命名弹窗预填当前角色名', prefilled === ROLE_A, `got=${prefilled}`);
    await promptInput.fill(ROLE_B);
    await page.locator('.el-message-box__btns .el-button--primary').click();
    await settle();

    roles = await customRoles();
    log('[4] 重命名后自定义角色:', JSON.stringify(roles));
    check(`角色已重命名为「${ROLE_B}」`, roles.some((r) => r.name === ROLE_B));
    check('旧名称已不存在', !roles.some((r) => r.name === ROLE_A));
    await page.screenshot({ path: path.join(OUT, 'rm-02-renamed.png') });

    // ---------- 选中角色，查看成员区 ----------
    await roleItem(ROLE_B).click();
    await page.waitForTimeout(700);
    check('角色已被选中（高亮）', (await customRoles()).find((r) => r.name === ROLE_B)?.active === true);
    check(
      '自定义角色显示成员区（含添加按钮）',
      (await page.locator('.perm-members__add').count()) === 1,
    );
    check(
      '空角色显示「暂无成员」',
      (await page.locator('.perm-members__hint').first().textContent())?.includes('暂无成员'),
    );
    check(
      '自定义角色不出现「协作成员」提示',
      (await page.locator('.perm-members__tip').count()) === 0,
    );

    // ---------- 添加成员 ----------
    await page.locator('.perm-members__add').click();
    await page.locator('.member-add-dialog').waitFor({ timeout: 10000 });
    await page.waitForTimeout(500);
    // 候选人是内联勾选列表（不是下拉浮层）
    const firstOption = page.locator('.member-pick').first();
    await firstOption.waitFor({ timeout: 10000 });
    const optionCount = await page.locator('.member-pick').count();
    check('添加成员弹窗列出候选用户', optionCount > 0, `count=${optionCount}`);
    const optionAvatar = await page
      .locator('.member-pick .el-avatar')
      .first()
      .evaluate((el) => ({
        hasImg: !!el.querySelector('img'),
        text: (el.textContent || '').trim(),
      }));
    check(
      '候选项带缩略图（头像或首字）',
      optionAvatar.hasImg || optionAvatar.text.length > 0,
      JSON.stringify(optionAvatar),
    );
    await firstOption.click();
    await page.waitForTimeout(300);
    await page.screenshot({ path: path.join(OUT, 'rm-03-add-member.png') });
    await page
      .locator('.el-dialog')
      .filter({ has: page.locator('.member-add-dialog') })
      .locator('.el-button--primary')
      .click();
    await settle();

    // ---------- 断言头像缩略图 ----------
    const avatars = await memberAvatars();
    log('[5] 成员头像:', JSON.stringify(avatars));
    check('成员已添加并渲染缩略图', avatars.length === 1, `count=${avatars.length}`);
    check(
      '缩略图有内容（头像 img 或姓名首字）',
      avatars[0] && (avatars[0].hasImg || avatars[0].text.length === 1),
      JSON.stringify(avatars[0]),
    );
    check('缩略图有稳定底色', !!avatars[0]?.bg, avatars[0]?.bg);

    roles = await customRoles();
    check(`角色成员徽标更新为 1`, roles.find((r) => r.name === ROLE_B)?.badge === '1',
      JSON.stringify(roles));
    await page.screenshot({ path: path.join(OUT, 'rm-04-member-added.png') });

    // ---------- 移除成员 ----------
    await page.locator('.perm-members__item').first().click();
    await page.locator('.el-message-box__btns .el-button--primary').click();
    await settle();

    const afterRemove = await memberAvatars();
    check('成员已移除', afterRemove.length === 0, `count=${afterRemove.length}`);
    roles = await customRoles();
    check('成员徽标回落为 0', roles.find((r) => r.name === ROLE_B)?.badge === '0',
      JSON.stringify(roles));

    // ---------- 系统角色：也能直接维护成员（与自定义角色一致） ----------
    const ownerRole = page
      .locator('.perm-role-item:not(.perm-role-item--custom)')
      .filter({ hasText: '所有者' })
      .first();
    await ownerRole.click();
    await page.waitForTimeout(700);
    check('系统角色提供「添加成员」入口', (await page.locator('.perm-members__add').count()) === 1);
    check(
      '系统角色不再显示「协作成员」维护提示',
      (await page.locator('.perm-members__tip').count()) === 0,
    );
    const ownerAvatars = await memberAvatars();
    check('系统角色成员以缩略图展示', ownerAvatars.length > 0, `count=${ownerAvatars.length}`);
    check(
      '系统角色成员缩略图有内容',
      ownerAvatars[0] && (ownerAvatars[0].hasImg || ownerAvatars[0].text.length > 0),
      JSON.stringify(ownerAvatars[0]),
    );
    check(
      '所有者成员不可被移除（无 is-removable）',
      (await page.locator('.perm-members__item.is-removable').count()) === 0,
    );
    check(
      '系统角色不提供重命名/删除入口',
      (await page.locator('.perm-role-item--custom').count()) === 0 ||
        (await page.locator('.perm-role-item:not(.perm-role-item--custom) .perm-role-item__more-btn').count()) === 0,
    );
    await page.screenshot({ path: path.join(OUT, 'rm-05-system-role.png') });

    // ---------- 清理：删除测试角色 ----------
    const cleanup = roleItem(ROLE_B);
    await cleanup.hover();
    await cleanup.locator('.perm-role-item__more-btn').click();
    await page.locator('.el-dropdown-menu__item:visible').filter({ hasText: '删除角色' }).first().click();
    await page.locator('.el-message-box__btns .el-button--primary').click();
    await settle();
    roles = await customRoles();
    check(`测试角色「${ROLE_B}」已清理`, !roles.some((r) => r.name === ROLE_B), JSON.stringify(roles));

    await page.screenshot({ path: path.join(OUT, 'rm-05-final.png') });
    check('页面无 JS 报错', errs.length === 0, errs.join(' | '));
  } catch (e) {
    fail++;
    log('!! 执行异常: ' + e.message);
    await page.screenshot({ path: path.join(OUT, 'rm-error.png') }).catch(() => {});
  } finally {
    log(`\n===== 结果：PASS ${pass} / FAIL ${fail} =====`);
    await browser.close();
    if (fail > 0) process.exitCode = 1;
  }
}

main();
