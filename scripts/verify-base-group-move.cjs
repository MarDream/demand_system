// 验证「多维表格」列表页左侧分组树（BaseGroupTree.vue）的「移动到分组」能力
//
// 回归的 bug：未分组的多维表格移动到自定义分组后
//   1) 自定义分组下看不到移动过去的多维表格（右侧范围一直「共 0 个」）
//   2) 该多维表格仍然留在「未分组」里
// 根因：BitableBaseMapper.xml 的三个 SELECT 漏查 group_id，
//      导致 GET /api/v1/bitable/bases 返回的 groupId 恒为 null。
//
// 覆盖：
//   - 树初始渲染（分组节点下的 Base 归属正确、未分组计数正确）
//   - 未分组 → 自定义分组（Base 移出「未分组」并出现在分组下、计数同步）
//   - 自定义分组 → 未分组（往返可逆）
//   - 清理临时分组
//
// 用法：NODE_PATH=E:/Project/Vue_demo/demand_system/demand_frontend/node_modules \
//       node scripts/verify-base-group-move.cjs
// 前置：后端 8081 + 前端 5170 均已启动；用 admin/admin123 登录。
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

  // 树快照：缩进换算层级（paddingLeft = 6 + depth * 14）
  const snap = () =>
    page.locator('.bgtree-node').evaluateAll((els) =>
      els.map((el) => {
        const indent = parseInt(el.style.paddingLeft) || 0;
        return {
          key: el.dataset.key,
          kind: ([...el.classList].find((c) => c.startsWith('bgtree-node--')) || '').replace('bgtree-node--', ''),
          indent,
          depth: Math.round((indent - 6) / 14),
          name: el.querySelector('.bgtree-node__name')?.textContent?.trim() ?? '',
          count: el.querySelector('.bgtree-node__count')?.textContent?.trim() || '',
        };
      }),
    );

  const show = (list) => list.map((n) => `${n.kind}:${n.name}${n.count ? `(${n.count})` : ''}@${n.depth}`).join(' / ');

  /** 用「最近的更浅层容器节点」判定每个 Base 当前挂在哪个容器下 */
  const ownerMap = (list) => {
    const out = {};
    const stack = [];
    for (const n of list) {
      while (stack.length && stack[stack.length - 1].depth >= n.depth) stack.pop();
      if (n.kind === 'base') {
        const owner = stack[stack.length - 1];
        out[n.name] = owner ? owner.name : '(无容器)';
      }
      stack.push(n);
    }
    return out;
  };

  const containerOf = (list, name) => {
    const n = list.find((x) => x.kind !== 'base' && x.name === name);
    return n ? { count: n.count, depth: n.depth } : null;
  };

  // .el-message 会堆叠，断言前先等它消失
  const waitNoMessage = async () => {
    await page.waitForFunction(() => !document.querySelector('.el-message'), { timeout: 8000 }).catch(() => {});
  };

  const nodeByKey = (key) => page.locator(`.bgtree-node[data-key="${key}"]`);

  /** 点击分组行会同时触发「选中 + 折叠切换」，断言前需要确保它处于展开态 */
  const ensureExpanded = async (key) => {
    const chev = nodeByKey(key).locator('.bgtree-node__chev');
    const cls = (await chev.getAttribute('class')) || '';
    if (!cls.includes('is-open')) {
      await chev.click();
      await page.waitForTimeout(600);
    }
  };

  /** 打开某树节点的 ··· 菜单并点击指定项 */
  const openNodeMenu = async (key, itemText) => {
    const node = nodeByKey(key);
    await node.hover();
    await node.locator('.bgtree-node__tool').last().click();
    await page.waitForTimeout(400);
    await page.locator('.el-dropdown-menu__item:visible').filter({ hasText: itemText }).first().click();
    await page.waitForTimeout(400);
  };

  /** 在「移动到分组」弹窗里选目标分组并确定 */
  const moveTo = async (targetText) => {
    await page.waitForSelector('.el-dialog:visible', { timeout: 8000 });
    const title = await page.locator('.el-dialog:visible .el-dialog__title').innerText();
    if (title.trim() !== '移动到分组') throw new Error('弹窗标题异常: ' + title);
    await page.locator('.el-dialog:visible .el-select__wrapper').click();
    await page.waitForTimeout(700);
    await page.locator('.el-select-dropdown__item:visible').filter({ hasText: targetText }).first().click();
    await page.waitForTimeout(400);
    await page.locator('.el-dialog:visible .el-button--primary').click();
    await waitNoMessage();
    await page.waitForTimeout(1200);
  };

  const TMP_GROUP = '__MOVE_TGT';
  let tmpGroupKey = null;
  let movedBaseKey = null;
  let movedBaseName = null;

  try {
    // ---------- [1] 登录 ----------
    await page.goto(BASE + '/login', { waitUntil: 'domcontentloaded' });
    await page.locator('input[placeholder="请输入用户名"]').fill('admin');
    await page.locator('input[placeholder="请输入密码"]').fill('admin123');
    await page.locator('.login-btn').first().click();
    await page.waitForURL((u) => !u.pathname.includes('/login'), { timeout: 30000 });
    log('[1] 登录成功');

    // ---------- [2] 打开多维表格列表页 ----------
    await page.goto(BASE + '/bitable', { waitUntil: 'domcontentloaded' });
    await page.locator('.bgtree-node').first().waitFor({ timeout: 30000 });
    await page.waitForTimeout(1000);

    const init = await snap();
    log('[2] 初始树:', show(init));
    check('左侧分组树已渲染', init.length > 0);
    check('存在「全部」虚拟节点', init.some((n) => n.name === '全部'));

    const allNode = containerOf(init, '全部');
    const ugRoot = containerOf(init, '未分组');
    check('存在根层级「未分组」节点', !!ugRoot);
    const allCount = Number(allNode?.count ?? -1);
    const ugCount = Number(ugRoot?.count ?? -1);

    const initOwners = ownerMap(init);
    const baseNodes = init.filter((n) => n.kind === 'base');
    log('    Base 总数 =', baseNodes.length, '/ 全部计数 =', allCount, '/ 未分组计数 =', ugCount);
    check('「全部」计数等于树中 Base 节点数', allCount === baseNodes.length, `all=${allCount} bases=${baseNodes.length}`);

    // 关键断言：数据库里已归组的 Base 必须出现在其分组下，而不是留在「未分组」
    const groupedBases = Object.entries(initOwners).filter(([, owner]) => owner !== '未分组' && owner !== '全部');
    log('    已归组的 Base:', JSON.stringify(groupedBases));
    check(
      '存在已归组的 Base（分组归属生效）',
      groupedBases.length > 0,
      '若为 0 说明 GET /bases 仍未返回 groupId',
    );
    check(
      '「未分组」计数 = 全部 - 已归组',
      ugCount === allCount - groupedBases.length,
      `ug=${ugCount} all=${allCount} grouped=${groupedBases.length}`,
    );

    // 记录「绩效考核」分组的初始状态，收尾时不得被破坏
    const perfBefore = (() => {
      const g = init.find((n) => n.kind === 'group' && n.name === '绩效考核');
      return g ? { count: g.count, bases: Object.entries(initOwners).filter(([, o]) => o === '绩效考核').map(([k]) => k) } : null;
    })();
    log('    「绩效考核」初始:', JSON.stringify(perfBefore));

    // 挑一个当前在根层级「未分组」下的 Base 作为搬运对象
    const candidate = init.find((n) => n.kind === 'base' && initOwners[n.name] === '未分组');
    check('存在可搬运的未分组 Base', !!candidate, show(init));
    if (!candidate) throw new Error('没有未分组的 Base，无法继续');
    movedBaseKey = candidate.key;
    movedBaseName = candidate.name;
    log('    搬运对象:', movedBaseKey, movedBaseName);

    // ---------- [3] 新建临时分组 ----------
    await waitNoMessage();
    await page.locator('.bgtree-header__btn[title="新建分组"]').click();
    await page.locator('.bgtree-editor input').waitFor({ timeout: 5000 });
    await page.locator('.bgtree-editor input').fill(TMP_GROUP);
    await page.keyboard.press('Enter');
    await page.waitForTimeout(1300);
    await waitNoMessage();

    const afterCreate = await snap();
    const tmpNode = afterCreate.find((n) => n.kind === 'group' && n.name === TMP_GROUP);
    check(`临时分组「${TMP_GROUP}」已创建`, !!tmpNode, show(afterCreate));
    check('新分组初始计数为 0', tmpNode?.count === '0', 'count=' + tmpNode?.count);
    if (!tmpNode) throw new Error('临时分组创建失败，无法继续');
    tmpGroupKey = tmpNode.key;

    // ---------- [4] 未分组 → 自定义分组（核心回归点） ----------
    log('\n[4] 未分组 → 自定义分组');
    await openNodeMenu(movedBaseKey, '移动到分组');
    const dlgTitle = await page.locator('.el-dialog:visible .el-dialog__title').innerText();
    check('「移动到分组」弹窗已打开', dlgTitle.trim() === '移动到分组', dlgTitle);

    await page.locator('.el-dialog:visible .el-select__wrapper').click();
    await page.waitForTimeout(700);
    const options = (await page.locator('.el-select-dropdown__item:visible').allInnerTexts()).map((s) => s.trim());
    log('    下拉选项:', JSON.stringify(options));
    check('下拉含「未分组（根层级）」', options.some((o) => o.includes('未分组（根层级）')), JSON.stringify(options));
    check(`下拉含临时分组「${TMP_GROUP}」`, options.some((o) => o.includes(TMP_GROUP)), JSON.stringify(options));

    await page.locator('.el-select-dropdown__item:visible').filter({ hasText: TMP_GROUP }).first().click();
    await page.waitForTimeout(400);
    await page.locator('.el-dialog:visible .el-button--primary').click();
    await waitNoMessage();
    await page.waitForTimeout(1300);

    const moved = await snap();
    log('    移动后:', show(moved));
    const movedOwners = ownerMap(moved);
    const tmpAfter = containerOf(moved, TMP_GROUP);
    const ugAfter = containerOf(moved, '未分组');

    check(
      `「${movedBaseName}」已出现在「${TMP_GROUP}」分组下（bug 1：分组未显示）`,
      movedOwners[movedBaseName] === TMP_GROUP,
      `owner=${movedOwners[movedBaseName]}`,
    );
    check(
      `「${movedBaseName}」已从「未分组」移走（bug 2：未从原处移去）`,
      movedOwners[movedBaseName] !== '未分组',
      `owner=${movedOwners[movedBaseName]}`,
    );
    check(`「${TMP_GROUP}」计数 = 1`, tmpAfter?.count === '1', 'count=' + tmpAfter?.count);
    check('「未分组」计数 -1', Number(ugAfter?.count) === ugCount - 1, `before=${ugCount} after=${ugAfter?.count}`);
    check('「全部」计数不变', containerOf(moved, '全部')?.count === String(allCount));

    // 右侧面板：选中该分组后范围计数应为 1
    await nodeByKey(tmpGroupKey).click();
    await page.waitForTimeout(900);
    const scopeTag = await page.locator('.bitable-list__scope .el-tag').first().innerText();
    const scopeTitle = await page.locator('.bitable-list__scope-title').first().innerText();
    log('    右侧范围:', scopeTitle, scopeTag);
    check('右侧标题为分组名', scopeTitle.trim() === TMP_GROUP, scopeTitle);
    check('右侧「共 N 个」= 1', scopeTag.includes('1'), scopeTag);
    check(
      '右侧卡片列出该 Base',
      (await page.locator('.base-card__name').allInnerTexts()).some((t) => t.trim() === movedBaseName),
      JSON.stringify(await page.locator('.base-card__name').allInnerTexts()),
    );
    await page.screenshot({ path: path.join(OUT, 'bgtree-move-into-group.png') });
    // 选中分组时顺带折叠了它，后面还要按 key 定位其下的 Base，先展开回来
    await ensureExpanded(tmpGroupKey);
    check('分组行重新展开后可见其下 Base', await nodeByKey(movedBaseKey).isVisible().catch(() => false));

    // ---------- [5] 自定义分组 → 未分组（往返可逆） ----------
    log('\n[5] 自定义分组 → 未分组（往返）');
    await openNodeMenu(movedBaseKey, '移动到分组');
    await moveTo('未分组（根层级）');

    const restored = await snap();
    log('    移回后:', show(restored));
    const restoredOwners = ownerMap(restored);
    check(`「${movedBaseName}」已回到「未分组」`, restoredOwners[movedBaseName] === '未分组', `owner=${restoredOwners[movedBaseName]}`);
    check(`「${TMP_GROUP}」计数回到 0`, containerOf(restored, TMP_GROUP)?.count === '0', 'count=' + containerOf(restored, TMP_GROUP)?.count);
    check('「未分组」计数复原', Number(containerOf(restored, '未分组')?.count) === ugCount, 'count=' + containerOf(restored, '未分组')?.count);

    // ---------- [6] 刷新后仍持久化 ----------
    log('\n[6] 刷新页面复核持久化');
    await page.reload({ waitUntil: 'domcontentloaded' });
    await page.locator('.bgtree-node').first().waitFor({ timeout: 30000 });
    await page.waitForTimeout(1000);
    const afterReload = await snap();
    const reloadOwners = ownerMap(afterReload);
    check(`刷新后「${movedBaseName}」仍在「未分组」`, reloadOwners[movedBaseName] === '未分组', `owner=${reloadOwners[movedBaseName]}`);
    check('刷新后已归组的 Base 归属不变', JSON.stringify(Object.entries(reloadOwners).filter(([, o]) => o !== '未分组' && o !== '全部').sort()) === JSON.stringify(groupedBases.sort()));

    // ---------- [7] 清理临时分组 ----------
    log('\n[7] 清理');
    await openNodeMenu(tmpGroupKey, '删除分组');
    await page.locator('.el-message-box__btns .el-button--primary').first().click();
    await waitNoMessage();
    await page.waitForTimeout(1200);

    const cleaned = await snap();
    log('    清理后:', show(cleaned));
    check(`临时分组「${TMP_GROUP}」已删除`, !cleaned.some((n) => n.kind === 'group' && n.name === TMP_GROUP));
    check('Base 数量未变', cleaned.filter((n) => n.kind === 'base').length === baseNodes.length);
    check('「全部」计数复原', containerOf(cleaned, '全部')?.count === String(allCount));
    check('「未分组」计数复原', Number(containerOf(cleaned, '未分组')?.count) === ugCount, 'count=' + containerOf(cleaned, '未分组')?.count);

    if (perfBefore) {
      const perfNow = cleaned.find((n) => n.kind === 'group' && n.name === '绩效考核');
      const nowBases = Object.entries(ownerMap(cleaned)).filter(([, o]) => o === '绩效考核').map(([k]) => k).sort();
      check('「绩效考核」分组未被波及', !!perfNow && perfNow.count === perfBefore.count && JSON.stringify(nowBases) === JSON.stringify([...perfBefore.bases].sort()),
        `before=${JSON.stringify(perfBefore)} after=${perfNow?.count}/${JSON.stringify(nowBases)}`);
    }

    await page.screenshot({ path: path.join(OUT, 'bgtree-move-cleanup.png') });
    check('页面无 JS 报错', errs.length === 0, errs.join(' | '));
  } catch (e) {
    fail++;
    log('!! 执行异常: ' + e.message + '\n' + e.stack);
    await page.screenshot({ path: path.join(OUT, 'bgtree-move-error.png') }).catch(() => {});
  } finally {
    await browser.close();
  }

  log(`\n===== 结果：PASS ${pass} / FAIL ${fail} =====`);
  process.exit(fail ? 1 : 0);
}

main();
