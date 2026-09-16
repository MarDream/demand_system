// 用户管理「邀请成员 / 添加·申请记录 / 批量管理」界面回归。
//
// 用法（前端 dev server 已在 127.0.0.1:5170 运行）：
//   NODE_PATH=<demand_frontend/node_modules 的 Windows 路径> node verify-user-invitation-ui.cjs
//
// 覆盖：
//   1) 邀请成员 → 通过链接邀请：生成链接并展示
//   2) 邀请成员 → 批量邀请：多行录入 + 提交 + 结果反馈
//   3) 添加/申请记录：邀请记录 / 申请记录 两个 tab 有数据
//   4) 批量管理：未勾选时提示、勾选后批量停用/启用生效
//   5) 匿名打开邀请落地页，提交资料成功
//   6) 全程 0 个 JS 报错
const path = require('node:path');
const fs = require('node:fs');
const { execFileSync } = require('node:child_process');

let ok = 0;
let fail = 0;
const failures = [];

function check(label, cond, detail = '') {
  if (cond) {
    ok += 1;
    console.log(`  PASS  ${label}`);
  } else {
    fail += 1;
    failures.push(label);
    console.log(`  FAIL  ${label}  ${detail}`);
  }
}

const STAMP = String(Date.now()).slice(-8);
const PHONE = '137' + STAMP;
const EMAIL = `uicase${STAMP}@example.com`;
const LANDING_EMAIL = `landing${STAMP}@example.com`;

/** 清掉本轮造出来的邀请与申请记录（没有删除接口，直接走 SQL），保证脚本可反复执行 */
function cleanupTestData() {
  const sql = [
    "DELETE FROM demand_system.sys_join_requests WHERE applicant_name IN ('界面回归甲','界面回归乙','落地页回归员')",
    `  OR applicant_phone = '${PHONE}'`,
    `  OR applicant_email IN ('${EMAIL}','${LANDING_EMAIL}');`,
    "DELETE FROM demand_system.sys_invitations WHERE target_name IN ('界面回归甲','界面回归乙')",
    `  OR target = '${PHONE}' OR remark = 'UI回归';`,
  ].join('\n');
  try {
    // 必须带 --default-character-set=utf8mb4：客户端默认 latin1，
    // 中文条件会被转码成乱码，DELETE 静默命中 0 行
    execFileSync('docker', ['exec', '-i', 'mysql', 'mysql', '-uroot', '-padmin123', '--default-character-set=utf8mb4'], {
      input: sql,
      stdio: ['pipe', 'ignore', 'ignore'],
    });
    console.log('  (已清理本轮测试数据)');
  } catch (error) {
    console.log(`  (清理跳过：${error.message})`);
  }
}

