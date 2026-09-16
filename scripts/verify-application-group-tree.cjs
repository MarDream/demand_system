// 验证「模型配置 → 模型应用」页的左侧功能点分组目录树端到端行为
// 用法：NODE_PATH=<demand_frontend/node_modules> node scripts/verify-application-group-tree.cjs
const path = require('node:path');
const fs = require('node:fs');

const BASE = 'http://127.0.0.1:5170';
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

  const snap = () =>
    page.locator('.agtree-node').evaluateAll((els) =>
      els.map((el) => ({
        key: el.dataset.key,
        indent: parseInt(el.style.paddingLeft) || 0,
        name: el.querySelector('.agtree-node__name')?.textContent?.trim(),
        count: el.querySelector('.agtree-node__count')?.textContent?.trim() || '',
      })),
    );
  const names = (s) => s.map((n) => `${n.name}${n.count ? `(${n.count})` : ''}@${n.indent}`);
  const groupRow = (name) => page.locator('.agtree-node--group').filter({ hasText: name }).first();
  const menuItem = (text) =>
    page.locator('.el-dropdown-menu__item:visible').filter({ hasText: text }).first();
  const cards = () => page.locator('.application-card');
  const scopeTitle = () => page.locator('.application-intro h3').first().textContent();

  try {
    // ---------- 登录 ----------
    await page.goto(BASE + '/login', { waitUntil: 'domcontentloaded' });
    await page.locator('input[placeholder="请输入用户名"]').fill('admin');
    await page.locator('input[placeholder="请输入密码"]').fill('admin123');
    await page.locator('.login-btn').first().click();
    await page.waitForURL((u) => !u.pathname.includes('/login'), { timeout: 30000 });
    log('[1] 登录成功');

    // ---------- 打开模型配置 -> 模型应用 ----------
    await page.goto(`${BASE}/settings/llm`, { waitUntil: 'domcontentloaded' });
    await page.locator('.el-tabs__item', { hasText: '模型应用' }).first().click();
    await page.locator('.agtree-node').first().waitFor({ timeout: 30000 });
    await page.waitForTimeout(800);
    const init = await snap();
    log('[2] 初始树:', names(init));

    check('左侧目录树已渲染', init.length > 0);
    check('存在「全部应用」虚拟节点', init.some((n) => n.name === '全部应用'));
    check('默认展示全部功能点（10 个）',
      (await cards().count()) === 10,
      'cards=' + (await cards().count()));
    check('默认标题为「全部应用」', (await scopeTitle())?.trim() === '全部应用');

    // 「全部应用」默认折叠（分组默认展开），因此每个功能点在树里只出现一次，不会重复平铺
    const appNames = init.filter((n) => !n.count).map((n) => n.name);
    const dupes = appNames.filter((n, i) => appNames.indexOf(n) !== i);
    check('「全部应用」默认折叠，功能点在树中不重复', dupes.length === 0, 'dupes=' + dupes.join(','));
    check('全部应用展开后功能点总数与右侧一致',
      init.find((n) => n.name === '全部应用')?.count === '10');

    const groups = init.filter((n) => n.key && n.key.startsWith('g:'));
    log('  分组节点:', groups.map((g) => `${g.name}(${g.count})`).join(', '));
    check('存在预置分组「智能助手 / 知识库 / 其他能力」',
      ['智能助手', '知识库', '其他能力'].every((n) => groups.some((g) => g.name === n)));
    await page.screenshot({ path: path.join(OUT, 'apptree-01-init.png') });

    // ---------- 点击分组：右侧范围收敛 ----------
    await groupRow('知识库').click();
    await page.waitForTimeout(700);
    check('点击「知识库」后标题切换', (await scopeTitle())?.trim() === '知识库');
    check('「知识库」下展示 6 个功能点',
      (await cards().count()) === 6,
      'cards=' + (await cards().count()));
    const afterGroupClick = await snap();
    log('[3] 展开知识库后:', names(afterGroupClick));
    check('分组展开后列出其功能点',
      afterGroupClick.some((n) => n.name === '知识库问答'));
    await page.screenshot({ path: path.join(OUT, 'apptree-02-group-selected.png') });

    // ---------- 点击单个功能点 ----------
    await page.locator('.agtree-node--application').filter({ hasText: '知识库向量化' }).first().click();
    await page.waitForTimeout(700);
    check('点击功能点后只展示该功能点', (await cards().count()) === 1,
      'cards=' + (await cards().count()));
    check('点击功能点后标题为该功能点名', (await scopeTitle())?.trim() === '知识库向量化');
    await page.screenshot({ path: path.join(OUT, 'apptree-03-application-selected.png') });

    // ---------- 新建 / 重命名 / 删除分组 ----------
    await page.locator('.agtree-header__btn[title="新建分组"]').click();
    await page.locator('.agtree-editor input').waitFor({ timeout: 5000 });
    await page.locator('.agtree-editor input').fill('UI 校验临时分组');
    await page.keyboard.press('Enter');
    await page.waitForTimeout(1500);
    const afterCreate = await snap();
    log('[4] 新建分组后:', names(afterCreate));
    check('新建根分组已出现在树中', afterCreate.some((n) => n.name === 'UI 校验临时分组'));

    await groupRow('UI 校验临时分组').hover();
    await groupRow('UI 校验临时分组').locator('.agtree-node__tool[title="更多操作"]').click();
    await menuItem('重命名').click();
    await page.locator('.el-message-box__input input').fill('UI 校验分组（已改名）');
    await page.locator('.el-message-box__btns .el-button--primary').click();
    await page.waitForTimeout(1500);
    const afterRename = await snap();
    check('分组重命名生效', afterRename.some((n) => n.name === 'UI 校验分组（已改名）'));

    await groupRow('UI 校验分组（已改名）').hover();
    await groupRow('UI 校验分组（已改名）').locator('.agtree-node__tool[title="更多操作"]').click();
    await menuItem('删除分组').click();
    await page.locator('.el-message-box__btns .el-button--primary').click();
    await page.waitForTimeout(1500);
    const afterDelete = await snap();
    check('分组删除生效', !afterDelete.some((n) => n.name === 'UI 校验分组（已改名）'));
    await page.screenshot({ path: path.join(OUT, 'apptree-04-after-crud.png') });

    // ---------- 功能点「移动到分组」入口 ----------
    await groupRow('知识库').click();
    await page.waitForTimeout(500);
    const rerank = page.locator('.agtree-node--application').filter({ hasText: '知识库重排' }).first();
    await rerank.hover();
    await rerank.locator('.agtree-node__tool[title="更多操作"]').click();
    await menuItem('移动到分组').click();
    const dlg = page.locator('.el-dialog').filter({ hasText: '移动到分组' }).first();
    await dlg.waitFor({ timeout: 5000 });
    check('「移动到分组」弹窗可打开', await dlg.isVisible());
    await page.screenshot({ path: path.join(OUT, 'apptree-05-move-dialog.png') });
    await page.locator('.el-dialog').filter({ hasText: '移动到分组' }).locator('button:has-text("取消")').click();
    await page.waitForTimeout(400);

    // ---------- 控制台错误 ----------
    check('无前端运行时错误', errs.length === 0, errs.slice(0, 5).join(' | '));

    log(`\n结果: ${pass} passed, ${fail} failed`);
    if (errs.length) log('错误明细:\n' + errs.slice(0, 10).join('\n'));
  } catch (e) {
    fail++;
    log('  FAIL  脚本异常: ' + e.message);
    try {
      await page.screenshot({ path: path.join(OUT, 'apptree-error.png') });
    } catch {}
  } finally {
    await browser.close();
  }

  process.exit(fail === 0 ? 0 : 1);
}

main();
