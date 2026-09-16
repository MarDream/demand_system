// 验证多维表格「数据表分组目录树」端到端行为
// 用法：NODE_PATH=<demand_frontend/node_modules> node scripts/verify-table-group-tree.cjs
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

  const snap = () =>
    page.locator('.tgt-node').evaluateAll((els) =>
      els.map((el) => ({
        key: el.dataset.key,
        indent: parseInt(el.style.paddingLeft) || 0,
        name: el.querySelector('.tgt-node__name')?.textContent?.trim(),
        count: el.querySelector('.tgt-node__count')?.textContent?.trim() || '',
      })),
    );
  const names = (s) => s.map((n) => `${n.name}${n.count ? `(${n.count})` : ''}@${n.indent}`);
  const groupRow = (name) => page.locator('.tgt-node--group').filter({ hasText: name }).first();
  const tableRow = (name) => page.locator('.tgt-node--table').filter({ hasText: name }).first();
  const menuItem = (text) =>
    page.locator('.el-dropdown-menu__item:visible').filter({ hasText: text }).first();

  try {
    // ---------- 登录 ----------
    await page.goto(BASE + '/login', { waitUntil: 'domcontentloaded' });
    await page.locator('input[placeholder="请输入用户名"]').fill('admin');
    await page.locator('input[placeholder="请输入密码"]').fill('admin123');
    await page.locator('.login-btn').first().click();
    await page.waitForURL((u) => !u.pathname.includes('/login'), { timeout: 30000 });
    log('[1] 登录成功');

    // ---------- 打开编辑器 ----------
    await page.goto(`${BASE}/bitable/${BASE_ID}`, { waitUntil: 'domcontentloaded' });
    await page.locator('.tgt-node').first().waitFor({ timeout: 30000 });
    await page.waitForTimeout(1000);
    const init = await snap();
    log('[2] 初始树:', names(init));
    check('「全部实体」默认折叠（表只出现一次）',
      init.filter((n) => n.name === '项目管理').length === 1);
    check('根层级「未分组」列出全部数据表',
      init.find((n) => n.name === '未分组')?.count === '2');
    await page.screenshot({ path: path.join(OUT, 'gt-01-init.png') });

    // ---------- 新建根分组 ----------
    await page.locator('.tgt-header__btn[title="新建分组"]').click();
    await page.locator('.tgt-editor input').waitFor({ timeout: 5000 });
    await page.locator('.tgt-editor input').fill('进销存系统（JXC）');
    await page.keyboard.press('Enter');
    await page.waitForTimeout(1200);
    const afterRoot = await snap();
    log('[3] 新建根分组后:', names(afterRoot));
    check('根分组已创建', afterRoot.some((n) => n.name === '进销存系统（JXC）'));

    // ---------- 在 JXC 下新建两个子分组 ----------
    for (const name of ['销售', '采购']) {
      await groupRow('进销存系统').hover();
      await groupRow('进销存系统').locator('.tgt-node__tool[title="新建子分组"]').click();
      await page.locator('.tgt-editor input').waitFor({ timeout: 5000 });
      await page.locator('.tgt-editor input').fill(name);
      await page.keyboard.press('Enter');
      await page.waitForTimeout(1200);
    }
    const afterSub = await snap();
    log('[4] 建子分组后:', names(afterSub));
    const jxcIndent = afterSub.find((n) => n.name === '进销存系统（JXC）')?.indent;
    check(
      '子分组缩进深于父分组',
      afterSub.find((n) => n.name === '销售')?.indent > jxcIndent &&
        afterSub.find((n) => n.name === '采购')?.indent > jxcIndent,
      `JXC=${jxcIndent}`,
    );

    // ---------- 拖拽数据表归组 ----------
    await tableRow('项目管理').dragTo(groupRow('销售'));
    await page.waitForTimeout(1500);
    const afterDrag = await snap();
    log('[5] 拖拽归组后:', names(afterDrag));
    const salesAfter = afterDrag.find((n) => n.name === '销售');
    const projInSales = afterDrag.filter((n) => n.name === '项目管理').pop();
    check(
      '数据表已拖入「销售」分组',
      !!projInSales && projInSales.indent > salesAfter.indent,
      `销售@${salesAfter?.indent} 项目管理@${projInSales?.indent}`,
    );
    check('分组计数更新：销售=1', salesAfter?.count === '1');
    check(
      '分组计数向上汇总：JXC=1',
      afterDrag.find((n) => n.name === '进销存系统（JXC）')?.count === '1',
    );
    check(
      '根层级未分组计数减为 1',
      afterDrag.find((n) => n.name === '未分组')?.count === '1',
    );
    await page.screenshot({ path: path.join(OUT, 'gt-02-after-drag.png') });

    // ---------- 折叠 / 展开 ----------
    await groupRow('销售').locator('.tgt-node__chev').click();
    await page.waitForTimeout(500);
    const collapsed = await snap();
    const salesCollapsed = collapsed.find((n) => n.name === '销售');
    log('[6] 折叠「销售」后:', names(collapsed));
    check(
      '折叠后该分组下的数据表隐藏',
      !collapsed.some((n) => n.name === '项目管理' && n.indent > salesCollapsed.indent),
    );

    await groupRow('销售').locator('.tgt-node__chev').click();
    await page.waitForTimeout(500);
    const reexpanded = await snap();
    const salesRe = reexpanded.find((n) => n.name === '销售');
    check(
      '重新展开后数据表恢复显示',
      reexpanded.some((n) => n.name === '项目管理' && n.indent > salesRe.indent),
    );

    // ---------- 搜索 ----------
    await page.locator('.tgt-search input').fill('人员');
    await page.waitForTimeout(700);
    const searched = await snap();
    log('[7] 搜索「人员」:', names(searched));
    check(
      '非命中项被过滤（仅保留命中表 + 容器节点）',
      searched.every((n) => n.name.includes('人员') || n.name === '未分组' || n.key === 'all'),
    );
    check('命中表只出现一次', searched.filter((n) => n.name === '人员座位表').length === 1);
    await page.locator('.tgt-search input').fill('');
    await page.waitForTimeout(700);

    // ---------- 重命名 ----------
    await groupRow('采购').hover();
    await groupRow('采购').locator('.tgt-node__tool[title="更多操作"]').click();
    await menuItem('重命名').click();
    await page.locator('.el-message-box__input input').waitFor({ timeout: 5000 });
    await page.locator('.el-message-box__input input').fill('采购管理');
    await page.locator('.el-message-box__btns .el-button--primary').click();
    await page.waitForTimeout(1300);
    check('分组已重命名为「采购管理」', (await snap()).some((n) => n.name === '采购管理'));

    // ---------- 删除分组：子项上移 ----------
    await groupRow('销售').hover();
    await groupRow('销售').locator('.tgt-node__tool[title="更多操作"]').click();
    await menuItem('删除分组').click();
    await page
      .locator('.el-message-box__btns .el-button--danger, .el-message-box__btns .el-button--primary')
      .first()
      .click();
    await page.waitForTimeout(1600);
    const afterDel = await snap();
    log('[8] 删除「销售」后:', names(afterDel));
    const iJxc = afterDel.findIndex((n) => n.name === '进销存系统（JXC）');
    const iProj = afterDel.findIndex((n) => n.name === '项目管理');
    const iPurch = afterDel.findIndex((n) => n.name === '采购管理');
    check('被删分组已消失', !afterDel.some((n) => n.name === '销售'));
    check('分组内的数据表未被级联删除', iProj >= 0);
    check(
      '数据表上移到父分组（位于「采购管理」之前）',
      iProj > iJxc && iProj < iPurch && afterDel[iProj].indent > afterDel[iJxc].indent,
      `JXC=${iJxc} 项目管理=${iProj} 采购管理=${iPurch}`,
    );

    // ---------- 全部实体平铺 ----------
    await page.locator('.tgt-node[data-key="all"] .tgt-node__chev').click();
    await page.waitForTimeout(500);
    const flat = await snap();
    check(
      '展开「全部实体」后平铺所有数据表',
      flat.filter((n) => n.name === '项目管理').length === 2,
      JSON.stringify(names(flat)),
    );
    await page.locator('.tgt-node[data-key="all"] .tgt-node__chev').click();
    await page.waitForTimeout(500);

    // ---------- 点击数据表切换 ----------
    const before = await page.locator('.tgt-node--table.is-active .tgt-node__name').innerText();
    await tableRow('人员座位表').click();
    await page.waitForTimeout(1200);
    const after = await page.locator('.tgt-node--table.is-active .tgt-node__name').innerText();
    check(`点击数据表切换激活项（${before} -> ${after}）`, before !== after && after === '人员座位表');

    await page.screenshot({ path: path.join(OUT, 'gt-03-final.png') });
    await page.locator('.editor-sidebar').screenshot({ path: path.join(OUT, 'gt-04-sidebar.png') });

    check('页面无 JS 报错', errs.length === 0, errs.join(' | '));
  } catch (e) {
    fail++;
    log('!! 执行异常: ' + e.message);
    await page.screenshot({ path: path.join(OUT, 'gt-error.png') }).catch(() => {});
  } finally {
    log(`\n===== 结果：PASS ${pass} / FAIL ${fail} =====`);
    await browser.close();
    if (fail > 0) process.exitCode = 1;
  }
}

main();