(async () => {
  const { chromium } = require('playwright');

  const outDir = path.join(__dirname, 'out');
  if (!fs.existsSync(outDir)) fs.mkdirSync(outDir, { recursive: true });

  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({ viewport: { width: 1600, height: 950 } });
  const page = await context.newPage();

  const jsErrors = [];
  page.on('console', (msg) => {
    if (msg.type() === 'error') jsErrors.push(msg.text());
  });
  page.on('pageerror', (err) => jsErrors.push(err.message));

  const dismissMessages = async () => {
    await page.waitForFunction(() => !document.querySelector('.el-message'), { timeout: 8000 }).catch(() => {});
  };

  try {
    // ── 登录 ──
    await page.goto('http://127.0.0.1:5170/login', { waitUntil: 'domcontentloaded' });
    await page.locator('input[placeholder="请输入用户名"]').fill('admin');
    await page.locator('input[placeholder="请输入密码"]').fill('admin123');
    await page.locator('button:has-text("登录"), button:has-text("登 录"), button[type="submit"]').first().click();
    await page.waitForURL((u) => !u.pathname.includes('/login'), { timeout: 20000 });
    console.log('[1] 登录成功');

    // ── 打开用户管理 ──
    await page.goto('http://127.0.0.1:5170/settings/users', { waitUntil: 'domcontentloaded' });
    await page.waitForSelector('.member-table, .action-row', { timeout: 20000 });
    await page.waitForTimeout(1200);
    check('用户管理页加载', await page.locator('.action-row').isVisible());
    check('邀请成员入口存在', await page.locator('.action-row button:has-text("邀请成员")').isVisible());
    check('添加/申请记录入口存在', await page.locator('.action-row button:has-text("添加/申请记录")').isVisible());
    check('批量管理入口存在', await page.locator('.action-row button:has-text("批量管理")').isVisible());

    // ── 2. 通过链接邀请 ──
    await page.locator('.action-row button:has-text("邀请成员")').hover();
    await page.locator('.el-popper:visible .el-dropdown-menu__item:has-text("通过链接邀请")').first().click();
    await page.waitForTimeout(600);
    const inviteDialog = page.locator('.el-dialog:has-text("邀请成员")').first();
    check('邀请弹窗打开', await inviteDialog.isVisible());
    check('默认落在「通过链接邀请」', await inviteDialog.locator('.el-tabs__item.is-active:has-text("通过链接邀请")').isVisible());
    check('弹窗含批量邀请 tab', await inviteDialog.locator('.el-tabs__item:has-text("批量邀请")').isVisible());

    // 打上标记，方便收尾时精确清理本轮造出来的邀请
    await inviteDialog.locator('input[placeholder="选填，仅内部可见"]').fill('UI回归');
    await inviteDialog.locator('button:has-text("生成邀请链接")').click();
    await page.waitForSelector('.invite-link__text', { timeout: 10000 });
    const inviteUrl = (await page.locator('.invite-link__text').first().innerText()).trim();
    check('生成邀请链接并展示', /\/public\/invite\/[A-Z0-9]{10}$/.test(inviteUrl), inviteUrl);
    check('提示链接已生成', await page.locator('.invite-result__title').first().isVisible());
    await page.screenshot({ path: path.join(outDir, 'user-invite-link.png') });

    const inviteCode = inviteUrl.split('/').pop();
    await inviteDialog.locator('button:has-text("复制链接")').click();
    await page.waitForTimeout(500);
    await dismissMessages();

    // 关掉弹窗，避免遮挡后续操作
    await inviteDialog.locator('.el-dialog__headerbtn').click();
    await page.waitForTimeout(600);

    // ── 3. 批量邀请 ──
    await page.locator('.action-row button:has-text("邀请成员")').hover();
    await page.locator('.el-popper:visible .el-dropdown-menu__item:has-text("批量邀请")').first().click();
    await page.waitForTimeout(600);
    check('默认落在「批量邀请」', await page.locator('.el-dialog .el-tabs__item.is-active:has-text("批量邀请")').isVisible());
    check('批量表格默认 1 行', (await page.locator('.batch-table__row').count()) === 1);

    await page.locator('.batch-table__row').first().locator('input').nth(0).fill('界面回归甲');
    await page.locator('.batch-table__row').first().locator('input').nth(1).fill(PHONE);
    await page.locator('.batch-table__row').first().locator('input').nth(2).fill(EMAIL);

    await page.locator('button:has-text("添加一行")').click();
    await page.waitForTimeout(300);
    check('添加一行后共 2 行', (await page.locator('.batch-table__row').count()) === 2);

    await page.locator('.batch-table__row').nth(1).locator('input').nth(0).fill('界面回归乙');
    await page.locator('.batch-table__row').nth(1).locator('input').nth(1).fill('123');
    check('已填写人数统计', (await page.locator('.batch-toolbar__count').innerText()).includes('2'));
    await page.screenshot({ path: path.join(outDir, 'user-invite-batch-form.png') });

    await page.locator('button:has-text("提交邀请")').click();
    await page.waitForSelector('.batch-result', { timeout: 10000 });
    await page.waitForTimeout(600);
    const resultText = await page.locator('.batch-result').innerText();
    check('批量结果提示已生成 1 条', resultText.includes('已生成 1 条邀请'), resultText);
    check('非法手机号被跳过并说明原因', resultText.includes('手机号格式不正确'), resultText);
    await page.screenshot({ path: path.join(outDir, 'user-invite-batch-result.png') });
    await page.locator('button:has-text("关闭")').first().click();
    await page.waitForTimeout(600);
    await dismissMessages();

    // ── 4. 添加/申请记录 ──
    await page.locator('.action-row button:has-text("添加/申请记录")').click();
    await page.waitForSelector('.application-records-dialog', { timeout: 10000 });
    await page.waitForTimeout(1200);
    check('申请记录弹窗打开', await page.locator('.application-records-dialog').isVisible());
    const inviteRows = await page.locator('.application-records-dialog .el-table__body tbody tr').count();
    check('邀请记录有数据', inviteRows > 0, `rows=${inviteRows}`);
    await page.screenshot({ path: path.join(outDir, 'user-invite-records.png') });

    await page.locator('.application-records-dialog .el-tabs__item:has-text("申请记录")').click();
    await page.waitForTimeout(1000);
    const joinEmpty = await page.locator('.application-records-dialog .el-table__empty-block').count();
    check('申请记录 tab 可切换', await page.locator('.application-records-dialog .el-tabs__item.is-active:has-text("申请记录")').isVisible());
    check('申请记录 tab 表格已渲染', joinEmpty === 0 || (await page.locator('.application-records-dialog .el-empty').isVisible()));
    await page.locator('.application-records-dialog .el-dialog__headerbtn').click();
    await page.waitForTimeout(600);

    // ── 5. 批量管理 ──
    await page.locator('.action-row button:has-text("批量管理")').hover();
    await page.locator('.el-popper:visible .el-dropdown-menu__item:has-text("批量停用")').first().click();
    await page.waitForTimeout(700);
    const warn = await page.locator('.el-message').last().innerText().catch(() => '');
    check('未勾选时给出提示', warn.includes('勾选'), warn);
    await dismissMessages();

    // 勾选第一行（跳过主管理员行）
    const rowCheckboxes = page.locator('.member-table .el-table__body-wrapper .el-checkbox');
    const rowCount = await rowCheckboxes.count();
    let picked = -1;
    for (let i = 0; i < rowCount; i++) {
      const name = (await page.locator('.member-table .el-table__body tbody tr').nth(i).innerText()).trim();
      if (!name.includes('主管理员')) {
        await rowCheckboxes.nth(i).click();
        picked = i;
        break;
      }
    }
    check('可勾选成员行', picked >= 0, `rowCount=${rowCount}`);
    await page.waitForTimeout(500);

    await page.locator('.action-row button:has-text("批量管理")').hover();
    await page.locator('.el-popper:visible .el-dropdown-menu__item:has-text("批量停用")').first().click();
    await page.waitForTimeout(700);
    check('弹出二次确认', await page.locator('.el-message-box').isVisible());
    await page.screenshot({ path: path.join(outDir, 'user-batch-confirm.png') });
    await page.locator('.el-message-box__btns .el-button--primary').click();
    await page.waitForTimeout(1500);
    const stopMsg = await page.locator('.el-message').last().innerText().catch(() => '');
    check('批量停用成功提示', stopMsg.includes('批量停用完成'), stopMsg);
    await dismissMessages();

    // 复原：勾选同一行再批量启用
    await page.locator('.member-table .el-table__body-wrapper .el-checkbox').nth(picked).click();
    await page.waitForTimeout(400);
    await page.locator('.action-row button:has-text("批量管理")').hover();
    await page.locator('.el-popper:visible .el-dropdown-menu__item:has-text("批量启用")').first().click();
    await page.waitForTimeout(700);
    await page.locator('.el-message-box__btns .el-button--primary').click();
    await page.waitForTimeout(1500);
    const startMsg = await page.locator('.el-message').last().innerText().catch(() => '');
    check('批量启用成功提示', startMsg.includes('批量启用完成'), startMsg);
    await dismissMessages();

    // ── 6. 匿名邀请落地页 ──
    const anonContext = await browser.newContext({ viewport: { width: 1280, height: 900 } });
    const anonPage = await anonContext.newPage();
    await anonPage.goto(inviteUrl, { waitUntil: 'domcontentloaded' });
    await anonPage.waitForSelector('.invite-card', { timeout: 15000 });
    await anonPage.waitForTimeout(1200);
    const cardText = await anonPage.locator('.invite-card').innerText();
    check('落地页展示邀请人', cardText.includes('系统管理员'), cardText.slice(0, 120));
    check('落地页展示目标组织', cardText.includes('邀请你加入'), cardText.slice(0, 120));
    await anonPage.screenshot({ path: path.join(outDir, 'user-invite-landing.png') });

    await anonPage.locator('input[placeholder="请输入真实姓名"]').fill('落地页回归员');
    await anonPage.locator('input[placeholder="用于生成初始密码，请填写正确"]').fill('136' + STAMP);
    await anonPage.locator('input[placeholder="初始密码将发送到该邮箱"]').fill(LANDING_EMAIL);
    await anonPage.locator('button:has-text("提交申请")').click();
    await anonPage.waitForTimeout(2000);
    const landingText = await anonPage.locator('.invite-card').innerText();
    check('落地页提交成功', landingText.includes('申请已提交'), landingText.slice(0, 120));
    await anonPage.screenshot({ path: path.join(outDir, 'user-invite-landing-success.png') });
    await anonContext.close();

    // 新提交的申请应出现在「申请记录」里
    await page.reload({ waitUntil: 'domcontentloaded' });
    await page.waitForSelector('.action-row', { timeout: 20000 });
    await page.waitForTimeout(1200);
    await page.locator('.action-row button:has-text("添加/申请记录")').click();
    await page.waitForSelector('.application-records-dialog', { timeout: 10000 });
    await page.waitForTimeout(1200);
    await page.locator('.application-records-dialog .el-tabs__item:has-text("申请记录")').click();
    await page.waitForTimeout(1500);
    // 两个 tab 的表格都渲染在 DOM 里，必须限定到当前可见的 pane，否则 strict mode 会报错
    const joinText = await page
      .locator('.application-records-dialog .el-tab-pane:visible .el-table__body')
      .innerText()
      .catch(() => '');
    check('新申请出现在申请记录中', joinText.includes('落地页回归员'), joinText.slice(0, 200));
    await page.screenshot({ path: path.join(outDir, 'user-join-requests.png') });
    await page.locator('.application-records-dialog .el-dialog__headerbtn').click();
    await page.waitForTimeout(500);

    // ── 7. 无 JS 报错 ──
    const realErrors = jsErrors.filter(
      (e) => !/favicon|ResizeObserver|Download the Vue Devtools/i.test(e),
    );
    check('全程无 JS 报错', realErrors.length === 0, realErrors.slice(0, 3).join(' | '));

    console.log(`\n邀请码：${inviteCode}`);
  } catch (error) {
    fail += 1;
    failures.push('脚本异常');
    console.log(`  FAIL  脚本异常：${error.message}`);
    await page.screenshot({ path: path.join(outDir, 'user-invite-error.png') }).catch(() => {});
  } finally {
    await browser.close();
    cleanupTestData();
  }

  console.log(`\n==== 通过 ${ok} / 失败 ${fail} ====`);
  if (failures.length) console.log('失败项：' + failures.join('、'));
  process.exit(fail ? 1 : 0);
})();
