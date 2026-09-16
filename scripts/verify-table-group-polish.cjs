// 验证：折叠状态持久化 + 搜索关键词高亮（不产生测试数据）
// 用法：NODE_PATH=<demand_frontend/node_modules> node scripts/verify-table-group-polish.cjs
const path = require('node:path');
const fs = require('node:fs');

const BASE = 'http://127.0.0.1:5170';
const OUT = path.join(__dirname, 'out');
const log = (...a) => console.log(...a);

let pass = 0, fail = 0;
const check = (label, ok, extra) => {
  if (ok) { pass++; log('  PASS  ' + label); }
  else { fail++; log('  FAIL  ' + label + (extra ? '  ' + extra : '')); }
};

async function main() {
  const { chromium } = require('playwright');
  fs.mkdirSync(OUT, { recursive: true });
  const browser = await chromium.launch({ headless: true });
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const page = await ctx.newPage();
  const errs = [];
  page.on('pageerror', (e) => errs.push('pageerror: ' + e.message));
  page.on('console', (m) => { if (m.type() === 'error') errs.push('console: ' + m.text()); });

  const names = () => page.locator('.tgt-node__name').allInnerTexts().then((a) => a.map((s) => s.trim()));
  const snap = () => page.locator('.tgt-node').evaluateAll((els) => els.map((el) => ({
    indent: parseInt(el.style.paddingLeft) || 0,
    name: el.querySelector('.tgt-node__name')?.textContent?.trim(),
    count: el.querySelector('.tgt-node__count')?.textContent?.trim() || '',
  })));
  const allChev = () => page.locator('.tgt-node[data-key="all"] .tgt-node__chev');

  try {
    await page.goto(BASE + '/login', { waitUntil: 'domcontentloaded' });
    await page.locator('input[placeholder="请输入用户名"]').fill('admin');
    await page.locator('input[placeholder="请输入密码"]').fill('admin123');
    await page.locator('.login-btn').first().click();
    await page.waitForURL((u) => !u.pathname.includes('/login'), { timeout: 30000 });

    // ---------- 折叠状态持久化 ----------
    log('[1] 折叠状态持久化');
    await page.goto(`${BASE}/bitable/1`, { waitUntil: 'domcontentloaded' });
    await page.locator('.tgt-node').first().waitFor({ timeout: 30000 });
    await page.waitForTimeout(1000);

    const initNames = await names();
    log('    默认:', initNames.join(' / '));
    check('「全部实体」默认折叠（表不在平铺里重复出现）',
      initNames.filter((n) => n === '项目管理').length === 1, initNames.join('|'));
    check('首次进入 localStorage 为空',
      (await page.evaluate(() => localStorage.getItem('bitable.tableGroupTree.collapsed.1'))) === null);

    // 展开「全部实体」
    await allChev().click();
    await page.waitForTimeout(600);
    const expandedNames = await names();
    log('    展开全部实体后:', expandedNames.join(' / '));
    check('展开后平铺出全部数据表',
      expandedNames.filter((n) => n === '项目管理').length === 2, expandedNames.join('|'));
    const stored = await page.evaluate(() => localStorage.getItem('bitable.tableGroupTree.collapsed.1'));
    log('    localStorage:', stored);
    check('折叠状态已写入 localStorage', stored === '[]', String(stored));

    // 刷新后应保持展开
    await page.reload({ waitUntil: 'domcontentloaded' });
    await page.locator('.tgt-node').first().waitFor({ timeout: 30000 });
    await page.waitForTimeout(1000);
    const afterReload = await names();
    log('    刷新后:', afterReload.join(' / '));
    check('刷新后仍是展开态（持久化生效）',
      afterReload.filter((n) => n === '项目管理').length === 2, afterReload.join('|'));

    // 折叠回去，刷新后应保持折叠
    await allChev().click();
    await page.waitForTimeout(600);
    await page.reload({ waitUntil: 'domcontentloaded' });
    await page.locator('.tgt-node').first().waitFor({ timeout: 30000 });
    await page.waitForTimeout(1000);
    const afterCollapseReload = await names();
    check('折叠回去后刷新仍保持折叠',
      afterCollapseReload.filter((n) => n === '项目管理').length === 1, afterCollapseReload.join('|'));

    // 清理 localStorage，避免影响后续手动使用
    await page.evaluate(() => localStorage.removeItem('bitable.tableGroupTree.collapsed.1'));

    // ---------- 搜索高亮 ----------
    log('\n[2] 搜索关键词高亮');
    await page.locator('.tgt-search input').fill('人员');
    await page.waitForTimeout(800);
    const marks = await page.locator('.tgt-node__name em').allInnerTexts();
    log('    高亮片段:', JSON.stringify(marks));
    check('命中片段被 <em> 高亮', marks.length > 0 && marks.every((t) => t === '人员'), JSON.stringify(marks));
    const emStyle = await page.locator('.tgt-node__name em').first().evaluate((el) => {
      const s = getComputedStyle(el);
      return { bg: s.backgroundColor, color: s.color, style: s.fontStyle };
    });
    log('    高亮样式:', JSON.stringify(emStyle));
    check('高亮样式生效（黄底、非斜体）',
      emStyle.style === 'normal' && emStyle.bg !== 'rgba(0, 0, 0, 0)', JSON.stringify(emStyle));

    // 非命中：不应有高亮，树里只剩「全部实体」这一个容器节点
    await page.locator('.tgt-search input').fill('zzz-不存在');
    await page.waitForTimeout(800);
    const noHitNames = await names();
    check('无命中时无高亮且只剩「全部实体」',
      (await page.locator('.tgt-node__name em').count()) === 0 &&
        noHitNames.length === 1 && noHitNames[0] === '全部实体',
      JSON.stringify(noHitNames));

    await page.locator('.tgt-search input').fill('人员');
    await page.waitForTimeout(800);
    await page.screenshot({ path: path.join(OUT, 'polish-highlight.png') });
    await page.locator('.tgt-search input').fill('');
    await page.waitForTimeout(500);

    // ---------- 「移动到分组…」对话框（拖拽之外的另一入口，走完整往返） ----------
    log('\n[3] 移动到分组对话框');
    const TMP_GROUP = '__P_MOVE';
    const show = (list) => list.map((n) => `${n.name}${n.count ? `(${n.count})` : ''}@${n.indent}`).join(' / ');

    await page.locator('.tgt-header__btn[title="新建分组"]').click();
    await page.locator('.tgt-editor input').waitFor({ timeout: 5000 });
    await page.locator('.tgt-editor input').fill(TMP_GROUP);
    await page.keyboard.press('Enter');
    await page.waitForTimeout(1300);
    check('临时分组已创建', (await names()).includes(TMP_GROUP));

    const openMoveDialog = async (tableName) => {
      const r = page.locator('.tgt-node--table').filter({ hasText: tableName }).first();
      await r.hover();
      await r.locator('.tgt-node__tool[title="更多操作"]').click();
      await page.waitForTimeout(500);
      await page.locator('.el-dropdown-menu__item:visible').filter({ hasText: '移动到分组' }).first().click();
      await page.waitForSelector('.el-dialog:visible', { timeout: 8000 });
      await page.waitForTimeout(400);
    };

    await openMoveDialog('项目管理');
    const dlgTitle = await page.locator('.el-dialog:visible .el-dialog__title').innerText();
    check('对话框打开且标题正确', dlgTitle === '移动到分组', dlgTitle);
    check('对话框内有选择控件', (await page.locator('.el-dialog:visible .el-select').count()) === 1);

    // 展开下拉，应出现「未分组（根层级）」与临时分组
    // 注意：Element Plus 的 ElTreeSelect 选项渲染为 .el-select-dropdown__item（不是 el-tree-node__label），
    // 且 label 带全角缩进空格，比较时用 includes 而非全等
    await page.locator('.el-dialog:visible .el-select__wrapper').click();
    await page.waitForTimeout(800);
    const options = (await page.locator('.el-select-dropdown__item:visible').allInnerTexts()).map((s) => s.trim());
    log('    下拉选项:', JSON.stringify(options));
    check('下拉含「未分组（根层级）」', options.some((o) => o.includes('未分组（根层级）')), JSON.stringify(options));
    check(`下拉含临时分组「${TMP_GROUP}」`, options.some((o) => o.includes(TMP_GROUP)), JSON.stringify(options));

    // 选临时分组并确认
    await page.locator('.el-select-dropdown__item:visible').filter({ hasText: TMP_GROUP }).first().click();
    await page.waitForTimeout(500);
    await page.locator('.el-dialog:visible .el-button--primary').click();
    await page.waitForTimeout(1500);
    const moved = await snap();
    log('    移入后:', show(moved));
    const gTmp = moved.find((n) => n.name === TMP_GROUP);
    const tProj = moved.find((n) => n.name === '项目管理');
    check(`「项目管理」已移入「${TMP_GROUP}」`, gTmp?.count === '1' && !!tProj && tProj.indent > gTmp.indent,
      `tmp=${gTmp?.count} proj@${tProj?.indent} grp@${gTmp?.indent}`);

    // 再移回根层级
    await openMoveDialog('项目管理');
    await page.locator('.el-dialog:visible .el-select__wrapper').click();
    await page.waitForTimeout(800);
    await page.locator('.el-select-dropdown__item:visible').filter({ hasText: '未分组（根层级）' }).first().click();
    await page.waitForTimeout(500);
    await page.locator('.el-dialog:visible .el-button--primary').click();
    await page.waitForTimeout(1500);
    const restored = await snap();
    log('    移回后:', show(restored));
    check('「项目管理」已移回根层级',
      restored.find((n) => n.name === TMP_GROUP)?.count === '0' &&
        restored.find((n) => n.name === '项目管理')?.indent === 20,
      show(restored));

    // 删除临时分组
    const tmpRow = page.locator('.tgt-node--group').filter({ hasText: TMP_GROUP }).first();
    await tmpRow.hover();
    await tmpRow.locator('.tgt-node__tool[title="更多操作"]').click();
    await page.waitForTimeout(500);
    await page.locator('.el-dropdown-menu__item:visible').filter({ hasText: '删除分组' }).first().click();
    await page.locator('.el-message-box__btns .el-button--primary').first().click();
    await page.waitForTimeout(1500);
    const cleaned = await snap();
    log('    清理后:', show(cleaned));
    check('临时分组已清理，树恢复原状',
      !cleaned.some((n) => n.name === TMP_GROUP) && cleaned.length === 4, show(cleaned));

    check('页面无 JS 报错', errs.length === 0, errs.join(' | '));
  } catch (e) {
    fail++;
    log('!! 执行异常: ' + e.message + '\n' + e.stack);
    await page.screenshot({ path: path.join(OUT, 'polish-error.png') }).catch(() => {});
  } finally {
    log(`\n===== 结果：PASS ${pass} / FAIL ${fail} =====`);
    await browser.close();
    if (fail > 0) process.exitCode = 1;
  }
}

main();
